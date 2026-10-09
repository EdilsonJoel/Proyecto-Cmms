package com.mantenimiento.cmms.agente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La bitacora del agente: registro en orden, formato de duracion,
 * limite de pasos y exportacion a texto para auditoria.
 */
class TrazadorTest {

    private final Trazador trazador = new Trazador();

    @Test
    @DisplayName("Registra pasos en orden con fuente, criterio y duracion")
    void registraEnOrden() {
        trazador.consulta("ordenes de trabajo", "OrdenService.listar(ABIERTA)",
                "12 registros · 4 ms", 4);
        trazador.decision("Equipo liberado", "sin otra orden EN_EJECUCION", null);
        trazador.calculo("MTTR calculado", "4.2 h", "derivado de orden_trabajo", "n=87 cierres");

        assertEquals(3, trazador.total());
        var pasos = trazador.historial();
        assertEquals(TipoTraza.CONSULTA, pasos.get(0).tipo());
        assertEquals("ordenes de trabajo", pasos.get(0).fuente());
        assertEquals(4, pasos.get(0).duracionMs());
        assertEquals(TipoTraza.DECISION, pasos.get(1).tipo());
        assertEquals("sin otra orden EN_EJECUCION", pasos.get(1).criterio());
        assertEquals(TipoTraza.CALCULO, pasos.get(2).tipo());
        // los ids correlativos crecen en el mismo orden de registro
        assertTrue(pasos.get(0).id() < pasos.get(1).id());
        assertTrue(pasos.get(1).id() < pasos.get(2).id());
    }

    @Test
    @DisplayName("copiarTodo() produce un texto auditable con cabecera y criterios")
    void copiaTodo() {
        trazador.consulta("usuarios", "UsuarioService.autenticar()", "1 registro", 2);
        trazador.error("Inicio rechazado", "SEG-001: credenciales no coinciden", "usuarios");

        String texto = trazador.copiarTodo();
        assertTrue(texto.contains("BITACORA DEL AGENTE"));
        assertTrue(texto.contains("UsuarioService.autenticar()"));
        assertTrue(texto.contains("criterio") || texto.contains("fuente"));
        assertTrue(texto.contains("[ERROR]"));
        assertTrue(texto.contains("SEG-001"));
    }

    @Test
    @DisplayName("La bitacora es un volutil: conserva solo los ultimos 500 pasos")
    void respetaElLimite() {
        for (int i = 0; i < Trazador.LIMITE + 90; i++) {
            trazador.aviso("Paso " + i, "detalle");
        }
        assertEquals(Trazador.LIMITE, trazador.total());
        // el paso mas antiguo salio y el mas reciente sigue
        assertFalse(trazador.copiarTodo().contains("Paso 0\n"));
        assertTrue(trazador.copiarTodo().contains("Paso " + (Trazador.LIMITE + 89)));
    }

    @Test
    @DisplayName("limpiar() vacia la bitacora por completo")
    void limpia() {
        trazador.consulta("equipos", "EquipoService.todos()", "5 registros", 1);
        assertEquals(1, trazador.total());
        trazador.limpiar();
        assertEquals(0, trazador.total());
        assertTrue(trazador.ultimas(10).isEmpty());
    }

    @Test
    @DisplayName("La duracion se formatea de forma legible y sin alarmar")
    void formateaDuracion() {
        PasoTraza sin = new PasoTraza(1, java.time.LocalDateTime.now(),
                TipoTraza.DECISION, "t", "", "", "", -1);
        assertEquals("—", sin.duracionFormateada());

        PasoTraza casi = new PasoTraza(2, java.time.LocalDateTime.now(),
                TipoTraza.CONSULTA, "t", "", "", "", 0);
        assertEquals("<1 ms", casi.duracionFormateada());

        PasoTraza ms = new PasoTraza(3, java.time.LocalDateTime.now(),
                TipoTraza.CONSULTA, "t", "", "", "", 84);
        assertEquals("84 ms", ms.duracionFormateada());

        PasoTraza seg = new PasoTraza(4, java.time.LocalDateTime.now(),
                TipoTraza.CALCULO, "t", "", "", "", 1400);
        assertEquals("1.4 s", seg.duracionFormateada());
    }

    @Test
    @DisplayName("Los pasos se reflejan en la lista observable para la interfaz")
    void espejoReflejaElHistorial() throws InterruptedException {
        trazador.consulta("repuestos", "RepuestoService.alertasStock()", "2 registros", 1);
        // sin toolkit el reflejo es sincrono; con toolkit puede tardar un pulso
        for (int i = 0; i < 100 && trazador.espejo().isEmpty(); i++) {
            Thread.sleep(20);
        }
        assertEquals(1, trazador.espejo().size());
        assertEquals("repuestos", trazador.espejo().get(0).fuente());
    }
}
