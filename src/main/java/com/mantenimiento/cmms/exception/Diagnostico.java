package com.mantenimiento.cmms.exception;

import java.util.Locale;

/**
 * Convierte cualquier {@link NegocioException} en un diagnostico estructurado
 * de tres bloques: <b>que paso</b>, <b>por que ocurrio</b> y <b>que hacer
 * ahora</b>, mas un codigo corto. Si la excepcion ya trae su estructura se
 * respeta; si no, se deriva del mensaje con un diccionario de las reglas
 * conocidas del CMMS. Diseñado primero el caso de fallo: cada error deja al
 * usuario con un siguiente paso claro, nunca con un texto suelto.
 */
public final class Diagnostico {

    /** Los cuatro bloques que la interfaz necesita para mostrar un fallo. */
    public record Estructura(String codigo, String quePaso, String porque, String accion) {
    }

    private Diagnostico() {
    }

    public static Estructura de(NegocioException e) {
        String mensaje = e.getMessage() == null ? "Error de negocio sin descripcion." : e.getMessage();
        if (e.esEstructurada()) {
            return new Estructura(
                    e.getCodigo() != null ? e.getCodigo() : "NEG-001",
                    mensaje,
                    e.getRazon() != null ? e.getRazon()
                            : "Una regla del negocio rechazo la operacion antes de tocar la base de datos.",
                    e.getAccion() != null ? e.getAccion()
                            : "Revisa los campos marcados e intentalo de nuevo.");
        }
        return derivar(mensaje, e);
    }

    private static Estructura derivar(String mensaje, NegocioException e) {
        String m = normalizar(mensaje);

        if (contiene(m, "stock", "existencia")) {
            return new Estructura("REP-004", mensaje,
                    "El kardex no cubre la salida pedida: no hay existencias suficientes del repuesto.",
                    "Registra una entrada en Kardex (boton Movimiento) o reduce la cantidad solicitada.");
        }
        if (contiene(m, "solucion", "trabajo realizado")) {
            return new Estructura("OT-006", mensaje,
                    "Completar una orden exige dejar constancia de que se hizo (es el registro auditable).",
                    "Escribe el trabajo realizado en el campo 'Solucion' y vuelve a intentarlo.");
        }
        if (contiene(m, "tecnico")) {
            return new Estructura("OT-003", mensaje,
                    "El flujo exige un tecnico responsable para trazar quien atendio el equipo.",
                    "Asigna un tecnico activo en el formulario antes de continuar.");
        }
        if (contiene(m, "cancelar", "cerrada", "estado", "iniciar")) {
            return new Estructura("OT-002", mensaje,
                    "La orden esta en un estado que no permite esa transicion: el ciclo de vida es ABIERTA > EN EJECUCION > COMPLETADA/CANCELADA.",
                    "Si la orden ya cerro, crea una nueva orden vinculada al mismo equipo; si esta abierta, elige la accion que corresponde a su estado.");
        }
        if (contiene(m, "baja")) {
            return new Estructura("EQ-002", mensaje,
                    "Los equipos dados de baja quedan fuera de la operacion para no distorsionar indicadores ni costos.",
                    "Reactiva el equipo desde su ficha o elige otro equipo activo.");
        }
        if (contiene(m, "frecuencia")) {
            return new Estructura("PRE-003", mensaje,
                    "La frecuencia del plan debe ser un numero de dias valido para programar la proxima fecha.",
                    "Corrige la frecuencia (ej. 30 para mensual) y guarda de nuevo.");
        }
        if (contiene(m, "fecha", "periodo", "desde", "hasta")) {
            return new Estructura("GEN-003", mensaje,
                    "El periodo indicado no es coherente: la fecha inicial debe ser anterior a la final.",
                    "Ajusta el rango de fechas e intentalo otra vez.");
        }
        if (contiene(m, "usuario", "contrasena", "credencial", "autentic")) {
            return new Estructura("SEG-001", mensaje,
                    "El usuario o la contrasena no coinciden con ninguna cuenta activa; la comparacion es por hash, no se almacena la clave en claro.",
                    "Revisa mayusculas y espacios. Si olvidaste la clave, pide al administrador que la reinicie.");
        }
        if (contiene(m, "rol", "permiso", "administr")) {
            return new Estructura("SEG-005", mensaje,
                    "Tu rol de sesion no tiene permiso para esa operacion (control de acceso por rol).",
                    "Pide el acceso a un usuario con rol Administrador o entra con una cuenta que lo tenga.");
        }
        if (contiene(m, "existe", "duplicad", "unico", "registrado")) {
            return new Estructura("CAT-003", mensaje,
                    "Ya hay un registro con ese identificador: la base de datos exige unicidad.",
                    "Usa otro nombre/codigo o edita el registro existente en su tabla.");
        }
        if (contiene(m, "plan", "preventiv")) {
            return new Estructura("PRE-002", mensaje,
                    "El plan preventivo choca con una regla de programacion (equipo, fecha o duplicidad).",
                    "Revisa el equipo asignado, la proxima fecha y que no exista un plan igual.");
        }
        if (contiene(m, "equipo", "valido", "selecciona")) {
            return new Estructura("EQ-001", mensaje,
                    "La referencia indicada ya no existe o no es valida para esta operacion.",
                    "Vuelve a seleccionar el registro en la lista (podria haber sido editado o eliminado).");
        }
        if (e.getCause() != null) {
            return new Estructura("PERSIST-001", mensaje,
                    "Fallo tecnico al guardar en la base de datos: " + e.getCause().getMessage(),
                    "Reintenta; si persiste, copia el diagnostico y reportalo al soporte.");
        }
        return new Estructura("NEG-001", mensaje,
                "Una regla del negocio rechazo la operacion antes de tocar la base de datos.",
                "Revisa los campos marcados en el formulario e intentalo de nuevo.");
    }

    private static boolean contiene(String mensajeNormalizado, String... claves) {
        for (String clave : claves) {
            if (mensajeNormalizado.contains(clave)) {
                return true;
            }
        }
        return false;
    }

    /** Minusculas y sin acentos para comparar mensajes con y sin tilde. */
    private static String normalizar(String texto) {
        return texto.toLowerCase(Locale.ROOT)
                .replace('á', 'a').replace('é', 'e').replace('í', 'i')
                .replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n');
    }
}
