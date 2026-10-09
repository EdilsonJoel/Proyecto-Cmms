package com.mantenimiento.cmms.model;

/** Estado operativo de un equipo o activo fisico. */
public enum EstadoEquipo {

    OPERATIVO("Operativo", "exito"),
    EN_MANTENIMIENTO("En mantenimiento", "advertencia"),
    DETENIDO("Detenido", "peligro"),
    BAJA("De baja", "neutro");

    private final String etiqueta;
    private final String clase;

    EstadoEquipo(String etiqueta, String clase) {
        this.etiqueta = etiqueta;
        this.clase = clase;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Clase CSS de la pastilla de estado (chip). */
    public String getClase() {
        return clase;
    }
}
