package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.model.*;
import com.mantenimiento.cmms.repository.EquipoRepository;
import com.mantenimiento.cmms.repository.OrdenTrabajoRepository;
import com.mantenimiento.cmms.repository.RepuestoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Indicadores del tablero: disponibilidad de equipos, ordenes, costos,
 * MTTR (tiempo medio de reparacion) y alertas de preventivo y stock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardService {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMM yy");

    private final EquipoRepository equipoRepository;
    private final OrdenTrabajoRepository ordenRepository;
    private final RepuestoRepository repuestoRepository;
    private final PreventivoService preventivoService;
    private final com.mantenimiento.cmms.agente.Trazador trazador;

    /** Indicadores principales del tablero. */
    public record Indicadores(
            long equiposActivos, long operativos, long enMantenimiento, long detenidos,
            double disponibilidad,
            long abiertas, long enEjecucion, long completadas, long canceladas,
            long vencidas, long criticas,
            BigDecimal costoMes, BigDecimal costoMesAnterior, Double variacionCosto,
            double mttrHoras, long completadas30,
            long planesVencidos, long preventivosProximos,
            long repuestosBajoStock, long repuestosSinStock,
            BigDecimal valorRepuestos) {
    }

    /** Fila de una grafica de estados, tipos o prioridades. */
    public record Serie(String etiqueta, long valor, String clase) {
    }

    /** Punto mensual de la serie de costos. */
    public record PuntoCosto(String etiqueta, BigDecimal valor) {
    }

    /** Fila del ranking de equipos mas costosos. */
    public record CostoEquipo(String equipo, String codigo, BigDecimal costo, long ordenes) {
    }

    /** Fila de la carga de trabajo por tecnico. */
    public record CargaTecnico(String tecnico, String especialidad, long activas, long completadas) {
    }

    public record Resumen(Indicadores kpis, List<Serie> porEstado, List<Serie> porTipo,
                          List<Serie> porPrioridad, List<PuntoCosto> costoMensual,
                          List<CostoEquipo> topCostos, List<CargaTecnico> cargaTecnicos,
                          List<OrdenTrabajo> ultimas, List<Repuesto> alertasStock,
                          List<PlanPreventivo> planesUrgentes) {
    }

    /** Resumen de un periodo arbitrario (para la vista de reportes). */
    public record ResumenPeriodo(long ordenes, long completadas, long preventivas, BigDecimal costo) {
    }

    public ResumenPeriodo resumenPeriodo(LocalDate desde, LocalDate hasta) {
        List<OrdenTrabajo> ordenes = ordenRepository
                .findByFechaCreacionBetween(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
        long completadas = ordenes.stream()
                .filter(o -> o.getEstado() == EstadoOrden.COMPLETADA).count();
        long preventivas = ordenes.stream()
                .filter(o -> o.getTipo() == TipoOrden.PREVENTIVO).count();
        BigDecimal costo = ordenes.stream()
                .map(OrdenTrabajo::getCostoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumenPeriodo(ordenes.size(), completadas, preventivas, costo);
    }

    public Resumen obtenerResumen() {
        List<Equipo> equipos = equipoRepository.findAll();
        List<OrdenTrabajo> ordenes = ordenRepository.findAll();

        long activos = equipos.stream().filter(e -> e.getEstado() != EstadoEquipo.BAJA).count();
        long operativos = equipos.stream().filter(e -> e.getEstado() == EstadoEquipo.OPERATIVO).count();
        long enMantenimiento = equipos.stream()
                .filter(e -> e.getEstado() == EstadoEquipo.EN_MANTENIMIENTO).count();
        long detenidos = equipos.stream().filter(e -> e.getEstado() == EstadoEquipo.DETENIDO).count();
        double disponibilidad = activos == 0 ? 100 : (operativos * 100.0) / activos;

        long abiertas = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.ABIERTA).count();
        long enEjecucion = ordenes.stream()
                .filter(o -> o.getEstado() == EstadoOrden.EN_EJECUCION).count();
        long completadas = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.COMPLETADA).count();
        long canceladas = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.CANCELADA).count();
        long vencidas = ordenes.stream().filter(OrdenTrabajo::isVencida).count();
        long criticas = ordenes.stream()
                .filter(o -> o.isVencida() && o.getPrioridad() == Prioridad.CRITICA).count();

        BigDecimal costoMes = costoEnMes(ordenes, LocalDate.now().withDayOfMonth(1), true);
        BigDecimal costoAnterior = costoEnMes(ordenes, LocalDate.now().withDayOfMonth(1).minusMonths(1), true);
        Double variacion = null;
        if (costoAnterior.compareTo(BigDecimal.ZERO) > 0) {
            variacion = costoMes.subtract(costoAnterior)
                    .multiply(BigDecimal.valueOf(100)).divide(costoAnterior, 1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        double mttr = mttrHoras(ordenes);
        long completadas30 = ordenes.stream()
                .filter(o -> o.getEstado() == EstadoOrden.COMPLETADA && o.getFechaFin() != null
                        && o.getFechaFin().isAfter(LocalDateTime.now().minusDays(30)))
                .count();

        List<Repuesto> bajos = repuestoRepository.findByActivoTrueAndStockLessThanEqualOrderByStockAsc();
        long sinStock = bajos.stream().filter(Repuesto::isSinStock).count();
        BigDecimal valorRepuestos = repuestoRepository.findAll().stream()
                .map(Repuesto::getValorStock)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long planesVencidos = preventivoService.contarVencidos();
        long preventivosProximos = preventivoService.contarProximos(7);
        List<PlanPreventivo> planesUrgentes = preventivoService.proximos().stream().limit(5).toList();

        // El tablero distingue dato guardado de valor derivado: esto es calculo.
        trazador.calculo("Indicadores del tablero calculados",
                "disponibilidad " + String.format(java.util.Locale.US, "%.1f", disponibilidad)
                        + "% (" + operativos + "/" + activos + " equipos) · MTTR "
                        + String.format(java.util.Locale.US, "%.1f", mttr) + " h con "
                        + completadas30 + " cierres (30 d) · costo del mes S/ " + costoMes,
                "derivado de equipo y orden_trabajo",
                "no es un dato guardado: se calcula con " + equipos.size() + " equipos y "
                        + ordenes.size() + " ordenes"
                        + (completadas30 >= 30 ? " · confianza alta"
                        : completadas30 >= 10 ? " · confianza media" : " · confianza baja"));

        return new Resumen(
                new Indicadores(activos, operativos, enMantenimiento, detenidos, disponibilidad,
                        abiertas, enEjecucion, completadas, canceladas, vencidas, criticas,
                        costoMes, costoAnterior, variacion, mttr, completadas30,
                        planesVencidos, preventivosProximos,
                        bajos.size(), sinStock, valorRepuestos),
                seriesEstados(abiertas, enEjecucion, completadas, canceladas),
                seriesTipos(ordenes),
                seriesPrioridades(ordenes),
                serieCostos(ordenes),
                topCostos(ordenes),
                cargaTecnicos(ordenes),
                ordenes.stream().sorted((a, b) -> b.getFechaCreacion().compareTo(a.getFechaCreacion()))
                        .limit(5).toList(),
                bajos.stream().limit(6).toList(),
                planesUrgentes);
    }

    // ------------------------------------------------------------- series

    private List<Serie> seriesEstados(long abiertas, long enEjecucion, long completadas, long canceladas) {
        List<Serie> series = new ArrayList<>();
        for (EstadoOrden estado : EstadoOrden.values()) {
            long valor = switch (estado) {
                case ABIERTA -> abiertas;
                case EN_EJECUCION -> enEjecucion;
                case COMPLETADA -> completadas;
                case CANCELADA -> canceladas;
            };
            series.add(new Serie(estado.getEtiqueta(), valor, estado.getClase()));
        }
        return series;
    }

    private List<Serie> seriesTipos(List<OrdenTrabajo> ordenes) {
        List<Serie> series = new ArrayList<>();
        for (TipoOrden tipo : TipoOrden.values()) {
            long valor = ordenes.stream().filter(o -> o.getTipo() == tipo).count();
            String clase = switch (tipo) {
                case PREVENTIVO -> "exito";
                case CORRECTIVO -> "advertencia";
                case PREDICTIVO -> "info";
            };
            series.add(new Serie(tipo.getEtiqueta(), valor, clase));
        }
        return series;
    }

    private List<Serie> seriesPrioridades(List<OrdenTrabajo> ordenes) {
        List<Serie> series = new ArrayList<>();
        for (Prioridad prioridad : Prioridad.values()) {
            long valor = ordenes.stream()
                    .filter(o -> o.getPrioridad() == prioridad && o.getEstado().estaActiva())
                    .count();
            series.add(new Serie(prioridad.getEtiqueta(), valor, prioridad.getClase()));
        }
        return series;
    }

    /** Costo total por mes de los ultimos 6 meses (mes actual incluido). */
    private List<PuntoCosto> serieCostos(List<OrdenTrabajo> ordenes) {
        List<PuntoCosto> puntos = new ArrayList<>();
        LocalDate hoy = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate inicio = hoy.withDayOfMonth(1).minusMonths(i);
            LocalDate fin = inicio.plusMonths(1);
            BigDecimal total = ordenes.stream()
                    .filter(o -> fechaDeCosto(o) != null
                            && !fechaDeCosto(o).isBefore(inicio)
                            && fechaDeCosto(o).isBefore(fin))
                    .map(OrdenTrabajo::getCostoTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            puntos.add(new PuntoCosto(inicio.format(MES), total));
        }
        return puntos;
    }

    /** Fecha en la que el costo se reconoce: fin de la orden, o creacion si sigue abierta. */
    private LocalDate fechaDeCosto(OrdenTrabajo orden) {
        if (orden.getFechaFin() != null) {
            return orden.getFechaFin().toLocalDate();
        }
        return orden.getFechaCreacion() == null ? null : orden.getFechaCreacion().toLocalDate();
    }

    private BigDecimal costoEnMes(List<OrdenTrabajo> ordenes, LocalDate inicioMes, boolean soloCerradas) {
        LocalDate finMes = inicioMes.plusMonths(1);
        return ordenes.stream()
                .filter(o -> !soloCerradas || o.getEstado().esFinal())
                .filter(o -> fechaDeCosto(o) != null
                        && !fechaDeCosto(o).isBefore(inicioMes)
                        && fechaDeCosto(o).isBefore(finMes))
                .map(OrdenTrabajo::getCostoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Tiempo medio de reparacion en horas (ordenes completadas con hora de inicio). */
    private double mttrHoras(List<OrdenTrabajo> ordenes) {
        List<OrdenTrabajo> muestras = ordenes.stream()
                .filter(o -> o.getEstado() == EstadoOrden.COMPLETADA
                        && o.getFechaInicio() != null && o.getFechaFin() != null)
                .toList();
        if (muestras.isEmpty()) {
            return 0;
        }
        double total = muestras.stream().mapToLong(OrdenTrabajo::getDuracionHoras).sum();
        return Math.round(total / muestras.size() * 10) / 10.0;
    }

    private List<CostoEquipo> topCostos(List<OrdenTrabajo> ordenes) {
        record Acumulado(Equipo equipo, BigDecimal costo, long ordenes) {
        }
        List<Acumulado> acumulados = ordenes.stream()
                .filter(o -> o.getEstado() == EstadoOrden.COMPLETADA)
                .collect(java.util.stream.Collectors.groupingBy(OrdenTrabajo::getEquipo,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(),
                                lista -> new Acumulado(lista.get(0).getEquipo(),
                                        lista.stream().map(OrdenTrabajo::getCostoTotal)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                                        lista.size()))))
                .values().stream()
                .sorted((a, b) -> b.costo().compareTo(a.costo()))
                .limit(5)
                .toList();

        return acumulados.stream()
                .map(a -> new CostoEquipo(a.equipo().getNombre(), a.equipo().getCodigo(),
                        a.costo(), a.ordenes()))
                .toList();
    }

    private List<CargaTecnico> cargaTecnicos(List<OrdenTrabajo> ordenes) {
        List<CargaTecnico> carga = new ArrayList<>();
        var porTecnico = ordenes.stream()
                .filter(o -> o.getTecnico() != null)
                .collect(java.util.stream.Collectors.groupingBy(OrdenTrabajo::getTecnico));
        for (var entrada : porTecnico.entrySet()) {
            Tecnico tecnico = entrada.getKey();
            List<OrdenTrabajo> suyas = entrada.getValue();
            long activas = suyas.stream().filter(o -> o.getEstado().estaActiva()).count();
            long completadas = suyas.stream()
                    .filter(o -> o.getEstado() == EstadoOrden.COMPLETADA).count();
            carga.add(new CargaTecnico(tecnico.getNombre(), tecnico.getEspecialidad(),
                    activas, completadas));
        }
        carga.sort((a, b) -> Long.compare(b.activas(), a.activas()));
        return carga;
    }
}
