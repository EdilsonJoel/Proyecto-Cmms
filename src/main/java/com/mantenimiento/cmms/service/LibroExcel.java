package com.mantenimiento.cmms.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/** Estilos y utilidades comunes a los reportes Excel (tablas con cabecera, filtro y totales). */
final class LibroExcel {

    private LibroExcel() {
    }

    // ---------------------------------------------------------------- tablas

    static void prepararTabla(Sheet hoja, Estilos e, String[] titulos, int[] anchos) {
        cabecera(hoja.createRow(0), e, titulos);
        for (int i = 0; i < anchos.length; i++) {
            hoja.setColumnWidth(i, anchos[i] * 256);
        }
        hoja.createFreezePane(0, 1);
    }

    /** Autofiltro sobre los datos y fila de totales con SUBTOTAL (respeta el filtro). */
    static void cerrarTabla(Sheet hoja, Estilos e, int columnas, int siguienteFila, int[] enteros, int[] importes) {
        if (siguienteFila > 1) {
            hoja.setAutoFilter(new CellRangeAddress(0, siguienteFila - 1, 0, columnas - 1));
        }
        Row fila = hoja.createRow(siguienteFila);
        for (int c = 0; c < columnas; c++) {
            fila.createCell(c).setCellStyle(e.total);
        }
        filaTotales(fila, e, 1, siguienteFila - 1, 0, enteros, importes);
    }

    static void filaTotales(Row fila, Estilos e, int desde, int hasta, int colEtiqueta, int[] enteros, int[] importes) {
        texto(fila, colEtiqueta, "TOTAL", e.total);
        for (int c : enteros) {
            formula(fila, c, subtotal(c, desde, hasta), e.totalEntero);
        }
        for (int c : importes) {
            formula(fila, c, subtotal(c, desde, hasta), e.totalMoneda);
        }
    }

    static String subtotal(int columna, int desde, int hasta) {
        if (hasta < desde) {
            return "0";
        }
        String col = CellReference.convertNumToColString(columna);
        return "SUBTOTAL(109," + col + (desde + 1) + ":" + col + (hasta + 1) + ")";
    }

    static void cabecera(Row fila, Estilos e, String... titulos) {
        fila.setHeightInPoints(22);
        for (int i = 0; i < titulos.length; i++) {
            texto(fila, i, titulos[i], e.cabecera);
        }
    }

    // ---------------------------------------------------------------- celdas

    static void texto(Row fila, int col, String valor, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(valor == null ? "" : valor);
        c.setCellStyle(estilo);
    }

    static void numero(Row fila, int col, BigDecimal valor, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(valor == null ? 0 : valor.doubleValue());
        c.setCellStyle(estilo);
    }

    static void entero(Row fila, int col, long valor, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    static void fecha(Row fila, int col, LocalDate valor, CellStyle estilo) {
        if (valor == null) {
            texto(fila, col, "", estilo);
            return;
        }
        Cell c = fila.createCell(col);
        c.setCellValue(Date.from(valor.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        c.setCellStyle(estilo);
    }

    static void fechaHora(Row fila, int col, LocalDateTime valor, CellStyle estilo) {
        if (valor == null) {
            texto(fila, col, "", estilo);
            return;
        }
        Cell c = fila.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    static void formula(Row fila, int col, String formula, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellFormula(formula);
        c.setCellStyle(estilo);
    }

    /** Estilos compartidos del libro (POI limita la cantidad de estilos por archivo). */
    static final class Estilos {
        final XSSFCellStyle titulo, subtitulo, seccion, cabecera, celda, celdaNegrita, anulada;
        final XSSFCellStyle entero, moneda, monedaNegrita, porcentaje, fecha, mes, fechaHora;
        final XSSFCellStyle estadoOk, estadoPeligro, estadoAdvertencia, total, totalEntero, totalMoneda;

        Estilos(XSSFWorkbook libro) {
            XSSFColor acento = color(42, 120, 214);
            XSSFColor bordeColor = color(216, 222, 228);
            XSSFColor fondoTotal = color(234, 242, 252);
            short fmtMoneda = libro.createDataFormat().getFormat("\"S/\" #,##0.00");
            short fmtEntero = libro.createDataFormat().getFormat("#,##0");

            titulo = estilo(libro, fuente(libro, 18, true, color(31, 35, 40)), null, null);
            subtitulo = estilo(libro, fuente(libro, 10, false, color(101, 109, 118)), null, null);
            seccion = estilo(libro, fuente(libro, 12, true, acento), null, null);

            cabecera = estilo(libro, fuente(libro, 10, true, color(255, 255, 255)), acento, null);
            cabecera.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFFont normal = fuente(libro, 10, false, color(31, 35, 40));
            XSSFFont negrita = fuente(libro, 10, true, color(31, 35, 40));
            celda = estilo(libro, normal, null, bordeColor);
            celdaNegrita = estilo(libro, negrita, null, bordeColor);

            XSSFFont tachada = fuente(libro, 10, false, color(140, 149, 159));
            tachada.setStrikeout(true);
            anulada = estilo(libro, tachada, null, bordeColor);

            entero = estilo(libro, normal, null, bordeColor);
            entero.setDataFormat(fmtEntero);
            entero.setAlignment(HorizontalAlignment.CENTER);
            moneda = estilo(libro, normal, null, bordeColor);
            moneda.setDataFormat(fmtMoneda);
            monedaNegrita = estilo(libro, negrita, null, bordeColor);
            monedaNegrita.setDataFormat(fmtMoneda);
            porcentaje = estilo(libro, normal, null, bordeColor);
            porcentaje.setDataFormat(libro.createDataFormat().getFormat("0.0%"));
            fecha = estilo(libro, normal, null, bordeColor);
            fecha.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy"));
            mes = estilo(libro, normal, null, bordeColor);
            mes.setDataFormat(libro.createDataFormat().getFormat("mmm yyyy"));
            fechaHora = estilo(libro, normal, null, bordeColor);
            fechaHora.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

            estadoOk = estilo(libro, fuente(libro, 9, true, color(26, 127, 55)), color(218, 251, 225), bordeColor);
            estadoOk.setAlignment(HorizontalAlignment.CENTER);
            estadoPeligro = estilo(libro, fuente(libro, 9, true, color(207, 34, 46)), color(255, 235, 233), bordeColor);
            estadoPeligro.setAlignment(HorizontalAlignment.CENTER);
            estadoAdvertencia = estilo(libro, fuente(libro, 9, true, color(154, 103, 0)), color(255, 248, 197), bordeColor);
            estadoAdvertencia.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont fuenteTotal = fuente(libro, 10, true, acento);
            total = estilo(libro, fuenteTotal, fondoTotal, null);
            total.setBorderTop(BorderStyle.MEDIUM);
            total.setTopBorderColor(acento);
            totalEntero = libro.createCellStyle();
            totalEntero.cloneStyleFrom(total);
            totalEntero.setDataFormat(fmtEntero);
            totalEntero.setAlignment(HorizontalAlignment.CENTER);
            totalMoneda = libro.createCellStyle();
            totalMoneda.cloneStyleFrom(total);
            totalMoneda.setDataFormat(fmtMoneda);
        }

        private static XSSFCellStyle estilo(XSSFWorkbook libro, XSSFFont fuente, XSSFColor fondo, XSSFColor borde) {
            XSSFCellStyle s = libro.createCellStyle();
            s.setFont(fuente);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fondo != null) {
                s.setFillForegroundColor(fondo);
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (borde != null) {
                s.setBorderBottom(BorderStyle.THIN);
                s.setBottomBorderColor(borde);
            }
            return s;
        }

        private static XSSFFont fuente(XSSFWorkbook libro, int puntos, boolean negrita, XSSFColor color) {
            XSSFFont f = libro.createFont();
            f.setFontName("Calibri");
            f.setFontHeightInPoints((short) puntos);
            f.setBold(negrita);
            f.setColor(color);
            return f;
        }

        private static XSSFColor color(int r, int g, int b) {
            return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
        }
    }
}
