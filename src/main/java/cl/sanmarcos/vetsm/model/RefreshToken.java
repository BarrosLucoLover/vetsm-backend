package cl.sanmarcos.vetsm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Token de renovación de una sesión. Permite pedir un token de acceso nuevo sin volver a escribir
 * la contraseña, y se puede revocar (cerrar sesión, cambio de contraseña o sospecha de robo).
 *
 * En la base de datos solo se guarda el hash SHA-256 del token: si alguien leyera la tabla, no
 * podría usar los tokens guardados.
 */
@Entity
@Table(name = "refresh_token", uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"))
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_token_usuario"))
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    /** Un token revocado ya no sirve (se usó, se cerró la sesión o se cambió la contraseña). */
    @Column(nullable = false)
    private boolean revocado;

    /**
     * true si se revocó porque ya se usó para renovar. Solo en ese caso, que vuelva a aparecer
     * indica que alguien más tiene una copia (robo). Un token cerrado por logout no cuenta como robo.
     */
    @Column(nullable = false)
    private boolean reemplazado;

    public RefreshToken(Usuario usuario, String tokenHash, Instant creadoEn, Instant expiraEn) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
    }

    public boolean estaVencido(Instant ahora) {
        return !expiraEn.isAfter(ahora);
    }
}
