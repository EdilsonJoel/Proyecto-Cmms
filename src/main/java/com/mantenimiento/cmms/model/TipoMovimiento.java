package com.mantenimiento.cmms.model;

/** Tipo de movimiento de almacen en el kardex de repuestos. */
public enum TipoMovimiento {

    ENTRADA("Entrada", "exito"),
    SALIDA("Salida", "peligro"),
    AJUSTE("Ajuste", "advertencia");

    private final String etiqueta;
    private final String clase;

    TipoMovimiento(String etiqueta, String clase) {
        this.etiqueta = etiqueta;
        this.clase = clase;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getClase() {
        return clase;
    }

    /**
     * Variacion de stock que produce el movimiento sobre el stock actual.
     * Entradas suman, salidas restan; un ajuste acepta cantidad con signo
     * (+ para agregar, - para corregir hacia abajo).
     */
    public int variacion(int cantidad) {
        return switch (this) {
            case ENTRADA -> Math.abs(cantidad);
            case SALIDA -> -Math.abs(cantidad);
            case AJUSTE -> cantidad;
        };
    }
}
