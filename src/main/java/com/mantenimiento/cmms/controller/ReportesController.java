package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.service.DashboardService;
import com.mantenimiento.cmms.service.ReporteExcelService;
import com.mantenimiento.cmms.service.ReportePdfService;
import com.mantenimiento.cmms.ui.Exportador;
import com.mantenimiento.cmms.ui.Notificador;
import com.mantenimiento.cmms.ui.Refrescable;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Centro de reportes: elegir un periodo y exportar el reporte ejecutivo
 * a Excel (5 hojas) o a PDF (graficos + tablas) listo para imprimir.
 */
@Component
@RequiredArgsConstructor
public class ReportesController implements Refrescable {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReporteExcelService excelService;
    private final ReportePdfService pdfService;
    private final DashboardService dashboardService;
    private final Exportador exportador;
    private final Notificador notificador;

    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Label lblPeriodo;
    @FXML private Label lblResumen;
    @FXML private VBox panelExcel;
    @FXML private VBox panelPdf;

    @FXML
    public void initialize() {
        LocalDate hoy = LocalDate.now();
        dpDesde.setValue(hoy.withDayOfMonth(1));
        dpHasta.setValue(hoy);
        dpDesde.valueProperty().addListener((o, a, b) -> actualizar());
        dpHasta.valueProperty().addListener((o, a, b) -> actualizar());
        actualizar();
    }

    @Override
    public void refrescar() {
        actualizar();
    }

    private void actualizar() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde == null || hasta == null) {
            lblPeriodo.setText("Selecciona el periodo");
            lblResumen.setText("—");
            return;
        }
        if (desde.isAfter(hasta)) {
            lblPeriodo.setText("La fecha inicial no puede ser posterior a la final");
            lblResumen.setText("—");
            return;
        }
        long dias = java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1;
        lblPeriodo.setText(desde.format(FORMATO) + "  →  " + hasta.format(FORMATO)
                + "   ·   " + dias + (dias == 1 ? " dia" : " dias"));

        DashboardService.ResumenPeriodo r = dashboardService.resumenPeriodo(desde, hasta);
        lblResumen.setText(String.format(Locale.US,
                "%d ordenes  ·  %d completadas  ·  %d preventivas  ·  costo total S/ %,.2f",
                r.ordenes(), r.completadas(), r.preventivas(), r.costo()));
    }

    // ------------------------------------------------------------ periodos

    @FXML
    private void onMesActual() {
        LocalDate hoy = LocalDate.now();
        dpDesde.setValue(hoy.withDayOfMonth(1));
        dpHasta.setValue(hoy);
    }

    @FXML
    private void onUltimos30() {
        LocalDate hoy = LocalDate.now();
        dpDesde.setValue(hoy.minusDays(29));
        dpHasta.setValue(hoy);
    }

    @FXML
    private void onEsteAnio() {
        LocalDate hoy = LocalDate.now();
        dpDesde.setValue(hoy.withDayOfYear(1));
        dpHasta.setValue(hoy);
    }

    // ------------------------------------------------------------ exportar

    @FXML
    private void onExportarExcel() {
        LocalDate[] periodo = periodoValido();
        if (periodo == null) {
            return;
        }
        String nombre = "ManteniMax-Excel-" + periodo[0] + "_a_" + periodo[1];
        exportador.abrirTemporal(nombre, Exportador.Formato.EXCEL,
                destino -> excelService.generar(periodo[0], periodo[1], destino));
        notificador.info("Generando el libro de Excel con 5 hojas…");
    }

    @FXML
    private void onExportarPdf() {
        LocalDate[] periodo = periodoValido();
        if (periodo == null) {
            return;
        }
        String nombre = "ManteniMax-Reporte-" + periodo[0] + "_a_" + periodo[1];
        exportador.abrirTemporal(nombre, Exportador.Formato.PDF,
                destino -> pdfService.generar(periodo[0], periodo[1], destino));
        notificador.info("Generando el reporte ejecutivo en PDF…");
    }

    private LocalDate[] periodoValido() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde == null || hasta == null) {
            notificador.advertencia("Selecciona las fechas del periodo.");
            return null;
        }
        if (desde.isAfter(hasta)) {
            notificador.advertencia("La fecha inicial no puede ser posterior a la final.");
            return null;
        }
        return new LocalDate[]{desde, hasta};
    }
}
