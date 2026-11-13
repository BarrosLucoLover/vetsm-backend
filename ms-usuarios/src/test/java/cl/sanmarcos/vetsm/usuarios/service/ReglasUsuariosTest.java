package cl.sanmarcos.vetsm.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.comun.security.Rol;
import cl.sanmarcos.vetsm.usuarios.cliente.ReferenciasCliente;
import cl.sanmarcos.vetsm.usuarios.dto.CambioPasswordRequest;
import cl.sanmarcos.vetsm.usuarios.dto.UsuarioRequest;
import cl.sanmarcos.vetsm.usuarios.dto.UsuarioUpdateRequest;
import cl.sanmarcos.vetsm.usuarios.model.Usuario;
import cl.sanmarcos.vetsm.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las cuentas, contra la base H2 de este servicio. Las llamadas a ms-ventas y
 * ms-agenda se simulan (mock), porque en esta prueba esos servicios no están corriendo.
 */
@SpringBootTest
@Transactional
class ReglasUsuariosTest {

    @Autowired private UsuarioService usuarios;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private ReferenciasCliente referencias;

    private Usuario ana;
    private Usuario admin;
    private Usuario recepcion;

    @BeforeEach
    void datos() {
        ana = usuarioRepo.findByEmail("ana.garcia@correo.cl").orElseThrow();
        admin = usuarioRepo.findByEmail("admin@sanmarcos.cl").orElseThrow();
        recepcion = usuarioRepo.findByEmail("recepcion@sanmarcos.cl").orElseThrow();
        when(referencias.enVentas(anyLong())).thenReturn(new ReferenciasCliente.Referencias(0, "0 compras"));
        when(referencias.enAgenda(anyLong())).thenReturn(new ReferenciasCliente.Referencias(0, "0 mascotas"));
    }

    @Test
    @DisplayName("El correo se normaliza y no se puede repetir")
    void correoUnico() {
        var creado = usuarios.crear(new UsuarioRequest("Javiera Torres", " Javiera@SanMarcos.cl ", "Clave1234", null, Rol.RECEPCIONISTA));
        assertThat(creado.email()).isEqualTo("javiera@sanmarcos.cl");
        assertThatThrownBy(() -> usuarios.crear(new UsuarioRequest("Otra", "JAVIERA@sanmarcos.cl", "Clave1234", null, Rol.CLIENTE)))
                .isInstanceOf(ApiException.Conflicto.class);
    }

    @Test
    @DisplayName("La contraseña se guarda con hash BCrypt")
    void passwordConHash() {
        var creado = usuarios.crear(new UsuarioRequest("Javiera Torres", "javiera@sanmarcos.cl", "Clave1234", null, Rol.RECEPCIONISTA));
        String hash = usuarioRepo.findById(creado.id()).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("Clave1234").startsWith("$2");
        assertThat(passwordEncoder.matches("Clave1234", hash)).isTrue();
    }

    @Test
    @DisplayName("El registro público siempre crea un cliente")
    void registroCreaCliente() {
        assertThat(usuarios.registrarCliente("Luc Pérez", "luc@correo.cl", "Clave1234").rol()).isEqualTo(Rol.CLIENTE);
    }

    @Test
    @DisplayName("El único administrador activo no se puede desactivar, eliminar ni degradar")
    void ultimoAdministrador() {
        Long id = admin.getId();
        assertThatThrownBy(() -> usuarios.cambiarActivo(id, false, recepcion.getId())).isInstanceOf(ApiException.ReglaNegocio.class);
        assertThatThrownBy(() -> usuarios.eliminar(id, recepcion.getId())).isInstanceOf(ApiException.ReglaNegocio.class);
        UsuarioUpdateRequest degradar = new UsuarioUpdateRequest(admin.getNombre(), admin.getEmail(), null, Rol.CLIENTE);
        assertThatThrownBy(() -> usuarios.actualizar(id, degradar, recepcion.getId())).isInstanceOf(ApiException.ReglaNegocio.class);

        usuarios.crear(new UsuarioRequest("Segundo Admin", "admin2@sanmarcos.cl", "Clave1234", null, Rol.ADMIN));
        assertThat(usuarios.cambiarActivo(id, false, recepcion.getId()).activo()).isFalse();
    }

    @Test
    @DisplayName("Nadie puede desactivarse, eliminarse ni quitarse el rol de administrador a sí mismo")
    void unoMismo() {
        usuarios.crear(new UsuarioRequest("Segundo Admin", "admin2@sanmarcos.cl", "Clave1234", null, Rol.ADMIN));
        Long id = admin.getId();
        assertThatThrownBy(() -> usuarios.cambiarActivo(id, false, id)).hasMessageContaining("tu propia cuenta");
        assertThatThrownBy(() -> usuarios.eliminar(id, id)).hasMessageContaining("tu propia cuenta");
        UsuarioUpdateRequest degradar = new UsuarioUpdateRequest(admin.getNombre(), admin.getEmail(), null, Rol.CLIENTE);
        assertThatThrownBy(() -> usuarios.actualizar(id, degradar, id)).hasMessageContaining("a ti mismo");
    }

    @Test
    @DisplayName("Una cuenta con compras en ms-ventas o mascotas en ms-agenda no se elimina: hay que desactivarla")
    void conHistorial() {
        when(referencias.enAgenda(ana.getId())).thenReturn(new ReferenciasCliente.Referencias(2, "2 mascotas"));
        assertThatThrownBy(() -> usuarios.eliminar(ana.getId(), admin.getId()))
                .isInstanceOf(ApiException.Conflicto.class)
                .hasMessageContaining("Desactívala");
        verify(referencias, never()).eliminarTarjetas(anyLong());
        assertThat(usuarioRepo.findById(ana.getId())).isPresent();
    }

    @Test
    @DisplayName("Una cuenta sin historial se elimina y se le pide a ms-ventas borrar sus tarjetas")
    void sinHistorial() {
        Long nuevo = usuarios.registrarCliente("Luc Pérez", "luc@correo.cl", "Clave1234").id();
        usuarios.eliminar(nuevo, admin.getId());
        verify(referencias).eliminarTarjetas(nuevo);
        assertThat(usuarioRepo.findById(nuevo)).isEmpty();
    }

    @Test
    @DisplayName("Si ms-ventas no responde, la cuenta no se elimina (503)")
    void servicioCaido() {
        Long nuevo = usuarios.registrarCliente("Luc Pérez", "luc@correo.cl", "Clave1234").id();
        when(referencias.enVentas(nuevo)).thenThrow(new ApiException.ServicioNoDisponible("ventas"));
        assertThatThrownBy(() -> usuarios.eliminar(nuevo, admin.getId()))
                .isInstanceOf(ApiException.ServicioNoDisponible.class);
        assertThat(usuarioRepo.findById(nuevo)).isPresent();
    }

    @Test
    @DisplayName("Cambiar la contraseña exige la actual correcta y una nueva distinta")
    void cambiarPassword() {
        assertThatThrownBy(() -> usuarios.cambiarPassword(ana.getId(), new CambioPasswordRequest("Equivocada1", "NuevaClave1")))
                .isInstanceOf(ApiException.DatosInvalidos.class);
        assertThatThrownBy(() -> usuarios.cambiarPassword(ana.getId(), new CambioPasswordRequest("Cliente123!", "Cliente123!")))
                .isInstanceOf(ApiException.DatosInvalidos.class);

        usuarios.cambiarPassword(ana.getId(), new CambioPasswordRequest("Cliente123!", "NuevaClave1"));
        assertThat(passwordEncoder.matches("NuevaClave1", usuarioRepo.findById(ana.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
}
