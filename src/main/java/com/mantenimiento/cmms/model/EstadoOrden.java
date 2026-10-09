package com.mantenimiento.cmms.model;

/**
 * Ciclo de vida de una orden de trabajo:
 * <pre>
 * ABIERTA -> EN_EJECUCION -> COMPLETADA
 *    |            |
 *    +-> CANCELADA <-+
 * </pre>
 * Las transiciones se validan en {@code OrdenService} para que ninguna orden
 * pueda saltarse pasos (regla de negocio central del sistema).
 */
public enum EstadoOrden {

    ABIERTA("Abierta", "advertencia"),
    EN_EJECUCION("En ejecucion", "info"),
    COMPLETADA("Completada", "exito"),
    CANCELADA("Cancelada", "neutro");

    private final String etiqueta;
    private final String clase;

    EstadoOrden(String etiqueta, String clase) {
        this.etiqueta = etiqueta;
        this.clase = clase;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getClase() {
        return clase;
    }

    /** Cuenta para las ordenes pendientes del tablero y del equipo. */
    public boolean estaActiva() {
        return this == ABIERTA || this == EN_EJECUCION;
    }

    /** Estado final: ya no admite cambios. */
    public boolean esFinal() {
        return this == COMPLETADA || this == CANCELADA;
    }

    public boolean puedeIniciar() {
        return this == ABIERTA;
    }

    public boolean puedeCompletar() {
        return this == ABIERTA || this == EN_EJECUCION;
    }

    public boolean puedeCancelar() {
        return this == ABIERTA || this == EN_EJECUCION;
    }
}
