package com.mantenimiento.cmms.agente;

import com.mantenimiento.cmms.ui.Fx;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bitacora del agente: registro central de cada paso que ejecuta el sistema
 * (consultas, calculos, decisiones, avisos, errores e intervenciones del
 * usuario) con su fuente, su criterio y su duracion.
 * <p>
 * Doble estructura a proposito: {@link #historial()} es la fuente de verdad
 * sincrona (lo que se audita/exporta y lo que afirman las pruebas) y
 * {@link #espejo()} es la lista observable que pinta la tabla de la interfaz
 * desde el hilo de JavaFX.
 */
@Component
public class Trazador {

    /** Tope de pasos conservados: la bitacora es un volutil, no un archivo. */
    public static final int LIMITE = 500;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final List<PasoTraza> historial = new CopyOnWriteArrayList<>();
    private final ObservableList<PasoTraza> espejo = FXCollections.observableArrayList();
    private final AtomicLong secuencia = new AtomicLong();

    // ------------------------------------------------------------ acceso

    public List<PasoTraza> historial() {
        return historial;
    }

    /** Lista observable para vincular a la tabla de la bitacora. */
    public ObservableList<PasoTraza> espejo() {
        return espejo;
    }

    public int total() {
        return historial.size();
    }

    // ---------------------------------------------------------- registro

    public void registrar(TipoTraza tipo, String titulo, String detalle,
                          String fuente, String criterio, long duracionMs) {
        PasoTraza paso = new PasoTraza(
                secuencia.incrementAndGet(),
                LocalDateTime.now(),
                tipo,
                limpio(titulo),
                limpio(detalle),
                limpio(fuente),
                limpio(criterio),
                duracionMs);
        historial.add(paso);
        while (historial.size() > LIMITE) {
            historial.remove(0);
        }
        Fx.ejecutar(() -> {
            if (!historial.contains(paso)) {
                return; // se limpio la bitacora mientras se encolaba este paso
            }
            espejo.add(paso);
            while (espejo.size() > LIMITE) {
                espejo.remove(0);
            }
        });
    }

    /** Dato recuperado de la base de datos. */
    public void consulta(String fuente, String titulo, String detalle, long ms) {
        registrar(TipoTraza.CONSULTA, titulo, detalle, fuente, null, ms);
    }

    /** Valor derivado por el sistema (no es un dato almacenado). */
    public void calculo(String titulo, String detalle, String fuente, String criterio) {
        registrar(TipoTraza.CALCULO, titulo, detalle, fuente, criterio, -1);
    }

    /** Regla de negocio aplicada, con su criterio explicito. */
    public void decision(String titulo, String criterio, String detalle) {
        registrar(TipoTraza.DECISION, titulo, detalle, "reglas de negocio", criterio, -1);
    }

    public void resultado(String titulo, String detalle, String fuente) {
        registrar(TipoTraza.RESULTADO, titulo, detalle, fuente, null, -1);
    }

    public void aviso(String titulo, String detalle) {
        registrar(TipoTraza.AVISO, titulo, detalle, "vigilancia del agente", null, -1);
    }

    public void error(String titulo, String detalle, String fuente) {
        registrar(TipoTraza.ERROR, titulo, detalle, fuente, null, -1);
    }

    // ---------------------------------------------------------- exportar

    /** Texto completo para copiar al portapapeles (auditoria en un clic). */
    public String copiarTodo() {
        StringBuilder sb = new StringBuilder();
        sb.append("BITACORA DEL AGENTE - ManteniMax\n");
        sb.append("Generado: ").append(LocalDateTime.now().format(FECHA)).append('\n');
        sb.append("Pasos registrados: ").append(historial.size()).append('\n');
        sb.append("=".repeat(72)).append("\n\n");
        for (PasoTraza paso : historial) {
            sb.append(paso.comoTexto()).append("\n\n");
        }
        return sb.toString();
    }

    /** Exporta la bitacora completa como archivo de texto. */
    public void exportar(File destino) throws IOException {
        Files.writeString(destino.toPath(), copiarTodo(), StandardCharsets.UTF_8);
    }

    public void limpiar() {
        historial.clear();
        Fx.ejecutar(espejo::clear);
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? "" : texto.trim();
    }

    /** Lineas ya formateadas para construir vistas previas rapidas. */
    public List<String> ultimas(int cantidad) {
        List<PasoTraza> todos = new ArrayList<>(historial);
        int desde = Math.max(0, todos.size() - cantidad);
        List<String> lineas = new ArrayList<>();
        for (int i = desde; i < todos.size(); i++) {
            PasoTraza p = todos.get(i);
            lineas.add(p.horaFormateada() + " [" + p.tipo().getEtiqueta() + "] " + p.titulo());
        }
        return lineas;
    }
}
