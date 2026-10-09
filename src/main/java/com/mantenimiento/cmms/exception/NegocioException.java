package com.mantenimiento.cmms.exception;

/**
 * Violacion de una regla del negocio (ej. stock insuficiente, transicion invalida).
 * <p>
 * Puede llevar la <b>estructura de recuperacion</b> completa — codigo, que paso,
 * por que ocurrio y que hacer ahora — para que la interfaz muestre errores que
 * el usuario entiende y puede resolver. Las excepciones construidas solo con el
 * mensaje tipico se completan con {@link Diagnostico} segun su contenido.
 */
public class NegocioException extends RuntimeException {

    private final String codigo;
    private final String razon;
    private final String accion;

    public NegocioException(String mensaje) {
        this(null, mensaje, null, null, null);
    }

    public NegocioException(String mensaje, Throwable causa) {
        this(null, mensaje, null, null, causa);
    }

    /** Construccion estructurada: los cuatro bloques del error recuperable. */
    public NegocioException(String codigo, String mensaje, String razon, String accion) {
        this(codigo, mensaje, razon, accion, null);
    }

    private NegocioException(String codigo, String mensaje, String razon,
                             String accion, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
        this.razon = razon;
        this.accion = accion;
    }

    /** Codigo corto de diagnostico (ej. REP-004); puede ser null. */
    public String getCodigo() {
        return codigo;
    }

    /** Explicacion de por que ocurrio; puede ser null. */
    public String getRazon() {
        return razon;
    }

    /** Accion concreta sugerida al usuario; puede ser null. */
    public String getAccion() {
        return accion;
    }

    /** True si la excepcion ya trae su propia estructura de recuperacion. */
    public boolean esEstructurada() {
        return razon != null || accion != null;
    }
}
