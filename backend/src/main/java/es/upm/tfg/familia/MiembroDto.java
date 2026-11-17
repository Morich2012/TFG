package es.upm.tfg.familia;

/** Esquema {@code Miembro}. Nunca incluye correo, hashes ni datos de acceso. */
public record MiembroDto(long id, Rol rol, String nombre, String avatar, Integer anioNacimiento, int nivel,
        Integer etapa) {

    public static MiembroDto de(Miembro m, Integer etapa) {
        return new MiembroDto(m.getId(), m.getRol(), m.getNombre(), m.getAvatar(),
                m.getAnioNacimiento() == null ? null : m.getAnioNacimiento().intValue(),
                m.getNivel(), etapa);
    }
}
