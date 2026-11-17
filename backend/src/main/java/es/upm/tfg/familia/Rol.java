package es.upm.tfg.familia;

/** Rol de un miembro. Hay un único ADULTO_ADMIN por familia (índice miembro_un_admin_idx). */
public enum Rol {
    ADULTO_ADMIN,
    ADULTO,
    HIJO;

    public boolean esAdulto() {
        return this != HIJO;
    }
}
