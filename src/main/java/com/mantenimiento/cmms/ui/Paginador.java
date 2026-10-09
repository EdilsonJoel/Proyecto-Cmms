package com.mantenimiento.cmms.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.data.domain.Page;

/**
 * Barra de paginacion reutilizable: resumen "Mostrando x–y de z", selector de filas
 * por pagina y botones numerados con elipsis (1 … 4 5 6 … 20).
 * <p>
 * Se usa directamente desde FXML: {@code <Paginador fx:id="paginador"/>}.
 */
public class Paginador extends HBox {

    private static final int BOTONES_VISIBLES = 5;

    private final Label lblResumen = new Label();
    private final ComboBox<Integer> cmbTamanio =
            new ComboBox<>(FXCollections.observableArrayList(5, 10, 15, 25, 50));
    private final HBox botones = new HBox(4);

    private int pagina = 0;
    private int totalPaginas = 1;
    private Runnable onCambio = () -> { };

    public Paginador() {
        getStyleClass().add("paginador");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        lblResumen.getStyleClass().add("paginador-resumen");

        Label lblFilas = new Label("Filas");
        lblFilas.getStyleClass().add("paginador-resumen");
        cmbTamanio.setValue(10);
        cmbTamanio.getStyleClass().add("paginador-tamanio");
        cmbTamanio.valueProperty().addListener((obs, a, b) -> irAPrimera());

        botones.setAlignment(Pos.CENTER);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        getChildren().addAll(lblResumen, espacio, lblFilas, cmbTamanio, botones);
    }

    public void setOnCambio(Runnable onCambio) {
        this.onCambio = onCambio;
    }

    public int getPagina() {
        return pagina;
    }

    public int getTamanio() {
        return cmbTamanio.getValue();
    }

    /** Vuelve a la primera pagina y solicita recargar (p. ej. al cambiar el filtro). */
    public void irAPrimera() {
        irA(0);
    }

    /**
     * Refleja en la barra el resultado de una consulta paginada. Si la pagina actual
     * quedo fuera de rango (p. ej. tras eliminar el ultimo registro) retrocede y recarga.
     */
    public void actualizar(Page<?> resultado) {
        totalPaginas = Math.max(resultado.getTotalPages(), 1);
        if (pagina >= totalPaginas) {
            irA(totalPaginas - 1);
            return;
        }

        long total = resultado.getTotalElements();
        long desde = total == 0 ? 0 : (long) pagina * getTamanio() + 1;
        long hasta = (long) pagina * getTamanio() + resultado.getNumberOfElements();
        lblResumen.setText(total == 0
                ? "Sin registros"
                : "Mostrando " + desde + "–" + hasta + " de " + total);

        construirBotones();
    }

    private void irA(int nuevaPagina) {
        pagina = Math.max(0, Math.min(nuevaPagina, totalPaginas - 1));
        onCambio.run();
    }

    private void construirBotones() {
        botones.getChildren().clear();
        botones.getChildren().add(botonIcono(Feather.CHEVRONS_LEFT, 0, pagina == 0));
        botones.getChildren().add(botonIcono(Feather.CHEVRON_LEFT, pagina - 1, pagina == 0));

        int inicio = Math.max(0, Math.min(pagina - BOTONES_VISIBLES / 2, totalPaginas - BOTONES_VISIBLES));
        int fin = Math.min(totalPaginas, inicio + BOTONES_VISIBLES);

        if (inicio > 0) {
            botones.getChildren().add(botonNumero(0));
            if (inicio > 1) {
                botones.getChildren().add(elipsis());
            }
        }
        for (int i = inicio; i < fin; i++) {
            botones.getChildren().add(botonNumero(i));
        }
        if (fin < totalPaginas) {
            if (fin < totalPaginas - 1) {
                botones.getChildren().add(elipsis());
            }
            botones.getChildren().add(botonNumero(totalPaginas - 1));
        }

        boolean esUltima = pagina >= totalPaginas - 1;
        botones.getChildren().add(botonIcono(Feather.CHEVRON_RIGHT, pagina + 1, esUltima));
        botones.getChildren().add(botonIcono(Feather.CHEVRONS_RIGHT, totalPaginas - 1, esUltima));
    }

    private Button botonNumero(int indice) {
        Button b = new Button(String.valueOf(indice + 1));
        b.getStyleClass().add("pagina-btn");
        if (indice == pagina) {
            b.getStyleClass().add("activa");
        }
        b.setOnAction(e -> {
            if (indice != pagina) {
                irA(indice);
            }
        });
        return b;
    }

    private Button botonIcono(Feather icono, int destino, boolean deshabilitado) {
        Button b = new Button(null, new FontIcon(icono));
        b.getStyleClass().add("pagina-btn");
        b.setDisable(deshabilitado);
        b.setOnAction(e -> irA(destino));
        return b;
    }

    private Label elipsis() {
        Label l = new Label("…");
        l.getStyleClass().add("paginador-resumen");
        return l;
    }
}
