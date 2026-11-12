package cl.sanmarcos.vetsm.service;

import cl.sanmarcos.vetsm.dto.MascotaRequest;
import cl.sanmarcos.vetsm.dto.MiMascotaRequest;
import cl.sanmarcos.vetsm.dto.MascotaResponse;
import cl.sanmarcos.vetsm.exception.ApiException;
import cl.sanmarcos.vetsm.model.Mascota;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.model.Usuario;
import cl.sanmarcos.vetsm.repository.CitaRepository;
import cl.sanmarcos.vetsm.repository.MascotaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MascotaService {

    private final MascotaRepository mascotas;
    private final CitaRepository citas;
    private final UsuarioService usuarios;

    public MascotaService(MascotaRepository mascotas, CitaRepository citas, UsuarioService usuarios) {
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

    public Mascota buscar(Long id) {
        return mascotas.findById(id)
                .orElseThrow(() -> new ApiException.NoEncontrado("No existe la mascota " + id + "."));
    }

    private void aplicar(Mascota mascota, MascotaRequest datos) {
        Usuario dueno = usuarios.buscar(datos.duenoId());
        if (dueno.getRol() != Rol.CLIENTE) {
            throw new ApiException.ReglaNegocio("Las mascotas se registran a nombre de un cliente, no de una cuenta del personal.");
        }
        mascota.setNombre(datos.nombre().trim());
        mascota.setEspecie(datos.especie().trim());
        mascota.setRaza(CategoriaService.limpiar(datos.raza()));
        mascota.setDueno(dueno);
    }
}
