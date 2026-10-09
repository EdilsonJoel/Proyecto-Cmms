package com.mantenimiento.cmms.agente;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Un paso de la bitacora: que hizo el agente, con que fuente, bajo que criterio
 * y cuanto tardo. Es inmutable; la tabla de la bitacora solo los muestra.
 *
 * @param id         correlativo para ordenar y referenciar
 * @param hora       momento exacto del paso
 * @param tipo       consulta / calculo / decision / resultado / aviso / error / control
 * @param titulo     actividad en una frase
 * @param detalle    datos concretos (filas, importes, identificadores)
 * @param fuente     de donde salio el dato (tabla, servicio, archivo)
 * @param criterio   por que el agente hizo lo que hizo (regla aplicada)
 * @param duracionMs duracion en milisegundos, -1 si no aplica
 */
public record PasoTraza(long id,
                        LocalDateTime hora,
                        TipoTraza tipo,
                        String titulo,
                        String detalle,
                        String fuente,
                        String criterio,
                        long duracionMs) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public String horaFormateada() {
        return hora.format(HORA);
    }

    /** Duracion legible: "&lt;1 ms", "84 ms" o "1,4 s". */
    public String duracionFormateada() {
        if (duracionMs < 0) {
            return "—";
        }
        if (duracionMs == 0) {
            return "<1 ms";
        }
        if (duracionMs < 1000) {
            return duracionMs + " ms";
        }
        return String.format(java.util.Locale.US, "%.1f s", duracionMs / 1000.0);
    }

    /** Texto plano para copiar al portapapeles o exportar (auditoria). */
    public String comoTexto() {
        StringBuilder sb = new StringBuilder();
        sb.append(horaFormateada()).append("  [").append(tipo.getEtiqueta().toUpperCase()).append("]  ").append(titulo);
        if (detalle != null && !detalle.isBlank()) {
            sb.append("\n    detalle: ").append(detalle);
        }
        if (fuente != null && !fuente.isBlank()) {
            sb.append("\n    fuente:  ").append(fuente);
        }
        if (criterio != null && !criterio.isBlank()) {
            sb.append("\n    criterio: ").append(criterio);
        }
        if (duracionMs >= 0) {
            sb.append("\n    duracion: ").append(duracionFormateada());
        }
        return sb.toString();
    }
}
