package com.mantenimiento.cmms.service;

import com.lowagie.text.Chunk;
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
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.mantenimiento.cmms.config.DatosEmpresa;
import com.mantenimiento.cmms.model.DetalleRepuesto;
import com.mantenimiento.cmms.model.OrdenTrabajo;
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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Representacion impresa (A4) de una orden de trabajo: encabezado de la empresa,
 * datos generales, repuestos utilizados, costos y firmas.
 */
@Service
@RequiredArgsConstructor
public class OrdenPdfService {

    private static final Color ACENTO = new Color(42, 120, 214);
    private static final Color TEXTO = new Color(31, 35, 40);
    private static final Color TEXTO_SUAVE = new Color(101, 109, 118);
    private static final Color BORDE = new Color(216, 222, 228);
    private static final Color FILA_ALTERNA = new Color(246, 248, 250);
    private static final Color VERDE = new Color(26, 127, 55);
    private static final Color AMBAR = new Color(154, 103, 0);
    private static final Color ROJO = new Color(207, 34, 46);

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat IMPORTE =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private final OrdenService ordenService;
    private final DatosEmpresa empresa;

    private final Font fTitulo = fuente(15, Font.BOLD, TEXTO);
    private final Font fNormal = fuente(9, Font.NORMAL, TEXTO);
    private final Font fNegrita = fuente(9, Font.BOLD, TEXTO);
    private final Font fSuave = fuente(8.5f, Font.NORMAL, TEXTO_SUAVE);
    private final Font fEtiqueta = fuente(7.5f, Font.BOLD, TEXTO_SUAVE);
    private final Font fCabecera = fuente(8, Font.BOLD, Color.WHITE);
    private final Font fNumero = fuente(16, Font.BOLD, ACENTO);
    private final Font fTotal = fuente(11, Font.BOLD, Color.WHITE);

    public void generar(Long ordenId, File destino) throws IOException {
        OrdenTrabajo orden = ordenService.obtener(ordenId);
        try (OutputStream salida = new FileOutputStream(destino)) {
            escribir(orden, salida);
        }
    }

    void escribir(OrdenTrabajo orden, OutputStream salida) {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PiePagina(orden));
            doc.addTitle("Orden de trabajo " + orden.getNumero());
            doc.addAuthor(empresa.getRazonSocial());
            doc.open();

            doc.add(cabecera(orden));
            doc.add(espacio(12));
            doc.add(datosGenerales(orden));
            doc.add(espacio(10));
            doc.add(descripcion(orden, "DESCRIPCION DEL TRABAJO", orden.getDescripcion()));
            if (orden.getSolucion() != null && !orden.getSolucion().isBlank()) {
                doc.add(espacio(8));
                doc.add(descripcion(orden, "TRABAJO REALIZADO", orden.getSolucion()));
            }
            doc.add(espacio(10));
            doc.add(tablaRepuestos(orden));
            doc.add(espacio(8));
            doc.add(totales(orden));
            doc.add(espacio(16));
            doc.add(firmas());
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
    }

    // ------------------------------------------------------------- secciones

    private PdfPTable cabecera(OrdenTrabajo orden) {
        PdfPTable tabla = new PdfPTable(new float[]{60, 40});
        tabla.setWidthPercentage(100);

        PdfPCell emisor = sinBorde();
        emisor.setVerticalAlignment(Element.ALIGN_MIDDLE);
        emisor.addElement(new Paragraph(empresa.getNombreComercial(), fuente(20, Font.BOLD, ACENTO)));
        emisor.addElement(new Paragraph(empresa.getRazonSocial(), fNegrita));
        emisor.addElement(new Paragraph(empresa.getDireccion(), fSuave));
        if (!empresa.getTelefono().isBlank() || !empresa.getEmail().isBlank()) {
            emisor.addElement(new Paragraph(
                    (empresa.getTelefono().isBlank() ? "" : "Telf. " + empresa.getTelefono())
                            + (empresa.getEmail().isBlank() ? "" : "  ·  " + empresa.getEmail()), fSuave));
        }
        tabla.addCell(emisor);

        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(100);
        caja.addCell(celdaCaja(new Phrase("ORDEN DE TRABAJO", fCabecera), ACENTO, 8, 8));
        caja.addCell(celdaCaja(new Phrase(orden.getNumero(), fNumero), Color.WHITE, 8, 10));
        caja.addCell(celdaCaja(new Phrase(orden.getEstado().getEtiqueta().toUpperCase(), fCabecera),
                colorEstado(orden), 6, 8));

        PdfPCell contenedor = new PdfPCell(caja);
        contenedor.setBorderColor(ACENTO);
        contenedor.setBorderWidth(1.2f);
        contenedor.setPadding(0);
        tabla.addCell(contenedor);
        return tabla;
    }

    private PdfPTable datosGenerales(OrdenTrabajo orden) {
        PdfPTable tabla = new PdfPTable(new float[]{17, 33, 17, 33});
        tabla.setWidthPercentage(100);

        celdaEtiqueta(tabla, "EQUIPO");
        celdaValor(tabla, orden.getEquipo().getNombre() + " (" + orden.getEquipo().getCodigo() + ")");
        celdaEtiqueta(tabla, "UBICACION");
        celdaValor(tabla, orden.getEquipo().getUbicacion().getNombre());

        celdaEtiqueta(tabla, "TIPO");
        celdaValor(tabla, orden.getTipo().getEtiqueta());
        celdaEtiqueta(tabla, "PRIORIDAD");
        celdaValor(tabla, orden.getPrioridad().getEtiqueta());

        celdaEtiqueta(tabla, "TECNICO ASIGNADO");
        celdaValor(tabla, orden.getTecnico() == null ? "Sin asignar" : orden.getTecnico().getNombre());
        celdaEtiqueta(tabla, "FECHA PROGRAMADA");
        celdaValor(tabla, orden.getFechaProgramada() == null ? "Sin programar"
                : orden.getFechaProgramada().format(FECHA));

        celdaEtiqueta(tabla, "REGISTRADA");
        celdaValor(tabla, orden.getFechaCreacion().format(FECHA_HORA));
        celdaEtiqueta(tabla, "INICIO / CIERRE");
        celdaValor(tabla, (orden.getFechaInicio() == null ? "—" : orden.getFechaInicio().format(FECHA_HORA))
                + " / " + (orden.getFechaFin() == null ? "—" : orden.getFechaFin().format(FECHA_HORA)));

        celdaEtiqueta(tabla, "TITULO");
        PdfPCell titulo = new PdfPCell(new Phrase(orden.getTitulo(), fNegrita));
        titulo.setColspan(3);
        titulo.setPadding(5);
        titulo.setBorderColor(BORDE);
        titulo.setBorderWidth(0.5f);
        tabla.addCell(titulo);
        return tabla;
    }

    private PdfPTable descripcion(OrdenTrabajo orden, String encabezado, String texto) {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell();
        celda.setPadding(6);
        celda.setBorderColor(BORDE);
        celda.setBorderWidth(0.5f);
        celda.addElement(new Paragraph(encabezado, fEtiqueta));
        celda.addElement(new Paragraph(texto == null || texto.isBlank() ? "Sin observaciones." : texto, fNormal));
        tabla.addCell(celda);
        return tabla;
    }

    private PdfPTable tablaRepuestos(OrdenTrabajo orden) {
        PdfPTable tabla = new PdfPTable(new float[]{12, 44, 12, 16, 16});
        tabla.setWidthPercentage(100);

        for (String t : new String[]{"COD.", "REPUESTO", "CANT.", "P. UNIT.", "SUBTOTAL"}) {
            PdfPCell celda = new PdfPCell(new Phrase(t, fCabecera));
            celda.setBackgroundColor(ACENTO);
            celda.setPadding(5);
            tabla.addCell(celda);
        }

        if (orden.getDetalles().isEmpty()) {
            PdfPCell vacio = new PdfPCell(new Phrase("Sin repuestos utilizados en esta orden.", fSuave));
            vacio.setColspan(5);
            vacio.setPadding(6);
            vacio.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(vacio);
            return tabla;
        }

        boolean alterna = false;
        for (DetalleRepuesto d : orden.getDetalles()) {
            Color fondo = alterna ? FILA_ALTERNA : Color.WHITE;
            alterna = !alterna;
            tabla.addCell(celda(d.getRepuesto().getCodigo(), fondo));
            tabla.addCell(celda(d.getRepuesto().getNombre(), fondo));
            tabla.addCell(celdaCentrado(d.getCantidad() + " " + d.getRepuesto().getUnidad(), fondo));
            tabla.addCell(celdaDerecha(IMPORTE.format(d.getPrecioUnitario()), fondo));
            tabla.addCell(celdaDerecha(IMPORTE.format(d.getSubtotal()), fondo));
        }
        return tabla;
    }

    private PdfPTable totales(OrdenTrabajo orden) {
        PdfPTable tabla = new PdfPTable(new float[]{60, 40});
        tabla.setWidthPercentage(100);

        celdaEtiqueta(tabla, "MANO DE OBRA");
        celdaValorDerecha(tabla, IMPORTE.format(nuloCero(orden.getCostoManoObra()))
                + (orden.getHorasManoObra() == null ? "" : "   (" + orden.getHorasManoObra() + " hrs)"));
        celdaEtiqueta(tabla, "REPUESTOS");
        celdaValorDerecha(tabla, IMPORTE.format(orden.getCostoRepuestos()));

        PdfPCell etiqueta = new PdfPCell(new Phrase("COSTO TOTAL", fTotal));
        etiqueta.setBackgroundColor(ACENTO);
        etiqueta.setPadding(6);
        tabla.addCell(etiqueta);
        PdfPCell valor = new PdfPCell(new Phrase("S/ " + IMPORTE.format(orden.getCostoTotal()), fTotal));
        valor.setBackgroundColor(ACENTO);
        valor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valor.setPadding(6);
        tabla.addCell(valor);
        return tabla;
    }

    private PdfPTable firmas() {
        PdfPTable tabla = new PdfPTable(new float[]{33, 33, 34});
        tabla.setWidthPercentage(100);
        for (String texto : new String[]{"Ejecutado por", "Supervision", "V°B° Jefe de mantenimiento"}) {
            PdfPCell celda = new PdfPCell();
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPaddingTop(34);
            celda.addElement(new Paragraph("_________________________", fSuave));
            celda.addElement(new Paragraph(texto, fEtiqueta));
            tabla.addCell(celda);
        }
        return tabla;
    }

    // --------------------------------------------------------------- apoyo

    private void celdaEtiqueta(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fEtiqueta));
        celda.setBackgroundColor(new Color(234, 242, 252));
        celda.setPadding(5);
        celda.setBorderColor(BORDE);
        celda.setBorderWidth(0.5f);
        tabla.addCell(celda);
    }

    private void celdaValor(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fNormal));
        celda.setPadding(5);
        celda.setBorderColor(BORDE);
        celda.setBorderWidth(0.5f);
        tabla.addCell(celda);
    }

    private void celdaValorDerecha(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fNegrita));
        celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celda.setPadding(5);
        celda.setBorderColor(BORDE);
        celda.setBorderWidth(0.5f);
        tabla.addCell(celda);
    }

    private PdfPCell celda(String texto, Color fondo) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto, fNormal));
        c.setBackgroundColor(fondo);
        c.setPadding(4);
        c.setBorderColor(BORDE);
        c.setBorderWidth(0.5f);
        return c;
    }

    private PdfPCell celdaCentrado(String texto, Color fondo) {
        PdfPCell c = celda(texto, fondo);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private PdfPCell celdaDerecha(String texto, Color fondo) {
        PdfPCell c = celda(texto, fondo);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private PdfPCell celdaCaja(Phrase texto, Color fondo, float padding, float tamano) {
        PdfPCell c = new PdfPCell(texto);
        c.setBackgroundColor(fondo);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(padding);
        c.setBorder(Rectangle.NO_BORDER);
        c.setLeading(tamano, 1.2f);
        return c;
    }

    private PdfPCell sinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingRight(12);
        return c;
    }

    private Paragraph espacio(float puntos) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(puntos);
        return p;
    }

    private Color colorEstado(OrdenTrabajo orden) {
        return switch (orden.getEstado()) {
            case COMPLETADA -> VERDE;
            case CANCELADA -> TEXTO_SUAVE;
            case ABIERTA -> AMBAR;
            case EN_EJECUCION -> ACENTO;
        };
    }

    private BigDecimal nuloCero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private Font fuente(float tamano, int estilo, Color color) {
        try {
            BaseFont base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            return new Font(base, tamano, estilo, color);
        } catch (Exception e) {
            return new Font(Font.HELVETICA, tamano, estilo, color);
        }
    }

    /** Pie de pagina con folio y fecha de impresion. */
    private class PiePagina extends PdfPageEventHelper {
        private final OrdenTrabajo orden;
        private final Font fPie = fuente(7.5f, Font.NORMAL, TEXTO_SUAVE);

        PiePagina(OrdenTrabajo orden) {
            this.orden = orden;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document documento) {
            mostrarPie(writer, documento, orden.getNumero() + "  ·  "
                    + empresa.getNombreComercial() + "  ·  Impreso "
                    + FECHA_HORA.format(java.time.LocalDateTime.now()));
            mostrarPie(writer, documento, "Pagina " + writer.getPageNumber());
        }

        private void mostrarPie(PdfWriter writer, Document documento, String texto) {
            com.lowagie.text.pdf.ColumnText.showTextAligned(writer.getDirectContent(),
                    Element.ALIGN_LEFT, new Phrase(texto, fPie),
                    documento.left(), documento.bottom() - 18, 0);
            com.lowagie.text.pdf.ColumnText.showTextAligned(writer.getDirectContent(),
                    Element.ALIGN_RIGHT, new Phrase(orden.getTitulo(), fPie),
                    documento.right(), documento.bottom() - 18, 0);
        }
    }
}
