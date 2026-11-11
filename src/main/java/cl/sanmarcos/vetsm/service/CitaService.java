package cl.sanmarcos.vetsm.service;

import cl.sanmarcos.vetsm.dto.CitaRequest;
import cl.sanmarcos.vetsm.dto.CitaResponse;
import cl.sanmarcos.vetsm.exception.ApiException;
import cl.sanmarcos.vetsm.model.Cita;
import cl.sanmarcos.vetsm.model.EstadoCita;
import cl.sanmarcos.vetsm.model.Producto;
import cl.sanmarcos.vetsm.repository.CitaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agenda de la clínica. Reglas:
 * - No se agenda en el pasado ni en un horario ya ocupado por otra cita vigente.
 * - El servicio debe ser una atención de la clínica (producto de tipo SERVICIO) que esté activa.
 * - Los estados avanzan en orden (PENDIENTE → CONFIRMADA → COMPLETADA) y se puede cancelar antes de completar.
 * - Una cita completada o cancelada ya no se edita, y una completada no se elimina (es historial clínico).
 */
@Service
@Transactional
public class CitaService {

    private final CitaRepository citas;
    private final MascotaService mascotas;
    private final ProductoService productos;
    private final Clock reloj;

    public CitaService(CitaRepository citas, MascotaService mascotas, ProductoService productos, Clock reloj) {
        this.citas = citas;
        this.mascotas = mascotas;
        this.productos = productos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<CitaResponse> listar(EstadoCita estado, LocalDate fecha) {
        return citas.findAllByOrderByFechaAscHoraAsc().stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .filter(c -> fecha == null || c.getFecha().equals(fecha))
                .map(CitaResponse::desde)
                .toList();
    }

    /** Citas de las mascotas de un cliente. */
    @Transactional(readOnly = true)
    public List<CitaResponse> listarDeCliente(Long clienteId) {
        return citas.findByMascotaDuenoIdOrderByFechaDescHoraDesc(clienteId).stream().map(CitaResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public CitaResponse obtener(Long id) {
        return CitaResponse.desde(buscar(id));
    }

    public CitaResponse crear(CitaRequest datos) {
        Cita cita = new Cita();
        aplicar(cita, datos, null);
        cita.setEstado(EstadoCita.PENDIENTE);
        return CitaResponse.desde(citas.save(cita));
    }

    /** Edita o reagenda una cita que todavía no termina. */
    public CitaResponse actualizar(Long id, CitaRequest datos) {
        Cita cita = buscar(id);
        if (cita.getEstado().esFinal()) {
            throw new ApiException.Conflicto("La cita está " + cita.getEstado().name().toLowerCase() + " y ya no se puede modificar.");
        }
        aplicar(cita, datos, id);
        return CitaResponse.desde(cita);
    }

    public CitaResponse cambiarEstado(Long id, EstadoCita nuevo) {
        Cita cita = buscar(id);
        if (!cita.getEstado().puedePasarA(nuevo)) {
            throw new ApiException.Conflicto("Una cita " + cita.getEstado().name().toLowerCase()
                    + " no puede pasar a " + nuevo.name().toLowerCase() + ".");
        }
        cita.setEstado(nuevo);
        return CitaResponse.desde(cita);
    }

    public void eliminar(Long id) {
        Cita cita = buscar(id);
        if (cita.getEstado() == EstadoCita.COMPLETADA) {
            throw new ApiException.Conflicto("Las citas completadas son parte del historial clínico y no se eliminan.");
        }
        citas.delete(cita);
    }

    public Cita buscar(Long id) {
        return citas.findById(id).orElseThrow(() -> new ApiException.NoEncontrado("No existe la cita " + id + "."));
    }

    /** idActual: la cita que se está reagendando (su propio horario no cuenta como ocupado). */
    private void aplicar(Cita cita, CitaRequest datos, Long idActual) {
        if (datos.mascotaId() != null) {
            cita.asignarMascota(mascotas.buscar(datos.mascotaId()));
        } else {
            Map<String, String> errores = new LinkedHashMap<>();
            if (vacio(datos.nombreMascota())) {
                errores.put("nombreMascota", "Indica el nombre de la mascota.");
            }
            if (vacio(datos.nombreDueno())) {
                errores.put("nombreDueno", "Indica el nombre del dueño.");
            }
            if (vacio(datos.telefonoContacto())) {
                errores.put("telefonoContacto", "Indica un teléfono de contacto.");
            }
            if (!errores.isEmpty()) {
                throw new ApiException.DatosInvalidos(errores);
            }
            cita.setMascota(null);
            cita.setNombreMascota(datos.nombreMascota().trim());
            cita.setNombreDueno(datos.nombreDueno().trim());
            cita.setTelefonoContacto(datos.telefonoContacto().trim());
        }

        Producto servicio = productos.buscar(datos.servicioId());
        if (!servicio.esServicio() || !servicio.isActivo()) {
            throw new ApiException.ReglaNegocio("\"" + servicio.getNombre() + "\" no es una atención disponible de la clínica.");
        }

        if (LocalDateTime.of(datos.fecha(), datos.hora()).isBefore(LocalDateTime.now(reloj))) {
            throw new ApiException.ReglaNegocio("No se puede agendar una cita en una fecha u hora que ya pasó.");
        }
        boolean ocupado = idActual == null
                ? citas.existsByFechaAndHoraAndEstadoNot(datos.fecha(), datos.hora(), EstadoCita.CANCELADA)
                : citas.existsByFechaAndHoraAndEstadoNotAndIdNot(datos.fecha(), datos.hora(), EstadoCita.CANCELADA, idActual);
        if (ocupado) {
            throw new ApiException.Conflicto("Ya hay una cita agendada el " + datos.fecha() + " a las " + datos.hora() + ". Elige otro horario.");
        }

        cita.setServicio(servicio);
        cita.setFecha(datos.fecha());
        cita.setHora(datos.hora());
        cita.setNotas(CategoriaService.limpiar(datos.notas()));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
