package com.mantenimiento.cmms.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mantenimiento.cmms.config.DatosEmpresa;
import com.mantenimiento.cmms.model.Equipo;
import com.mantenimiento.cmms.model.EstadoOrden;
import com.mantenimiento.cmms.model.OrdenTrabajo;
import com.mantenimiento.cmms.model.Repuesto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Reporte ejecutivo de mantenimiento en PDF: indicadores del periodo,
 * ordenes, ranking de equipos por costo y alertas de inventario.
 */
@Service
@RequiredArgsConstructor
public class ReportePdfService {

    private static final Color ACENTO = new Color(42, 120, 214);
    private static final Color TEXTO = new Color(31, 35, 40);
    private static final Color TEXTO_SUAVE = new Color(101, 109, 118);
    private static final Color BORDE = new Color(216, 222, 228);
    private static final Color FILA_ALTERNA = new Color(246, 248, 250);

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat IMPORTE =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private final OrdenService ordenService;
    private final EquipoService equipoService;
    private final RepuestoService repuestoService;
    private final DatosEmpresa empresa;

    public void generar(LocalDate desde, LocalDate hasta, File destino) throws IOException {
        List<OrdenTrabajo> ordenes = ordenService.listar("", null, null, 0, 100_000).getContent().stream()
                .filter(o -> !o.getFechaCreacion().toLocalDate().isBefore(desde)
                        && !o.getFechaCreacion().toLocalDate().isAfter(hasta))
                .toList();
        List<Equipo> equipos = equipoService.todos();
        List<Repuesto> bajos = repuestoService.alertasStock();

        try (OutputStream salida = new FileOutputStream(destino)) {
            escribir(desde, hasta, ordenes, equipos, bajos, salida);
        }
    }

    void escribir(LocalDate desde, LocalDate hasta, List<OrdenTrabajo> ordenes, List<Equipo> equipos,
                  List<Repuesto> bajos, OutputStream salida) throws IOException {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.addTitle("Reporte de mantenimiento");
            doc.addAuthor(empresa.getRazonSocial());
            doc.open();

            doc.add(titulo(desde, hasta));
            doc.add(espacio(12));
            doc.add(indicadores(ordenes, equipos));
            doc.add(espacio(12));
            doc.add(tablaOrdenes(ordenes));
            doc.add(espacio(12));
            doc.add(alertas(bajos));
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
    }

    // ------------------------------------------------------------- secciones

    private PdfPTable titulo(LocalDate desde, LocalDate hasta) {
        PdfPTable tabla = new PdfPTable(new float[]{62, 38});
        tabla.setWidthPercentage(100);

        PdfPCell izq = new PdfPCell();
        izq.setBorder(Rectangle.NO_BORDER);
        izq.addElement(new Paragraph(empresa.getNombreComercial(), fuente(20, Font.BOLD, ACENTO)));
        izq.addElement(new Paragraph("Reporte de mantenimiento", fuente(13, Font.BOLD, TEXTO)));
        izq.addElement(new Paragraph("Periodo: " + desde.format(FECHA) + " al " + hasta.format(FECHA), fSuave()));
        izq.addElement(new Paragraph("Generado: " + LocalDateTime.now().format(FECHA_HORA), fSuave()));
        tabla.addCell(izq);

        PdfPCell der = new PdfPCell(new Phrase("R.U.C. " + empresa.getRuc(), fuente(11, Font.BOLD, TEXTO)));
        der.setBorder(Rectangle.RECTANGLE);
        der.setBorderColor(ACENTO);
        der.setBorderWidth(1.2f);
        der.setHorizontalAlignment(Element.ALIGN_CENTER);
        der.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(der);
        return tabla;
    }

    private PdfPTable indicadores(List<OrdenTrabajo> ordenes, List<Equipo> equipos) {
        long activos = equipos.stream().filter(e -> e.getEstado() != com.mantenimiento.cmms.model.EstadoEquipo.BAJA)
                .count();
        long operativos = equipos.stream()
                .filter(e -> e.getEstado() == com.mantenimiento.cmms.model.EstadoEquipo.OPERATIVO).count();
        long completadas = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.COMPLETADA).count();
        long activas = ordenes.stream().filter(o -> o.getEstado().estaActiva()).count();
        long vencidas = ordenes.stream().filter(OrdenTrabajo::isVencida).count();
        BigDecimal costo = ordenes.stream().map(OrdenTrabajo::getCostoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PdfPTable tabla = new PdfPTable(new float[]{34, 33, 33});
        tabla.setWidthPercentage(100);
        celdaIndicador(tabla, "DISPONIBILIDAD", activos == 0 ? "100%"
                : String.format(Locale.US, "%.1f%%", operativos * 100.0 / activos),
                operativos + " de " + activos + " equipos operativos");
        celdaIndicador(tabla, "ORDENES", String.valueOf(ordenes.size()),
                completadas + " completadas · " + activas + " activas");
        celdaIndicador(tabla, "COSTO DEL PERIODO", "S/ " + IMPORTE.format(costo),
                vencidas + " ordenes vencidas");
        return tabla;
    }

    private PdfPTable tablaOrdenes(List<OrdenTrabajo> ordenes) {
        PdfPTable tabla = new PdfPTable(new float[]{13, 32, 22, 15, 18});
        tabla.setWidthPercentage(100);

        for (String t : new String[]{"NUMERO", "TITULO", "EQUIPO", "ESTADO", "COSTO TOTAL"}) {
            PdfPCell celda = new PdfPCell(new Phrase(t, fuente(8, Font.BOLD, Color.WHITE)));
            celda.setBackgroundColor(ACENTO);
            celda.setPadding(5);
            tabla.addCell(celda);
        }

        boolean alterna = false;
        for (OrdenTrabajo o : ordenes.stream().limit(60).toList()) {
            Color fondo = alterna ? FILA_ALTERNA : Color.WHITE;
            alterna = !alterna;
            tabla.addCell(celdaTexto(o.getNumero(), true, fondo));
            tabla.addCell(celdaTexto(o.getTitulo(), false, fondo));
            tabla.addCell(celdaTexto(o.getEquipo().getNombre(), false, fondo));
            tabla.addCell(celdaCentrado(o.getEstado().getEtiqueta(), fondo));
            tabla.addCell(celdaDerecha("S/ " + IMPORTE.format(o.getCostoTotal()), fondo));
        }
        if (ordenes.isEmpty()) {
            PdfPCell vacio = new PdfPCell(new Phrase("Sin ordenes en el periodo seleccionado.", fSuave()));
            vacio.setColspan(5);
            vacio.setHorizontalAlignment(Element.ALIGN_CENTER);
            vacio.setPadding(8);
            tabla.addCell(vacio);
        }
        return tabla;
    }

    private PdfPTable alertas(List<Repuesto> bajos) {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        PdfPCell titulo = new PdfPCell(new Paragraph("ALERTAS DE INVENTARIO", fuente(9, Font.BOLD, ACENTO)));
        titulo.setPadding(6);
        titulo.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(titulo);

        if (bajos.isEmpty()) {
            PdfPCell ok = new PdfPCell(new Phrase("Todos los repuestos estan por encima del stock minimo.", fSuave()));
            ok.setPadding(6);
            ok.setBorder(Rectangle.NO_BORDER);
            tabla.addCell(ok);
            return tabla;
        }
        for (Repuesto r : bajos.stream().limit(15).toList()) {
            String texto = r.getCodigo() + " · " + r.getNombre() + " — stock "
                    + r.getStock() + " " + r.getUnidad() + " (minimo " + r.getStockMinimo() + ")";
            PdfPCell celda = new PdfPCell(new Phrase(texto, fuente(8.5f, Font.NORMAL, TEXTO)));
            celda.setPadding(5);
            celda.setBorderColor(BORDE);
            celda.setBorderWidth(0.5f);
            tabla.addCell(celda);
        }
        return tabla;
    }

    // --------------------------------------------------------------- apoyo

    private PdfPCell celdaIndicador(PdfPTable tabla, String etiqueta, String valor, String detalle) {
        PdfPCell celda = new PdfPCell();
        celda.setPadding(10);
        celda.setBorderColor(BORDE);
        celda.setBorderWidth(0.5f);
        celda.addElement(new Paragraph(etiqueta, fEtiqueta()));
        celda.addElement(new Paragraph(valor, fuente(16, Font.BOLD, ACENTO)));
        celda.addElement(new Paragraph(detalle, fSuave()));
        tabla.addCell(celda);
        return celda;
    }

    private PdfPCell celdaTexto(String texto, boolean negrita, Color fondo) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto,
                negrita ? fuente(8.5f, Font.BOLD, TEXTO) : fuente(8.5f, Font.NORMAL, TEXTO)));
        c.setBackgroundColor(fondo);
        c.setPadding(4);
        c.setBorderColor(BORDE);
        c.setBorderWidth(0.5f);
        return c;
    }

    private PdfPCell celdaCentrado(String texto, Color fondo) {
        PdfPCell c = celdaTexto(texto, false, fondo);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private PdfPCell celdaDerecha(String texto, Color fondo) {
        PdfPCell c = celdaTexto(texto, false, fondo);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private Paragraph espacio(float puntos) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(puntos);
        return p;
    }

    private Font fSuave() {
        return fuente(8.5f, Font.NORMAL, TEXTO_SUAVE);
    }

    private Font fEtiqueta() {
        return fuente(7.5f, Font.BOLD, TEXTO_SUAVE);
    }

    private Font fuente(float tamano, int estilo, Color color) {
        try {
            BaseFont base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            return new Font(base, tamano, estilo, color);
        } catch (Exception e) {
            return new Font(Font.HELVETICA, tamano, estilo, color);
        }
    }
}
