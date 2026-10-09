package com.mantenimiento.cmms.model;

/** Nivel de urgencia de una orden de trabajo. */
public enum Prioridad {

    BAJA("Baja", "neutro"),
    MEDIA("Media", "info"),
    ALTA("Alta", "advertencia"),
    CRITICA("Critica", "peligro");

    private final String etiqueta;
    private final String clase;

    Prioridad(String etiqueta, String clase) {
        this.etiqueta = etiqueta;
        this.clase = clase;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Clase CSS de la pastilla (chip). */
    public String getClase() {
        return clase;
    }

    /** Puntaje usado para ordenar: critica primero. */
    public int getPuntaje() {
        return ordinal() + 1;
    }
}
