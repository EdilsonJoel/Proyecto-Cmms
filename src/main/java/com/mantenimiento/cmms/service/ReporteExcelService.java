package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.config.DatosEmpresa;
import com.mantenimiento.cmms.model.*;
import com.mantenimiento.cmms.repository.EquipoRepository;
import com.mantenimiento.cmms.repository.OrdenTrabajoRepository;
import com.mantenimiento.cmms.repository.PlanPreventivoRepository;
import com.mantenimiento.cmms.repository.RepuestoRepository;
import com.mantenimiento.cmms.service.LibroExcel.Estilos;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.mantenimiento.cmms.service.LibroExcel.*;

/**
 * Reporte general de mantenimiento en Excel con cinco hojas: Resumen (KPIs y costos
 * por mes), Ordenes del periodo, Costos por equipo, Inventario de repuestos y
 * Planes preventivos. Los totales usan formulas SUBTOTAL para seguir siendo
 * correctos si el usuario filtra las tablas.
 */
@Service
@RequiredArgsConstructor
public class ReporteExcelService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OrdenTrabajoRepository ordenRepository;
    private final EquipoRepository equipoRepository;
    private final RepuestoRepository repuestoRepository;
    private final PlanPreventivoRepository planRepository;
    private final DatosEmpresa empresa;

    public void generar(LocalDate desde, LocalDate hasta, File destino) throws IOException {
        List<OrdenTrabajo> ordenes = ordenRepository.findAll().stream()
                .filter(o -> o.getFechaCreacion().toLocalDate().isAfter(desde.minusDays(1))
                        && o.getFechaCreacion().toLocalDate().isBefore(hasta.plusDays(1)))
                .toList();
        List<Equipo> equipos = equipoRepository.findAll();
        List<Repuesto> repuestos = repuestoRepository.findAll();
        List<PlanPreventivo> planes = planRepository.findAllByOrderByProximaFechaAsc();

        try (OutputStream salida = new FileOutputStream(destino)) {
            escribir(desde, hasta, ordenes, equipos, repuestos, planes, salida);
        }
    }

    void escribir(LocalDate desde, LocalDate hasta, List<OrdenTrabajo> ordenes, List<Equipo> equipos,
                  List<Repuesto> repuestos, List<PlanPreventivo> planes, OutputStream salida)
            throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos e = new Estilos(libro);
            hojaResumen(libro, e, desde, hasta, ordenes, equipos, repuestos, planes);
            hojaOrdenes(libro, e, ordenes);
            hojaCostosPorEquipo(libro, e, ordenes);
            hojaRepuestos(libro, e, repuestos);
            hojaPreventivos(libro, e, planes);
            libro.getProperties().getCoreProperties().setTitle("Reporte de mantenimiento");
            libro.getProperties().getCoreProperties().setCreator(empresa.getRazonSocial());
            libro.write(salida);
        }
    }

    // ----------------------------------------------------------------- hojas

    private void hojaResumen(XSSFWorkbook libro, Estilos e, LocalDate desde, LocalDate hasta,
                             List<OrdenTrabajo> ordenes, List<Equipo> equipos,
                             List<Repuesto> repuestos, List<PlanPreventivo> planes) {
        Sheet hoja = libro.createSheet("Resumen");
        hoja.setDisplayGridlines(false);
        hoja.setColumnWidth(0, 34 * 256);
        hoja.setColumnWidth(1, 22 * 256);
        hoja.setColumnWidth(2, 22 * 256);

        long activos = equipos.stream().filter(q -> q.getEstado() != EstadoEquipo.BAJA).count();
        long operativos = equipos.stream().filter(q -> q.getEstado() == EstadoEquipo.OPERATIVO).count();
        long abiertas = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.ABIERTA).count();
        long ejecucion = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.EN_EJECUCION).count();
        long completadas = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.COMPLETADA).count();
        long canceladas = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.CANCELADA).count();
        long vencidas = ordenes.stream().filter(OrdenTrabajo::isVencida).count();
        BigDecimal costoTotal = ordenes.stream().map(OrdenTrabajo::getCostoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long bajos = repuestos.stream().filter(Repuesto::isStockBajo).count();

        int f = 0;
        texto(hoja.createRow(f++), 0, "Reporte de mantenimiento", e.titulo);
        texto(hoja.createRow(f++), 0, empresa.getRazonSocial() + "  ·  RUC " + empresa.getRuc(), e.subtitulo);
        texto(hoja.createRow(f++), 0, "Periodo: " + desde.format(FECHA) + " al " + hasta.format(FECHA)
                + "   ·   Generado: " + LocalDateTime.now().format(FECHA_HORA), e.subtitulo);
        f++;

        texto(hoja.createRow(f++), 0, "Indicadores", e.seccion);
        cabecera(hoja.createRow(f++), e, "Indicador", "Valor");
        Object[][] kpis = {
                {"Equipos activos", activos},
                {"Equipos operativos", operativos},
                {"Disponibilidad", activos == 0 ? 100.0 : operativos * 100.0 / activos},
                {"Ordenes en el periodo", (long) ordenes.size()},
                {"Ordenes abiertas", abiertas},
                {"Ordenes en ejecucion", ejecucion},
                {"Ordenes completadas", completadas},
                {"Ordenes canceladas", canceladas},
                {"Ordenes vencidas", vencidas},
                {"Costo total del periodo", costoTotal},
                {"Repuestos bajo stock minimo", bajos},
                {"Planes preventivos", (long) planes.size()},
        };
        for (Object[] k : kpis) {
            Row fila = hoja.createRow(f++);
            texto(fila, 0, (String) k[0], e.celdaNegrita);
            if (k[1] instanceof BigDecimal b) {
                numero(fila, 1, b, e.moneda);
            } else if (k[1] instanceof Double d) {
                // La disponibilidad se almacena como fraccion para el formato "0.0%".
                numero(fila, 1, BigDecimal.valueOf(d / 100), e.porcentaje);
            } else {
                entero(fila, 1, ((Number) k[1]).longValue(), e.entero);
            }
        }
        f++;

        texto(hoja.createRow(f++), 0, "Costo por mes", e.seccion);
        cabecera(hoja.createRow(f++), e, "Mes", "Ordenes", "Costo");
        int primera = f;
        LocalDate inicio = desde.withDayOfMonth(1);
        while (!inicio.isAfter(hasta)) {
            final LocalDate mes = inicio;
            List<OrdenTrabajo> delMes = ordenes.stream()
                    .filter(o -> o.getFechaCreacion().getMonthValue() == mes.getMonthValue()
                            && o.getFechaCreacion().getYear() == mes.getYear())
                    .toList();
            Row fila = hoja.createRow(f++);
            fecha(fila, 0, mes, e.mes);
            entero(fila, 1, delMes.size(), e.entero);
            numero(fila, 2, delMes.stream().map(OrdenTrabajo::getCostoTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add), e.moneda);
            inicio = inicio.plusMonths(1);
        }
        if (f > primera) {
            filaTotales(hoja.createRow(f), e, primera, f - 1, 0, new int[]{1}, new int[]{2});
        }
    }

    private void hojaOrdenes(XSSFWorkbook libro, Estilos e, List<OrdenTrabajo> ordenes) {
        Sheet hoja = libro.createSheet("Ordenes");
        String[] titulos = {"Numero", "Titulo", "Tipo", "Prioridad", "Estado", "Equipo", "Codigo",
                "Tecnico", "Creada", "Programada", "Horas", "Costo MO", "Costo rep.", "Costo total"};
        int[] anchos = {12, 42, 14, 12, 16, 30, 12, 22, 17, 14, 9, 14, 14, 15};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        for (OrdenTrabajo o : ordenes) {
            Row fila = hoja.createRow(f++);
            texto(fila, 0, o.getNumero(), e.celdaNegrita);
            texto(fila, 1, o.getTitulo(), e.celda);
            texto(fila, 2, o.getTipo().getEtiqueta(), e.celda);
            texto(fila, 3, o.getPrioridad().getEtiqueta(), e.celda);
            texto(fila, 4, o.getEstado().getEtiqueta(), estado(e, o.getEstado()));
            texto(fila, 5, o.getEquipo().getNombre(), e.celda);
            texto(fila, 6, o.getEquipo().getCodigo(), e.celda);
            texto(fila, 7, o.getTecnico() == null ? "Sin asignar" : o.getTecnico().getNombre(), e.celda);
            fechaHora(fila, 8, o.getFechaCreacion(), e.fechaHora);
            fecha(fila, 9, o.getFechaProgramada(), e.fecha);
            numero(fila, 10, o.getHorasManoObra() == null ? BigDecimal.ZERO : o.getHorasManoObra(), e.moneda);
            numero(fila, 11, o.getCostoManoObra() == null ? BigDecimal.ZERO : o.getCostoManoObra(), e.moneda);
            numero(fila, 12, o.getCostoRepuestos(), e.moneda);
            numero(fila, 13, o.getCostoTotal(), e.moneda);
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{}, new int[]{11, 12, 13});
    }

    private void hojaCostosPorEquipo(XSSFWorkbook libro, Estilos e, List<OrdenTrabajo> ordenes) {
        Sheet hoja = libro.createSheet("Costos por equipo");
        String[] titulos = {"Codigo", "Equipo", "Categoria", "Ubicacion", "Ordenes", "Completadas",
                "Horas", "Costo total"};
        int[] anchos = {12, 34, 22, 22, 11, 13, 10, 16};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        var porEquipo = ordenes.stream()
                .collect(java.util.stream.Collectors.groupingBy(o -> o.getEquipo()));
        List<Equipo> conOrdenes = porEquipo.keySet().stream()
                .sorted((a, b) -> a.getCodigo().compareTo(b.getCodigo())).toList();
        for (Equipo equipo : conOrdenes) {
            List<OrdenTrabajo> suyas = porEquipo.get(equipo);
            Row fila = hoja.createRow(f++);
            texto(fila, 0, equipo.getCodigo(), e.celda);
            texto(fila, 1, equipo.getNombre(), e.celdaNegrita);
            texto(fila, 2, equipo.getCategoria().getNombre(), e.celda);
            texto(fila, 3, equipo.getUbicacion().getNombre(), e.celda);
            entero(fila, 4, suyas.size(), e.entero);
            entero(fila, 5, suyas.stream()
                    .filter(o -> o.getEstado() == EstadoOrden.COMPLETADA).count(), e.entero);
            numero(fila, 6, suyas.stream()
                    .map(o -> o.getHorasManoObra() == null ? BigDecimal.ZERO : o.getHorasManoObra())
                    .reduce(BigDecimal.ZERO, BigDecimal::add), e.moneda);
            numero(fila, 7, suyas.stream().map(OrdenTrabajo::getCostoTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add), e.moneda);
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{4, 5}, new int[]{6, 7});
    }

    private void hojaRepuestos(XSSFWorkbook libro, Estilos e, List<Repuesto> repuestos) {
        Sheet hoja = libro.createSheet("Repuestos");
        String[] titulos = {"Codigo", "Repuesto", "Stock", "Minimo", "Unidad", "Precio costo",
                "Valor stock", "Ubicacion", "Proveedor"};
        int[] anchos = {12, 40, 10, 10, 9, 14, 15, 18, 30};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        for (Repuesto r : repuestos) {
            Row fila = hoja.createRow(f++);
            texto(fila, 0, r.getCodigo(), e.celda);
            texto(fila, 1, r.getNombre(), e.celdaNegrita);
            entero(fila, 2, r.getStock(), r.isSinStock() ? e.estadoPeligro
                    : r.isStockBajo() ? e.estadoAdvertencia : e.entero);
            entero(fila, 3, r.getStockMinimo(), e.entero);
            texto(fila, 4, r.getUnidad(), e.celda);
            numero(fila, 5, r.getPrecioCosto(), e.moneda);
            numero(fila, 6, r.getValorStock(), e.moneda);
            texto(fila, 7, r.getUbicacionAlmacen() == null ? "" : r.getUbicacionAlmacen(), e.celda);
            texto(fila, 8, r.getProveedor() == null ? "" : r.getProveedor().getNombre(), e.celda);
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{2, 3}, new int[]{5, 6});
    }

    private void hojaPreventivos(XSSFWorkbook libro, Estilos e, List<PlanPreventivo> planes) {
        Sheet hoja = libro.createSheet("Preventivos");
        String[] titulos = {"Equipo", "Codigo", "Tarea", "Frecuencia (dias)", "Proxima fecha",
                "Dias restantes", "Tecnico", "Estado"};
        int[] anchos = {34, 12, 36, 17, 15, 15, 24, 16};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        for (PlanPreventivo p : planes) {
            Row fila = hoja.createRow(f++);
            texto(fila, 0, p.getEquipo().getNombre(), e.celdaNegrita);
            texto(fila, 1, p.getEquipo().getCodigo(), e.celda);
            texto(fila, 2, p.getNombre() == null ? "" : p.getNombre(), e.celda);
            entero(fila, 3, p.getFrecuenciaDias(), e.entero);
            fecha(fila, 4, p.getProximaFecha(), e.fecha);
            entero(fila, 5, p.getDiasRestantes(), e.entero);
            texto(fila, 6, p.getTecnico() == null ? "Sin asignar" : p.getTecnico().getNombre(), e.celda);
            String estado = !p.isActivo() ? "Inactivo" : p.isVencido() ? "Vencido"
                    : p.isProximo() ? "Proximo" : "Programado";
            texto(fila, 7, estado, !p.isActivo() ? e.celda
                    : p.isVencido() ? e.estadoPeligro : p.isProximo() ? e.estadoAdvertencia : e.estadoOk);
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{3, 5}, new int[]{});
    }

    private static org.apache.poi.ss.usermodel.CellStyle estado(Estilos e, EstadoOrden estado) {
        return switch (estado) {
            case COMPLETADA -> e.estadoOk;
            case CANCELADA -> e.celda;
            case ABIERTA -> e.estadoAdvertencia;
            case EN_EJECUCION -> e.estadoAdvertencia;
        };
    }
}
