package cl.sanmarcos.vetsm.comun.web;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Ayudas para armar respuestas REST. */
public final class Respuestas {

    private Respuestas() {
    }

    /** 201 Created con la cabecera Location apuntando al recurso nuevo (por ejemplo /api/v1/categorias/5). */
    public static <T> ResponseEntity<T> creado(Long id, T cuerpo) {
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(ubicacion).body(cuerpo);
    }
}
