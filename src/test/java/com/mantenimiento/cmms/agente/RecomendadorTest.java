package com.mantenimiento.cmms.agente;

import com.mantenimiento.cmms.IntegracionBase;
import com.mantenimiento.cmms.model.Equipo;
import com.mantenimiento.cmms.model.EstadoEquipo;
import com.mantenimiento.cmms.model.EstadoOrden;
import com.mantenimiento.cmms.model.OrdenTrabajo;
import com.mantenimiento.cmms.model.Prioridad;
import com.mantenimiento.cmms.model.Repuesto;
import com.mantenimiento.cmms.model.TipoOrden;
import com.mantenimiento.cmms.service.EquipoService;
import com.mantenimiento.cmms.service.OrdenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El agente propone con fuente y criterio; el usuario mantiene el control:
 * cada sugerencia puede aplicarse o descartarse, y las descartadas se restauran.
 */
class RecomendadorTest extends IntegracionBase {

    @Autowired
    private Recomendador recomendador;

    @Autowired
    private OrdenService ordenService;

    @Autowired
    private EquipoService equipoService;

    @Test
    @DisplayName("Un repuesto sin existencias genera una sugerencia de reposicion trazable")
    void stockAgotadoSugiereReposicion() {
        Repuesto agotado = repuestoNuevo("RP-SIN-STOCK", 0);

        var sugerencias = recomendador.sugerir();
        var reposicion = sugerencias.stream()
                .filter(s -> s.tipo() == Recomendador.Tipo.REPOSICION)
                .filter(s -> agotado.getId().equals(s.referencia()))
                .findFirst();

        assertTrue(reposicion.isPresent(), "debia sugerirse la reposicion del repuesto agotado");
        var s = reposicion.get();
        assertFalse(s.fuente().isBlank(), "toda sugerencia declara su fuente");
        assertFalse(s.criterio().isBlank(), "toda sugerencia declara su criterio");
        assertTrue(s.criterio().contains("stock"));
        assertTrue(s.cantidad() > 0, "propone una cantidad concreta");
        assertEquals("peligro", s.prioridad(), "estar en cero es critico");
    }

    @Test
    @DisplayName("El usuario descarta una sugerencia y puede restaurarla (override)")
    void ignorarYRestaurar() {
        Repuesto bajo = repuestoNuevo("RP-DESCARTABLE", 0);
        String id = "STOCK-" + bajo.getId();

        assertTrue(recomendador.sugerir().stream()
                        .anyMatch(s -> id.equals(s.id())),
                "aparece mientras nadie la descarte");

        recomendador.ignorar(id);
        assertTrue(recomendador.sugerir().stream().noneMatch(s -> id.equals(s.id())),
                "descartada: ya no vuelve a aparecer");
        assertEquals(1, recomendador.ignoradasCount());

        recomendador.restaurar();
        assertTrue(recomendador.sugerir().stream().anyMatch(s -> id.equals(s.id())),
                "restaurada: vuelve a proponerse");
        assertEquals(0, recomendador.ignoradasCount());
    }

    @Test
    @DisplayName("Un equipo detenido sin orden activa sugiere abrir la correctiva")
    void equipoDetenidoSugiereCorrectiva() {
        Equipo equipo = equipoNuevo("E-DET-SUG");
        equipoService.cambiarEstado(equipo.getId(), EstadoEquipo.DETENIDO);

        assertTrue(recomendador.sugerir().stream()
                        .anyMatch(s -> s.tipo() == Recomendador.Tipo.CORRECTIVA
                                && equipo.getId().equals(s.referencia())),
                "equipo DETENIDO y sin orden activa debe proponer correctiva");
    }

    @Test
    @DisplayName("Una orden abierta con fecha pasada sugiere revision")
    void ordenVencidaSugiereRevision() {
        Equipo equipo = equipoNuevo("E-OT-VENC");
        OrdenTrabajo vencida = ordenService.crear(OrdenTrabajo.builder()
                .equipo(equipo)
                .tipo(TipoOrden.CORRECTIVO)
                .prioridad(Prioridad.CRITICA)
                .titulo("Mantenimiento atrasado")
                .descripcion("Programada para una fecha que ya paso")
                .fechaProgramada(LocalDate.now().minusDays(3))
                .build());
        assertEquals(EstadoOrden.ABIERTA, vencida.getEstado());

        var sugerencias = recomendador.sugerir();
        assertTrue(sugerencias.stream()
                        .anyMatch(s -> s.tipo() == Recomendador.Tipo.ORDENES_VENCIDAS),
                "debia proponerse revisar las ordenes vencidas");
        var revision = sugerencias.stream()
                .filter(s -> s.tipo() == Recomendador.Tipo.ORDENES_VENCIDAS)
                .findFirst().orElseThrow();
        assertTrue(revision.titulo().toLowerCase().contains("vencida"));
        assertFalse(revision.detalle().isBlank());
        assertFalse(revision.fuente().isBlank());
    }
}
