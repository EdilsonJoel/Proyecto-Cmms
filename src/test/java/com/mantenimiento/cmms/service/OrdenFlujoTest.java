package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.IntegracionBase;
import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reglas del ciclo de vida de una orden de trabajo:
 * creacion, inicio con tecnico, cierre con costos, liberacion del equipo
 * y transiciones invalidas.
 */
class OrdenFlujoTest extends IntegracionBase {

    @Autowired
    private OrdenService ordenService;

    private OrdenTrabajo nuevaOrden(Equipo equipo, Tecnico tecnico, String titulo) {
        return ordenService.crear(OrdenTrabajo.builder()
                .equipo(equipo)
                .tecnico(tecnico)
                .tipo(TipoOrden.CORRECTIVO)
                .prioridad(Prioridad.ALTA)
                .titulo(titulo)
                .descripcion("Descripcion de prueba")
                .fechaProgramada(LocalDate.now().plusDays(2))
                .build());
    }

    @Test
    @DisplayName("Flujo completo: abrir, iniciar, consumir repuestos y completar con costo")
    void flujoCompleto() {
        Equipo equipo = equipoNuevo("E-FLUJO");
        Tecnico tecnico = tecnicoNuevo("90000001", "Tecnico Flujo");
        Repuesto repuesto = repuestoNuevo("RP-FLUJO", 10);

        OrdenTrabajo orden = nuevaOrden(equipo, tecnico, "Cambio de rodamiento");
        assertEquals(EstadoOrden.ABIERTA, orden.getEstado());
        assertTrue(orden.getNumero().matches("OT-\\d{4}"));
        assertEquals(BigDecimal.ZERO, orden.getCostoManoObra());

        // iniciar pone el equipo en mantenimiento
        ordenService.iniciar(orden.getId());
        assertEquals(EstadoOrden.EN_EJECUCION, ordenService.obtener(orden.getId()).getEstado());
        assertEquals(EstadoEquipo.EN_MANTENIMIENTO,
                equipos.findById(equipo.getId()).orElseThrow().getEstado());

        // consumir 3 repuestos de S/ 25.00
        ordenService.agregarRepuesto(orden.getId(), repuesto.getId(), 3);
        assertEquals(7, repuestos.findById(repuesto.getId()).orElseThrow().getStock());

        // completar registra mano de obra y calcula el total
        OrdenTrabajo completa = ordenService.completar(orden.getId(),
                "Se cambio el rodamiento y se alineo el eje",
                new BigDecimal("4.5"), new BigDecimal("173.25"));

        assertEquals(EstadoOrden.COMPLETADA, completa.getEstado());
        assertEquals(new BigDecimal("173.25"), completa.getCostoManoObra());
        assertEquals(0, new BigDecimal("75.00").compareTo(completa.getCostoRepuestos()),
                "3 repuestos de S/ 25.00 = S/ 75.00, fue " + completa.getCostoRepuestos());
        assertEquals(0, new BigDecimal("248.25").compareTo(completa.getCostoTotal()));
        assertNotNull(completa.getFechaFin());

        // el equipo queda operativo de nuevo
        assertEquals(EstadoEquipo.OPERATIVO,
                equipos.findById(equipo.getId()).orElseThrow().getEstado());

        // el consumo dejo movimiento de kardex
        assertTrue(movimientos.findAll().stream()
                .anyMatch(m -> m.getOrden() != null && m.getOrden().getId().equals(orden.getId())
                        && m.getTipo() == TipoMovimiento.SALIDA));
    }

    @Test
    @DisplayName("No se puede iniciar una orden sin tecnico asignado")
    void iniciarSinTecnico() {
        Equipo equipo = equipoNuevo("E-SINTEC");
        OrdenTrabajo orden = nuevaOrden(equipo, null, "Trabajo sin tecnico");

        NegocioException e = assertThrows(NegocioException.class,
                () -> ordenService.iniciar(orden.getId()));
        assertTrue(e.getMessage().toLowerCase().contains("tecnico"));
        assertEquals(EstadoOrden.ABIERTA, ordenService.obtener(orden.getId()).getEstado());
    }

    @Test
    @DisplayName("Completar exige describir el trabajo realizado")
    void completarRequiereSolucion() {
        Equipo equipo = equipoNuevo("E-SOLSIN");
        Tecnico tecnico = tecnicoNuevo("90000002", "Tecnico Solucion");
        OrdenTrabajo orden = nuevaOrden(equipo, tecnico, "Orden sin solucion");
        ordenService.iniciar(orden.getId());

        assertThrows(NegocioException.class,
                () -> ordenService.completar(orden.getId(), "   ",
                        BigDecimal.ONE, BigDecimal.TEN));
        assertThrows(NegocioException.class,
                () -> ordenService.completar(orden.getId(), null,
                        BigDecimal.ONE, BigDecimal.TEN));
        assertEquals(EstadoOrden.EN_EJECUCION, ordenService.obtener(orden.getId()).getEstado());
    }

    @Test
    @DisplayName("Cancelar libera el equipo y guarda el motivo")
    void cancelarLiberaElEquipo() {
        Equipo equipo = equipoNuevo("E-CANCEL");
        Tecnico tecnico = tecnicoNuevo("90000003", "Tecnico Cancela");
        OrdenTrabajo orden = nuevaOrden(equipo, tecnico, "Orden a cancelar");
        ordenService.iniciar(orden.getId());
        assertEquals(EstadoEquipo.EN_MANTENIMIENTO,
                equipos.findById(equipo.getId()).orElseThrow().getEstado());

        OrdenTrabajo cancelada = ordenService.cancelar(orden.getId(),
                "El equipo quedo fuera de servicio por decision de operaciones.");

        assertEquals(EstadoOrden.CANCELADA, cancelada.getEstado());
        assertTrue(cancelada.getSolucion().contains("fuera de servicio"));
        assertEquals(EstadoEquipo.OPERATIVO,
                equipos.findById(equipo.getId()).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("Las transiciones invalidas y el borrado de historial estan prohibidos")
    void transicionesInvalidas() {
        Equipo equipo = equipoNuevo("E-INVALIDA");
        Tecnico tecnico = tecnicoNuevo("90000004", "Tecnico Invalida");

        // completada -> no se puede iniciar ni cancelar
        OrdenTrabajo orden = nuevaOrden(equipo, tecnico, "Orden cerrada");
        ordenService.iniciar(orden.getId());
        OrdenTrabajo completa = ordenService.completar(orden.getId(),
                "Trabajo realizado y probado", BigDecimal.ONE, BigDecimal.valueOf(50));

        assertThrows(NegocioException.class, () -> ordenService.iniciar(completa.getId()));
        assertThrows(NegocioException.class, () -> ordenService.cancelar(completa.getId(), "x"));
        assertThrows(NegocioException.class, () -> ordenService.eliminar(completa.getId()));
        assertThrows(NegocioException.class,
                () -> ordenService.agregarRepuesto(completa.getId(), repuestoNuevo("RP-BLOQ", 5).getId(), 1));

        // cancelada -> no admite ediciones
        OrdenTrabajo otra = nuevaOrden(equipo, tecnico, "Orden que se cancela");
        OrdenTrabajo cancelada = ordenService.cancelar(otra.getId(), "Motivo");
        assertThrows(NegocioException.class, () -> ordenService.iniciar(cancelada.getId()));
        assertThrows(NegocioException.class, () -> ordenService.completar(cancelada.getId(),
                "solucion", BigDecimal.ONE, BigDecimal.ONE));

        // cerrada -> no admite actualizar
        assertThrows(NegocioException.class, () -> ordenService.actualizar(
                OrdenTrabajo.builder()
                        .id(completa.getId())
                        .equipo(equipo)
                        .tipo(TipoOrden.PREVENTIVO)
                        .prioridad(Prioridad.BAJA)
                        .titulo("Intento de edicion")
                        .build()));
    }

    @Test
    @DisplayName("El listado filtra por estado, tipo y texto")
    void listadoFiltrado() {
        Equipo equipo = equipoNuevo("E-FILTRO");
        Tecnico tecnico = tecnicoNuevo("90000005", "Tecnico Filtro");
        OrdenTrabajo una = nuevaOrden(equipo, tecnico, "Filtro de prueba unico ZZZ");

        assertEquals(1, ordenService.listar("ZZZ", null, null, 0, 10).getTotalElements());
        assertEquals(1, ordenService.listar(null, EstadoOrden.ABIERTA, TipoOrden.CORRECTIVO, 0, 100)
                .getContent().stream().filter(o -> o.getId().equals(una.getId())).count());

        long canceladas = ordenService.listar(null, EstadoOrden.CANCELADA, null, 0, 100)
                .getTotalElements();
        assertEquals(0, ordenService.listar(null, EstadoOrden.CANCELADA, null, 0, 100)
                .getContent().stream().filter(o -> o.getId().equals(una.getId())).count());
        assertTrue(canceladas >= 0);

        // el tecnico asignado solo ve las suyas
        long propias = ordenService.listar(null, null, null, tecnico.getId(), 0, 100)
                .getTotalElements();
        assertTrue(propias >= 1);
        assertEquals(0, ordenService.listar(null, null, null, -99L, 0, 100).getTotalElements());
    }
}
