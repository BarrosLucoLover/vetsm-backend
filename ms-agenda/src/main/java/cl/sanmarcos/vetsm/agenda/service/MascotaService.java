package cl.sanmarcos.vetsm.agenda.service;

import cl.sanmarcos.vetsm.agenda.cliente.UsuariosCliente;

import cl.sanmarcos.vetsm.comun.util.Textos;
import cl.sanmarcos.vetsm.agenda.dto.MascotaRequest;
import cl.sanmarcos.vetsm.agenda.dto.MiMascotaRequest;
import cl.sanmarcos.vetsm.agenda.dto.MascotaResponse;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.agenda.model.Mascota;
import cl.sanmarcos.vetsm.agenda.repository.CitaRepository;
import cl.sanmarcos.vetsm.agenda.repository.MascotaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MascotaService {

    private final MascotaRepository mascotas;
    private final CitaRepository citas;
    private final UsuariosCliente usuarios;

    public MascotaService(MascotaRepository mascotas, CitaRepository citas, UsuariosCliente usuarios) {
        this.mascotas = mascotas;
        this.citas = citas;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<MascotaResponse> listar(Long duenoId) {
        List<Mascota> lista = duenoId == null ? mascotas.findAllByOrderByNombreAsc() : mascotas.findByDuenoIdOrderByNombreAsc(duenoId);
        return lista.stream().map(MascotaResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public MascotaResponse obtener(Long id) {
        return MascotaResponse.desde(buscar(id));
    }

    /** Un cliente registra su propia mascota: el dueño es quien inició sesión. */
    public MascotaResponse crearPara(Long clienteId, MiMascotaRequest datos) {
        return crear(new MascotaRequest(datos.nombre(), datos.especie(), datos.raza(), clienteId));
    }

    public MascotaResponse crear(MascotaRequest datos) {
        Mascota mascota = new Mascota();
        aplicar(mascota, datos);
        return MascotaResponse.desde(mascotas.save(mascota));
    }

    public MascotaResponse actualizar(Long id, MascotaRequest datos) {
        Mascota mascota = buscar(id);
        aplicar(mascota, datos);
        return MascotaResponse.desde(mascota);
    }

    /** No se elimina una mascota con citas: se perdería su historial de atenciones. */
    public void eliminar(Long id) {
        Mascota mascota = buscar(id);
        if (citas.existsByMascotaId(id)) {
            throw new ApiException.Conflicto(mascota.getNombre() + " tiene citas registradas y no se puede eliminar.");
        }
        mascotas.delete(mascota);
    }

    /** Cuántas mascotas tiene un cliente (lo pregunta ms-usuarios antes de eliminar una cuenta). */
    @Transactional(readOnly = true)
    public long contarDeDueno(Long duenoId) {
        return mascotas.countByDuenoId(duenoId);
    }

    public Mascota buscar(Long id) {
        return mascotas.findById(id)
                .orElseThrow(() -> new ApiException.NoEncontrado("No existe la mascota " + id + "."));
    }

    private void aplicar(Mascota mascota, MascotaRequest datos) {
        // El dueño está en ms-usuarios: se le pide su rol, nombre y teléfono por la API interna.
        UsuariosCliente.Usuario dueno = usuarios.obtener(datos.duenoId());
        if (!"CLIENTE".equals(dueno.rol())) {
            throw new ApiException.ReglaNegocio("Las mascotas se registran a nombre de un cliente, no de una cuenta del personal.");
        }
        mascota.setNombre(datos.nombre().trim());
        mascota.setEspecie(datos.especie().trim());
        mascota.setRaza(Textos.limpiar(datos.raza()));
        mascota.setDuenoId(dueno.id());
        mascota.setDuenoNombre(dueno.nombre());
        mascota.setDuenoTelefono(dueno.telefono());
    }
}
