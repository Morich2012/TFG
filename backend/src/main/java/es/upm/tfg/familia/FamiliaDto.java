package es.upm.tfg.familia;

/** Esquema {@code Familia} de la OpenAPI. */
public record FamiliaDto(long id, String nombre, String nombreMoneda, int monedasPorEuro) {

    public static FamiliaDto de(Familia f) {
        return new FamiliaDto(f.getId(), f.getNombre(), f.getNombreMoneda(), f.getMonedasPorEuro());
    }
}
