package com.mantenimiento.cmms.agente;

import com.mantenimiento.cmms.ui.Fx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Ejecuta acciones largas como <b>flujos de pasos controlables</b>: el usuario
 * puede pausar entre pasos y cancelar en cualquier momento sin reiniciar nada,
 * y cada paso queda en la bitacora. Los pasos "criticos" (escrituras atomicas)
 * no se interrumpen: se ejecutan completos y la cancelacion surte efecto en el
 * paso siguiente, cosa que la interfaz explica en vez de fingir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteTareas {

    /** Un paso del flujo: deja su traza y actualiza el dock. */
    @FunctionalInterface
    public interface Paso {
        void avanzar(TipoTraza tipo, String titulo, String detalle,
                     String fuente, String criterio) throws InterruptedException;
    }

    /** El flujo completo; puede lanzar cualquier excepcion. */
    @FunctionalInterface
    public interface Flujo {
        void ejecutar(Paso paso) throws Exception;
    }

    /** Resultado entregado al solicitante cuando la accion termina. */
    public record Resultado(String nombre, boolean cancelado, Exception fallo, String mensaje) {
    }

    private static final class CanceladoException extends RuntimeException {
    }

    private final Trazador trazador;
    private final EstadoAgente estado;

    private final Object candado = new Object();
    private volatile boolean pausa;
    private volatile boolean cancelado;
    private volatile Thread hilo;
    private volatile String nombreActual;
    private volatile Consumer<Resultado> escucha;

    // ------------------------------------------------------------ control

    /** True si hay una accion ejecutandose ahora mismo. */
    public boolean hayAccion() {
        Thread t = hilo;
        return t != null && t.isAlive();
    }

    public boolean enPausa() {
        return pausa;
    }

    /**
     * Lanza la accion en un hilo propio. Si ya hay otra en curso lanza
     * {@link IllegalStateException}: la interfaz lo evita consultando
     * {@link #hayAccion()}.
     */
    public synchronized void ejecutar(String nombre, Flujo flujo, Consumer<Resultado> alTerminar) {
        if (hayAccion()) {
            throw new IllegalStateException("Ya hay una accion en curso: " + nombreActual);
        }
        pausa = false;
        cancelado = false;
        nombreActual = nombre;
        escucha = alTerminar;

        estado.empezar(nombre);
        trazador.registrar(TipoTraza.CONTROL, "Accion iniciada: " + nombre,
                "El usuario lanzo la accion desde la interfaz",
                "agente de mantenimiento", "confirmacion explicita del usuario", -1);

        Thread t = new Thread(() -> correr(flujo), "agente-tarea");
        t.setDaemon(true);
        hilo = t;
        t.start();
    }

    /** Pausa suave: el paso en curso termina y el flujo se detiene ahi. */
    public void pausar() {
        if (!hayAccion()) {
            return;
        }
        pausa = true;
        estado.pausar(true);
        trazador.registrar(TipoTraza.CONTROL, "Pausa solicitada: " + nombreActual,
                "El flujo se detendra al terminar el paso en curso",
                "control del usuario", "pausa permitida solo entre pasos", -1);
    }

    public void reanudar() {
        if (!hayAccion()) {
            return;
        }
        pausa = false;
        estado.pausar(false);
        trazador.registrar(TipoTraza.CONTROL, "Accion reanudada: " + nombreActual,
                "El usuario retomo el flujo", "control del usuario", null, -1);
        synchronized (candado) {
            candado.notifyAll();
        }
    }

    /** Cancela: los pasos criticos no se interrumpen a mitad de escritura. */
    public void cancelar() {
        if (!hayAccion()) {
            return;
        }
        cancelado = true;
        pausa = false;
        synchronized (candado) {
            candado.notifyAll();
        }
        Thread t = hilo;
        if (t != null) {
            t.interrupt();
        }
        trazador.registrar(TipoTraza.CONTROL, "Cancelacion solicitada: " + nombreActual,
                "Surtira efecto en el proximo paso; un paso critico terminara primero",
                "control del usuario", "el usuario puede detener cualquier accion", -1);
    }

    // ----------------------------------------------------------- ejecucion

    private void correr(Flujo flujo) {
        String nombre = nombreActual;
        Resultado resultado;
        try {
            flujo.ejecutar(this::avanzar);
            resultado = new Resultado(nombre, false, null, "completada");
        } catch (CanceladoException | InterruptedException e) {
            Thread.currentThread().interrupt();
            resultado = new Resultado(nombre, true, null, "cancelada por el usuario");
        } catch (Exception e) {
            log.error("La accion {} fallo", nombre, e);
            resultado = new Resultado(nombre, false, e, "fallo: " + e.getMessage());
        }

        final Resultado fin = resultado;
        TipoTraza tipoFinal = fin.cancelado() ? TipoTraza.AVISO
                : fin.fallo() != null ? TipoTraza.ERROR : TipoTraza.RESULTADO;
        trazador.registrar(tipoFinal,
                "Accion " + (fin.cancelado() ? "cancelada"
                        : fin.fallo() != null ? "fallida" : "completada") + ": " + nombre,
                fin.mensaje(), "agente de mantenimiento",
                fin.cancelado() ? "decision del usuario" : null, -1);

        Fx.ejecutar(() -> {
            estado.terminar(fin.cancelado() ? "Cancelada: " + nombre
                    : fin.fallo() != null ? "Fallo: " + nombre : "Completada: " + nombre);
            Consumer<Resultado> oyente = escucha;
            escucha = null;
            if (oyente != null) {
                oyente.accept(fin);
            }
        });
    }

    private void avanzar(TipoTraza tipo, String titulo, String detalle,
                         String fuente, String criterio) throws InterruptedException {
        esperarPausaCancelacion();
        trazador.registrar(tipo, titulo, detalle, fuente, criterio, -1);
        Fx.ejecutar(() -> estado.paso(tipo, titulo));
    }

    private void esperarPausaCancelacion() throws InterruptedException {
        synchronized (candado) {
            while (pausa && !cancelado) {
                candado.wait(200);
            }
        }
        if (cancelado) {
            throw new CanceladoException();
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
    }
}
