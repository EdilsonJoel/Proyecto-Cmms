package com.mantenimiento.cmms.agente;

import com.mantenimiento.cmms.ui.Fx;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.stereotype.Component;

/**
 * Estado publico del agente que el dock inferior refleja en todo momento:
 * <ul>
 *   <li>{@code actividad}: que esta haciendo ahora ("Consultando ordenes...").</li>
 *   <li>{@code progreso}: 0..1 o -1 cuando es indeterminado.</li>
 *   <li>{@code enCurso}/{@code pausado}: habilitan los botones de control.</li>
 *   <li>{@code chip}: origen del ultimo paso (BD, calc, decision...).</li>
 * </ul>
 * Todas las escrituras pasan por {@link Fx} para respetar el hilo de JavaFX.
 */
@Component
public class EstadoAgente {

    private final StringProperty actividad = new SimpleStringProperty("Listo — sin acciones en curso");
    private final DoubleProperty progreso = new SimpleDoubleProperty(-1);
    private final BooleanProperty enCurso = new SimpleBooleanProperty(false);
    private final BooleanProperty pausado = new SimpleBooleanProperty(false);
    private final StringProperty chip = new SimpleStringProperty("");

    // ------------------------------------------------------------ lectura

    public StringProperty actividadProperty() {
        return actividad;
    }

    public DoubleProperty progresoProperty() {
        return progreso;
    }

    public BooleanProperty enCursoProperty() {
        return enCurso;
    }

    public BooleanProperty pausadoProperty() {
        return pausado;
    }

    public StringProperty chipProperty() {
        return chip;
    }

    public boolean hayAccion() {
        return enCurso.get();
    }

    // ---------------------------------------------------------- escritura

    /** Arranca una accion controlable. */
    public void empezar(String nombre) {
        Fx.ejecutar(() -> {
            enCurso.set(true);
            pausado.set(false);
            progreso.set(-1);
            chip.set("");
            actividad.set("En curso: " + nombre);
        });
    }

    /** Paso intermedio de una accion. */
    public void paso(TipoTraza tipo, String titulo) {
        Fx.ejecutar(() -> {
            chip.set(tipo.getChip());
            actividad.set(titulo);
        });
    }

    /** Consulta en vuelo disparada por el trazador automatico. */
    public void consultando(String fuente, String metodo) {
        Fx.ejecutar(() -> {
            if (!enCurso.get()) {
                chip.set(TipoTraza.CONSULTA.getChip());
                actividad.set("Consultando " + fuente + " · " + metodo);
            }
        });
    }

    /** Fin de una consulta: se informa cuanto tardo (comunicacion proactiva). */
    public void consultado(String fuente, String metodo, long ms) {
        Fx.ejecutar(() -> {
            if (!enCurso.get()) {
                chip.set(TipoTraza.CONSULTA.getChip());
                actividad.set("Consulté " + fuente + " · " + metodo + " · " + ms + " ms");
            }
        });
    }

    public void progreso(double valor) {
        Fx.ejecutar(() -> progreso.set(valor));
    }

    public void pausar(boolean valor) {
        Fx.ejecutar(() -> pausado.set(valor));
    }

    /** La accion termino (bien, cancelada o fallida). */
    public void terminar(String resumen) {
        Fx.ejecutar(() -> {
            enCurso.set(false);
            pausado.set(false);
            progreso.set(-1);
            chip.set("");
            actividad.set(resumen);
        });
    }
}
