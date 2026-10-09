package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.IntegracionBase;
import com.mantenimiento.cmms.model.EstadoOrden;
import com.mantenimiento.cmms.model.OrdenTrabajo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exportacion de reportes: genera el Excel de 5 hojas, el PDF ejecutivo y la
 * orden de trabajo individual sobre los datos del seeder y comprueba que los
 * archivos no quedan vacios. El seeder se dispara a mano en {@code @BeforeAll}
 * (la base de prueba arranca vacia con app.seeder.enabled=false) y asi queda
 * cubierto que la carga de datos de demostracion funciona.
 */
@TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class ReporteIntegracionTest extends IntegracionBase {

    @Autowired
    private ReporteExcelService excelService;
    @Autowired
    private ReportePdfService pdfService;
    @Autowired
    private OrdenPdfService ordenPdfService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private com.mantenimiento.cmms.seeder.DatabaseSeeder seeder;

    @BeforeAll
    void sembrarDatos() {
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "habilitado", true);
        seeder.run();
    }

    private Path carpeta() throws IOException {
        Path dir = Files.createTempDirectory("cmms-reportes");
        dir.toFile().deleteOnExit();
        return dir;
    }

    @Test
    @DisplayName("El seeder cargo datos reales para los reportes")
    void hayDatosParaReportar() {
        assertTrue(ordenes.count() >= 100, "el seeder debe cargar ~150 ordenes");
        assertTrue(equipos.count() >= 30, "el seeder debe cargar ~40 equipos");
        assertTrue(repuestos.count() >= 40, "el seeder debe cargar ~55 repuestos");
        assertTrue(categorias.count() >= 3);
        assertTrue(tecnicos.count() >= 3);
    }

    @Test
    @DisplayName("El libro de Excel genera las 5 hojas con contenido")
    void generaExcel() throws IOException {
        File xlsx = carpeta().resolve("reporte-ejecutivo.xlsx").toFile();

        excelService.generar(LocalDate.now().minusDays(250), LocalDate.now(), xlsx);

        assertTrue(xlsx.exists());
        assertTrue(xlsx.length() > 5_000,
                "el archivo debe traer las 5 hojas, pesaba " + xlsx.length());
        assertTrue(xlsx.getName().endsWith(".xlsx"));
    }

    @Test
    @DisplayName("El reporte ejecutivo en PDF genera un documento A4 con graficos")
    void generaPdf() throws IOException {
        File pdf = carpeta().resolve("reporte-ejecutivo.pdf").toFile();

        pdfService.generar(LocalDate.now().minusDays(250), LocalDate.now(), pdf);

        assertTrue(pdf.exists());
        assertTrue(pdf.length() > 5_000,
                "el PDF debe traer portada, KPIs y graficos, pesaba " + pdf.length());
        byte[] cabecera = Files.readAllBytes(pdf.toPath());
        assertEquals('%', cabecera[0]);
        assertEquals('P', cabecera[1]);
        assertEquals('D', cabecera[2]);
        assertEquals('F', cabecera[3]);
    }

    @Test
    @DisplayName("La orden de trabajo individual se exporta con sus detalles")
    void generaOrdenPdf() throws IOException {
        OrdenTrabajo completada = ordenes.findAll().stream()
                .filter(o -> o.getEstado() == EstadoOrden.COMPLETADA)
                .findFirst()
                .orElseThrow(() -> new AssertionError("el seeder debe tener ordenes completadas"));

        File pdf = carpeta().resolve(completada.getNumero() + ".pdf").toFile();
        ordenPdfService.generar(completada.getId(), pdf);

        assertTrue(pdf.exists());
        assertTrue(pdf.length() > 2_000, "la OT debe llevar texto y firmas");
    }

    @Test
    @DisplayName("El resumen por periodo cuadra con las ordenes del seeder")
    void resumenDelPeriodo() {
        DashboardService.ResumenPeriodo r = dashboardService.resumenPeriodo(
                LocalDate.now().minusDays(250), LocalDate.now());

        assertEquals(ordenes.count(), r.ordenes(), "8 meses cubren todo el historial");
        assertTrue(r.completadas() > 0);
        assertTrue(r.preventivas() > 0);
        assertTrue(r.costo().signum() > 0);
    }
}
