package cl.sanmarcos.vetsm.dto;

import cl.sanmarcos.vetsm.model.Mascota;

public record MascotaResponse(Long id, String nombre, String especie, String raza, Long duenoId, String duenoNombre) {

    public static MascotaResponse desde(Mascota m) {
        return new MascotaResponse(m.getId(), m.getNombre(), m.getEspecie(), m.getRaza(), m.getDueno().getId(), m.getDueno().getNombre());
    }
}
