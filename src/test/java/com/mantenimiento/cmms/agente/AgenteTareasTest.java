package com.mantenimiento.cmms.agente;

import com.mantenimiento.cmms.exception.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Control constante del usuario: una accion larga se puede observar paso a
 * paso, pausar entre pasos, cancelar sin reiniciar y ver el fallo estructurado.
 */
class AgenteTareasTest {

    private final Trazador trazador = new Trazador();
    private final EstadoAgente estado = new EstadoAgente();
    private final AgenteTareas agente = new AgenteTareas(trazador, estado);

    private static void esperarFin(AgenteTareas t) throws InterruptedException {
        for (int i = 0; i < 200 && t.hayAccion(); i++) {
            Thread.sleep(25);
        }
        assertFalse(t.hayAccion(), "la accion debia haber terminado");
    }

    @Test
    @DisplayName("Un flujo completo deja cada paso en la bitacora y notifica su fin")
    void flujoCompletado() throws Exception {
        CountDownLatch fin = new CountDownLatch(1);
        AtomicReference<AgenteTareas.Resultado> res = new AtomicReference<>();

        agente.ejecutar("Generar reporte", paso -> {
            paso.avanzar(TipoTraza.CONSULTA, "Leyendo datos del periodo",
                    "12 registros", "base de datos", "periodo elegido");
            paso.avanzar(TipoTraza.RESULTADO, "Archivo generado",
                    "reporte.pdf (18 KB)", "disco local", "escritura sin errores");
        }, r -> {
            res.set(r);
            fin.countDown();
        });

        assertTrue(agente.hayAccion(), "la accion debia estar en curso");
        assertTrue(fin.await(5, TimeUnit.SECONDS), "la accion no termino a tiempo");
        esperarFin(agente);

        assertFalse(res.get().cancelado());
        assertNull(res.get().fallo());
        assertEquals("completada", res.get().mensaje());

        String bitacora = trazador.copiarTodo();
        assertTrue(bitacora.contains("Accion iniciada: Generar reporte"));
        assertTrue(bitacora.contains("Leyendo datos del periodo"));
        assertTrue(bitacora.contains("Accion completada: Generar reporte"));
        assertEquals("Completada: Generar reporte", estado.actividadProperty().get());
    }

    @Test
    @DisplayName("Cancelar detiene el flujo sin reiniciar nada y lo deja en la bitacora")
    void cancelarSinReiniciar() throws Exception {
        CountDownLatch fin = new CountDownLatch(1);
        AtomicReference<AgenteTareas.Resultado> res = new AtomicReference<>();

        agente.ejecutar("Tarea larga", paso -> {
            for (int i = 0; i < 100; i++) {
                paso.avanzar(TipoTraza.AVISO, "Trabajo incremental " + i,
                        "paso " + i, "agente", null);
                Thread.sleep(40);
            }
        }, r -> {
            res.set(r);
            fin.countDown();
        });

        Thread.sleep(150);
        agente.cancelar();

        assertTrue(fin.await(5, TimeUnit.SECONDS), "la cancelacion no surtio efecto");
        esperarFin(agente);

        assertTrue(res.get().cancelado());
        assertNull(res.get().fallo());
        String bitacora = trazador.copiarTodo();
        assertTrue(bitacora.contains("Cancelacion solicitada: Tarea larga"));
        assertTrue(bitacora.contains("Accion cancelada: Tarea larga"));
    }

    @Test
    @DisplayName("La pausa detiene el flujo entre pasos y la reanudacion lo continua")
    void pausaEntrePasos() throws Exception {
        CountDownLatch fin = new CountDownLatch(1);
        AtomicReference<AgenteTareas.Resultado> res = new AtomicReference<>();
        AtomicInteger pasos = new AtomicInteger();

        agente.ejecutar("Paso a paso", paso -> {
            for (int i = 0; i < 10; i++) {
                paso.avanzar(TipoTraza.CALCULO, "Bloque " + i,
                        "parcial", "agente", null);
                pasos.incrementAndGet();
                Thread.sleep(60);
            }
        }, r -> {
            res.set(r);
            fin.countDown();
        });

        Thread.sleep(120);
        agente.pausar();
        assertTrue(agente.enPausa());

        Thread.sleep(200); // deja terminar el paso en curso
        int alPausar = pasos.get();
        Thread.sleep(350);
        assertEquals(alPausar, pasos.get(), "la pausa debia congelar el avance");

        agente.reanudar();
        assertTrue(fin.await(5, TimeUnit.SECONDS), "la reanudacion no continuo el flujo");
        esperarFin(agente);

        assertFalse(res.get().cancelado());
        assertFalse(agente.enPausa());
        assertEquals(10, pasos.get());
        String bitacora = trazador.copiarTodo();
        assertTrue(bitacora.contains("Pausa solicitada"));
        assertTrue(bitacora.contains("Accion reanudada"));
    }

    @Test
    @DisplayName("Un fallo llega como resultado estructurado, no rompe el hilo")
    void falloSeEntregaComoResultado() throws Exception {
        CountDownLatch fin = new CountDownLatch(1);
        AtomicReference<AgenteTareas.Resultado> res = new AtomicReference<>();

        agente.ejecutar("Accion que falla", paso -> {
            paso.avanzar(TipoTraza.DECISION, "Paso previo", "", "agente", null);
            throw new NegocioException("Stock insuficiente: solo hay 1 pza.");
        }, r -> {
            res.set(r);
            fin.countDown();
        });

        assertTrue(fin.await(5, TimeUnit.SECONDS));
        esperarFin(agente);

        assertNotNull(res.get().fallo());
        assertTrue(res.get().mensaje().contains("Stock insuficiente"));
        assertFalse(res.get().cancelado());

        String bitacora = trazador.copiarTodo();
        assertTrue(bitacora.contains("Accion fallida: Accion que falla"));
        assertEquals("Fallo: Accion que falla", estado.actividadProperty().get());
    }

    @Test
    @DisplayName("No se permiten dos acciones a la vez (el dock lo impide)")
    void noPermiteDosAcciones() throws Exception {
        CountDownLatch fin = new CountDownLatch(1);
        agente.ejecutar("Primera", paso -> {
            Thread.sleep(150);
        }, r -> fin.countDown());

        assertThrows(IllegalStateException.class,
                () -> agente.ejecutar("Segunda", paso -> { }, r -> { }));

        assertTrue(fin.await(5, TimeUnit.SECONDS));
        esperarFin(agente);
        assertFalse(agente.hayAccion());
    }
}
