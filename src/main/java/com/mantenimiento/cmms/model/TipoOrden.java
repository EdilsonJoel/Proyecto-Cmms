package com.mantenimiento.cmms.model;

/** Tipo de trabajo que responde una orden de mantenimiento. */
public enum TipoOrden {

    PREVENTIVO("Preventivo"),
    CORRECTIVO("Correctivo"),
    PREDICTIVO("Predictivo");

    private final String etiqueta;

    TipoOrden(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
