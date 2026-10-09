package com.mantenimiento.cmms.agente;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Trazador automatico: intercepta cada llamada a un servicio y deja en la
 * bitacora la <b>fuente consultada</b>, los parametros resumidos, el resultado
 * (n de registros) y la duracion. Es la capa de transparencia "sin que nadie
 * tenga que acordarse de instrumentar nada": toda consulta de la aplicacion
 * aparece sola en la bitacora del agente.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class TrazasAspect {

    /** Nombre amable de la fuente por servicio (lo que ve el usuario). */
    private static final Map<String, String> FUENTES = Map.ofEntries(
            Map.entry("OrdenService", "ordenes de trabajo"),
            Map.entry("PreventivoService", "planes preventivos"),
            Map.entry("EquipoService", "equipos"),
            Map.entry("RepuestoService", "repuestos y kardex"),
            Map.entry("TecnicoService", "tecnicos"),
            Map.entry("CatalogoService", "catalogos maestros"),
            Map.entry("UsuarioService", "usuarios y permisos"),
            Map.entry("SesionService", "sesion de usuario"),
            Map.entry("DashboardService", "indicadores del tablero"),
            Map.entry("ReporteExcelService", "reportes de Excel"),
            Map.entry("ReportePdfService", "reportes de PDF"),
            Map.entry("OrdenPdfService", "ficha PDF de la orden"),
            Map.entry("Seguridad", "hash de contrasenas"));

    private final Trazador trazador;
    private final EstadoAgente estado;

    @Around("execution(* com.mantenimiento.cmms.service..*.*(..))")
    public Object trazar(ProceedingJoinPoint punto) throws Throwable {
        String clase = punto.getSignature().getDeclaringType().getSimpleName();
        String metodo = punto.getSignature().getName();
        String fuente = FUENTES.getOrDefault(clase, clase);
        String llamada = metodo + "(" + resumenArgs(punto.getArgs()) + ")";

        boolean accionEnCurso = estado.hayAccion();
        if (!accionEnCurso) {
            estado.consultando(fuente, metodo + "()");
        }
        long inicio = System.nanoTime();
        try {
            Object resultado = punto.proceed();
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            trazador.consulta(fuente, clase + "." + llamada,
                    resumenResultado(resultado) + " · " + ms + " ms", ms);
            if (!accionEnCurso) {
                estado.consultado(fuente, metodo + "()", ms);
            }
            return resultado;
        } catch (Throwable e) {
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            trazador.registrar(TipoTraza.ERROR,
                    "Fallo al consultar " + clase + "." + llamada,
                    e.getMessage(), fuente, "excepcion propagada al llamador", ms);
            throw e;
        }
    }

    // ------------------------------------------------------------- resumenes

    private static String resumenArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(resumenValor(args[i]));
            if (sb.length() > 120) {
                sb.append("…");
                break;
            }
        }
        return sb.toString();
    }

    private static String resumenValor(Object valor) {
        if (valor == null) {
            return "—";
        }
        if (valor instanceof String s) {
            String t = s.strip();
            if (t.isEmpty()) {
                return "\"\"";
            }
            return t.length() > 30 ? "\"" + t.substring(0, 30) + "…\"" : "\"" + t + "\"";
        }
        if (valor instanceof Number || valor instanceof Boolean
                || valor instanceof java.time.temporal.Temporal) {
            return String.valueOf(valor);
        }
        if (valor instanceof Enum<?> e) {
            return e.name();
        }
        if (valor instanceof Collection<?> c) {
            return c.size() + " elementos";
        }
        if (valor instanceof Object[] a) {
            return a.length + " elementos";
        }
        return valor.getClass().getSimpleName();
    }

    private static String resumenResultado(Object resultado) {
        if (resultado == null) {
            return "sin datos que devolver";
        }
        if (resultado instanceof Collection<?> c) {
            return c.size() + (c.size() == 1 ? " registro" : " registros");
        }
        if (resultado instanceof org.springframework.data.domain.Page<?> p) {
            return "pagina " + (p.getNumber() + 1) + " de " + Math.max(p.getTotalPages(), 1)
                    + " (" + p.getNumberOfElements() + " filas de " + p.getTotalElements() + ")";
        }
        if (resultado instanceof Boolean b) {
            return b ? "true (si)" : "false (no)";
        }
        if (resultado instanceof Number n) {
            return String.valueOf(n);
        }
        return "ok";
    }

    /** Para trazas manuales que quieren el mismo formato de lista. */
    public static String lista(List<?> elementos) {
        return elementos == null ? "sin datos"
                : elementos.size() + (elementos.size() == 1 ? " registro" : " registros");
    }
}
