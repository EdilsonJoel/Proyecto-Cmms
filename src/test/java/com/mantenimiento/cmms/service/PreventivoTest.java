package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.IntegracionBase;
import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Planes de mantenimiento preventivo: generacion de ordenes cuando la fecha
 * vence, avance de la proxima fecha y reglas de validacion del plan.
 */
class PreventivoTest extends IntegracionBase {

    @Autowired
    private PreventivoService preventivoService;

    private PlanPreventivo planVencido(Equipo equipo, Tecnico tecnico, int frecuencia) {
        return preventivoService.guardar(PlanPreventivo.builder()
                .equipo(equipo)
                .nombre("Lubricacion y revision")
                .frecuenciaDias(frecuencia)
                .horasEstimadas(new BigDecimal("2.5"))
                .proximaFecha(LocalDate.now().minusDays(5))
                .tecnico(tecnico)
                .activo(true)
                .build());
    }

    @Test
    @DisplayName("Un plan vencido genera su orden de trabajo y avanza la proxima fecha")
    void generaOrdenDePlanVencido() {
        Equipo equipo = equipoNuevo("E-PLAN-VEN");
        Tecnico tecnico = tecnicoNuevo("90000020", "Tecnico Preventivo");
        PlanPreventivo plan = planVencido(equipo, tecnico, 30);

        long vencidos = preventivoService.contarVencidos();
        assertTrue(vencidos >= 1, "el plan recien creado debe estar vencido");

        int generadas = preventivoService.generarOrdenesPendientes();
        assertTrue(generadas >= 1, "debia generarse al menos la orden del plan");

        // la orden creada corresponde al equipo y esta abierta
        List<OrdenTrabajo> delEquipo = ordenes.findByEquipoIdOrderByFechaCreacionDesc(equipo.getId());
        assertFalse(delEquipo.isEmpty());
        OrdenTrabajo creada = delEquipo.get(0);
        assertEquals(EstadoOrden.ABIERTA, creada.getEstado());
        assertEquals(TipoOrden.PREVENTIVO, creada.getTipo());
        assertEquals(tecnico.getId(), creada.getTecnico().getId());
        assertTrue(creada.getTitulo().toLowerCase().contains("lubricacion"));

        // la fecha del plan avanza segun la frecuencia y ya no esta vencida
        PlanPreventivo actualizado = preventivoService.obtener(plan.getId());
        assertTrue(actualizado.getProximaFecha().isAfter(LocalDate.now()),
                "la proxima fecha debe quedar en el futuro");
        assertEquals(LocalDate.now(), actualizado.getUltimaOrden());

        // segunda pulsada: no hay mas planes vencidos, no duplica ordenes
        assertEquals(0, preventivoService.generarOrdenesPendientes());
    }

    @Test
    @DisplayName("Un plan con fecha futura no genera ordenes")
    void planFuturoNoGenera() {
        Equipo equipo = equipoNuevo("E-PLAN-FUT");
        Tecnico tecnico = tecnicoNuevo("90000021", "Tecnico Futuro");
        preventivoService.guardar(PlanPreventivo.builder()
                .equipo(equipo)
                .nombre("Calibracion de instrumentos")
                .frecuenciaDias(60)
                .horasEstimadas(BigDecimal.ONE)
                .proximaFecha(LocalDate.now().plusDays(45))
                .tecnico(tecnico)
                .activo(true)
                .build());

        long antes = ordenes.count();
        int generadas = preventivoService.generarOrdenesPendientes();
        assertEquals(0, generadas, "el plan futuro no debio generar ordenes");
        assertEquals(antes, ordenes.count());
    }

    @Test
    @DisplayName("Los planes inactivos no entran a la generacion")
    void planInactivoNoGenera() {
        Equipo equipo = equipoNuevo("E-PLAN-OFF");
        Tecnico tecnico = tecnicoNuevo("90000022", "Tecnico Inactivo");
        PlanPreventivo plan = preventivoService.guardar(PlanPreventivo.builder()
                .equipo(equipo)
                .nombre("Plan suspendido")
                .frecuenciaDias(30)
                .horasEstimadas(BigDecimal.ONE)
                .proximaFecha(LocalDate.now().minusDays(10))
                .tecnico(tecnico)
                .activo(true)
                .build());
        plan.setActivo(false);
        preventivoService.guardar(plan);

        long antes = ordenes.count();
        preventivoService.generarOrdenesPendientes();
        long creadas = ordenes.count() - antes;
        assertEquals(0, creadas, "los planes inactivos no generan ordenes");
    }

    @Test
    @DisplayName("El plan exige equipo, nombre y frecuencia valida")
    void validacionesDelPlan() {
        assertThrows(NegocioException.class, () -> preventivoService.guardar(
                PlanPreventivo.builder()
                        .nombre("Sin equipo")
                        .frecuenciaDias(30)
                        .proximaFecha(LocalDate.now())
                        .activo(true)
                        .build()));

        Equipo equipo = equipoNuevo("E-PLAN-VAL");
        assertThrows(NegocioException.class, () -> preventivoService.guardar(
                PlanPreventivo.builder()
                        .equipo(equipo)
                        .nombre("   ")
                        .frecuenciaDias(30)
                        .proximaFecha(LocalDate.now())
                        .activo(true)
                        .build()));

        // la frecuencia minima la impone la anotacion @Min de la entidad,
        // que Spring valida en el limite del metodo
        assertThrows(jakarta.validation.ConstraintViolationException.class,
                () -> preventivoService.guardar(
                        PlanPreventivo.builder()
                                .equipo(equipo)
                                .nombre("Frecuencia cero")
                                .frecuenciaDias(0)
                                .proximaFecha(LocalDate.now())
                                .activo(true)
                                .build()));
    }

    @Test
    @DisplayName("Eliminar un plan lo retira de la generacion")
    void eliminarPlan() {
        Equipo equipo = equipoNuevo("E-PLAN-DEL");
        Tecnico tecnico = tecnicoNuevo("90000023", "Tecnico Elimina");
        PlanPreventivo plan = planVencido(equipo, tecnico, 15);

        preventivoService.eliminar(plan.getId());

        assertFalse(preventivoService.todos().stream()
                .anyMatch(p -> p.getId().equals(plan.getId())));
        assertThrows(NegocioException.class, () -> preventivoService.eliminar(plan.getId()));
    }
}
