package cl.sanmarcos.vetsm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persona que usa el sistema: administrador, recepcionista o cliente (dueño de mascota). */
@Entity
@Table(name = "usuario", uniqueConstraints = @UniqueConstraint(name = "uk_usuario_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    /** Se guarda siempre en minúsculas para que el correo sea único sin importar cómo se escriba. */
    @Column(nullable = false, length = 150)
    private String email;

    /** Hash BCrypt de la contraseña. La contraseña en texto plano nunca se guarda. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 20)
    private String telefono;

    // ---------- Personalización del perfil (sección "Mi cuenta" del cliente) ----------
    @Column(length = 60)
    private String region;

    @Column(length = 60)
    private String comuna;

    @Column(length = 150)
    private String direccion;

    /** Emoji del avatar elegido por el usuario. */
    @Column(name = "avatar_icono", length = 16)
    private String avatarIcono;

    /** Color del avatar en formato #RRGGBB. */
    @Column(name = "avatar_color", length = 7)
    private String avatarColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    /** Un usuario desactivado no puede iniciar sesión, pero su historial se conserva. */
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Usuario(String nombre, String email, String passwordHash, String telefono, Rol rol) {
        this.nombre = nombre;
        this.email = email;
        this.passwordHash = passwordHash;
        this.telefono = telefono;
        this.rol = rol;
    }

    @PrePersist
    void antesDeCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
        normalizarEmail();
    }

    @PreUpdate
    void antesDeActualizar() {
        normalizarEmail();
    }

    private void normalizarEmail() {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }
}
