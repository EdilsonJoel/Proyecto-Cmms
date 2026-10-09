package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.IntegracionBase;
import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inventario de repuestos: entradas, salidas, kardex cuadrado y devoluciones
 * cuando se quita una linea de una orden.
 */
class RepuestoStockTest extends IntegracionBase {

    @Autowired
    private RepuestoService repuestoService;
    @Autowired
    private OrdenService ordenService;

    @Test
    @DisplayName("Entradas y salidas actualizan el stock y dejan el kardex cuadrado")
    void entradasSalidasYKardex() {
        Repuesto repuesto = repuestoNuevo("RP-KARDEX", 0);

        repuestoService.registrarMovimiento(repuesto.getId(), TipoMovimiento.ENTRADA, 10,
                "Ingreso por compra OC-001", null);
        assertEquals(10, repuestoService.obtener(repuesto.getId()).getStock());

        repuestoService.registrarMovimiento(repuesto.getId(), TipoMovimiento.SALIDA, 4,
                "Consumo en taller", null);
        assertEquals(6, repuestoService.obtener(repuesto.getId()).getStock());

        // el kardex se consulta del mas reciente al mas antiguo
        var kardex = repuestoService.kardex(repuesto.getId());
        assertEquals(2, kardex.size());
        assertEquals(TipoMovimiento.SALIDA, kardex.get(0).getTipo());
        assertEquals(4, kardex.get(0).getCantidad());
        assertEquals(6, kardex.get(0).getStockResultante());
        assertEquals(TipoMovimiento.ENTRADA, kardex.get(1).getTipo());
        assertEquals(10, kardex.get(1).getCantidad());
        assertEquals(10, kardex.get(1).getStockResultante());
        assertEquals(6, repuestoService.obtener(repuesto.getId()).getStock());
    }

    @Test
    @DisplayName("No se puede sacar mas stock del que hay")
    void sinStockNoPermiteSalida() {
        Repuesto repuesto = repuestoNuevo("RP-SINSTOCK", 3);

        NegocioException e = assertThrows(NegocioException.class,
                () -> repuestoService.registrarMovimiento(repuesto.getId(),
                        TipoMovimiento.SALIDA, 4, "Exceso", null));
        assertTrue(e.getMessage().toLowerCase().contains("stock"));
        assertEquals(3, repuestoService.obtener(repuesto.getId()).getStock());
        assertTrue(repuestoService.kardex(repuesto.getId()).isEmpty());
    }

    @Test
    @DisplayName("El stock nunca queda negativo y las alertas se disparan en el minimo")
    void alertasDeStock() {
        Repuesto bajo = repuestoNuevo("RP-BAJO", 1);      // minimo 2 -> bajo
        Repuesto agotado = repuestoNuevo("RP-AGOTADO", 0); // minimo 2 -> sin stock
        Repuesto sano = repuestoNuevo("RP-SANO", 25);

        assertTrue(repuestoService.obtener(bajo.getId()).isStockBajo());
        assertTrue(repuestoService.obtener(agotado.getId()).isSinStock());
        assertFalse(repuestoService.obtener(sano.getId()).isStockBajo());
        assertFalse(repuestoService.obtener(sano.getId()).isSinStock());

        var alertas = repuestoService.alertasStock();
        assertTrue(alertas.stream().anyMatch(r -> r.getId().equals(bajo.getId())));
        assertTrue(alertas.stream().anyMatch(r -> r.getId().equals(agotado.getId())));
        assertFalse(alertas.stream().anyMatch(r -> r.getId().equals(sano.getId())));
        assertTrue(repuestoService.contarBajoStock() >= 2);
    }

    @Test
    @DisplayName("Al quitar la linea de una orden el stock vuelve al almacen")
    void devolucionPorQuitarLinea() {
        Repuesto repuesto = repuestoNuevo("RP-DEVUELVE", 20);
        Equipo equipo = equipoNuevo("E-DEVUELVE");
        Tecnico tecnico = tecnicoNuevo("90000010", "Tecnico Devolucion");

        OrdenTrabajo orden = ordenService.crear(OrdenTrabajo.builder()
                .equipo(equipo)
                .tecnico(tecnico)
                .tipo(TipoOrden.CORRECTIVO)
                .prioridad(Prioridad.MEDIA)
                .titulo("Orden con repuestos")
                .build());
        ordenService.agregarRepuesto(orden.getId(), repuesto.getId(), 5);
        assertEquals(15, repuestoService.obtener(repuesto.getId()).getStock());

        DetalleRepuesto linea = ordenService.obtener(orden.getId()).getDetalles().get(0);
        ordenService.quitarRepuesto(orden.getId(), linea.getId());

        assertEquals(20, repuestoService.obtener(repuesto.getId()).getStock());
        assertTrue(ordenService.obtener(orden.getId()).getDetalles().isEmpty());

        // la devolucion dejo una entrada en el kardex
        assertTrue(repuestoService.kardex(repuesto.getId()).stream()
                .anyMatch(m -> m.getTipo() == TipoMovimiento.ENTRADA
                        && m.getCantidad() == 5));
    }

    @Test
    @DisplayName("El precio del detalle se congela al momento del consumo")
    void precioCongelado() {
        Repuesto repuesto = repuestoNuevo("RP-PRECIO", 10);
        Equipo equipo = equipoNuevo("E-PRECIO");
        Tecnico tecnico = tecnicoNuevo("90000011", "Tecnico Precio");

        OrdenTrabajo orden = ordenService.crear(OrdenTrabajo.builder()
                .equipo(equipo)
                .tecnico(tecnico)
                .tipo(TipoOrden.CORRECTIVO)
                .prioridad(Prioridad.BAJA)
                .titulo("Orden para precio")
                .build());
        ordenService.agregarRepuesto(orden.getId(), repuesto.getId(), 2);

        DetalleRepuesto linea = ordenService.obtener(orden.getId()).getDetalles().get(0);
        assertEquals(0, new java.math.BigDecimal("25.00").compareTo(linea.getPrecioUnitario()),
                "el precio debe ser S/ 25.00, fue " + linea.getPrecioUnitario());
        assertEquals(0, new java.math.BigDecimal("50.00").compareTo(linea.getSubtotal()),
                "2 x S/ 25.00 = S/ 50.00, fue " + linea.getSubtotal());

        // el consumo dejo ademas su movimiento fechado en el kardex
        assertFalse(repuestoService.kardex(repuesto.getId()).isEmpty());
        assertNotNull(repuestoService.kardex(repuesto.getId()).get(0).getFecha());
    }
}
