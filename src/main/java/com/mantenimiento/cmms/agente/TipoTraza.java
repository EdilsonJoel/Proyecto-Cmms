package com.mantenimiento.cmms.agente;

/**
 * Tipo de paso que el agente deja en la bitacora. El usuario distingue de un
 * vistazo lo que el sistema <b>consulto</b> (dato almacenado), lo que
 * <b>calculo</b>, lo que <b>decidio</b>, lo que <b>controlo</b> el propio
 * usuario y lo que <b>fallo</b>. La separacion consultado/generado es
 * deliberada: es la base de la confianza en la interfaz.
 */
public enum TipoTraza {

    /** Lectura de datos almacenados (base de datos / repositorios). */
    CONSULTA("Consulta", "BD", "info", "fth-database"),
    /** Derivacion aritmetica o estadistica sobre los datos consultados. */
    CALCULO("Calculo", "calc", "neutro", "fth-activity"),
    /** Aplicacion de una regla de negocio con su criterio visible. */
    DECISION("Decision", "decision", "exito", "fth-git-branch"),
    /** Fin feliz de una accion, con su resultado concreto. */
    RESULTADO("Resultado", "ok", "exito", "fth-check-circle"),
    /** Llamada de atencion sin romper nada (stock minimo, plan por vencer). */
    AVISO("Aviso", "aviso", "advertencia", "fth-alert-triangle"),
    /** La accion no pudo completarse. */
    ERROR("Error", "error", "peligro", "fth-x-circle"),
    /** Intervention del usuario: pausa, cancelacion, override. */
    CONTROL("Control", "control", "info", "fth-sliders");

    private final String etiqueta;
    private final String chip;
    private final String clase;
    private final String icono;

    TipoTraza(String etiqueta, String chip, String clase, String icono) {
        this.etiqueta = etiqueta;
        this.chip = chip;
        this.clase = clase;
        this.icono = icono;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Texto corto del chip del dock: "BD", "calc", "decision"... */
    public String getChip() {
        return chip;
    }

    /** Clase CSS de la pastilla (chip). */
    public String getClase() {
        return clase;
    }

    /** Literal de icono Feather para FontIcon. */
    public String getIcono() {
        return icono;
    }
}
