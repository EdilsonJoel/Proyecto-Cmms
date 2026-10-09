package com.mantenimiento.cmms.model;

/** Roles del sistema y sus niveles de acceso. */
public enum Rol {

    ADMIN("Administrador"),
    SUPERVISOR("Supervisor"),
    TECNICO("Tecnico");

    private final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Gestiona catalogos, tecnicos, planes y crea/elimina ordenes. */
    public boolean puedeGestionar() {
        return this == ADMIN || this == SUPERVISOR;
    }

    /** Solo el administrador administra usuarios del sistema. */
    public boolean puedeAdministrarUsuarios() {
        return this == ADMIN;
    }

    /** El tecnico solo ve y actualiza sus propias ordenes asignadas. */
    public boolean esTecnico() {
        return this == TECNICO;
    }
}
