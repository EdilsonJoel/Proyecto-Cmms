package com.mantenimiento.cmms.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utilidades para los formularios del panel lateral.
 * <p>
 * Cada campo vive en un contenedor con la clase {@code campo} que incluye un
 * {@code Label.campo-error}; los errores se muestran ahi, bajo el propio campo.
 */
public final class Formularios {

    private static final Pattern DECIMAL = Pattern.compile("\\d{0,8}([.,]\\d{0,2})?");

    private Formularios() {
    }

    /** Marca el campo en rojo, muestra el mensaje debajo y lo sacude suavemente. */
    public static void marcarError(Control campo, String mensaje) {
        campo.pseudoClassStateChanged(Styles.STATE_DANGER, true);
        Label error = etiquetaError(campo);
        if (error != null) {
            error.setText(mensaje);
            if (!error.isVisible()) {
                error.setVisible(true);
                error.setManaged(true);
                Animations.fadeInDown(error, Duration.millis(220)).play();
            }
        }
        Animations.shakeX(campo, 4).play();
    }

    public static void limpiarError(Control campo) {
        campo.pseudoClassStateChanged(Styles.STATE_DANGER, false);
        Label error = etiquetaError(campo);
        if (error != null) {
            error.setVisible(false);
            error.setManaged(false);
        }
    }

    public static void limpiarErrores(Control... campos) {
        for (Control campo : campos) {
            limpiarError(campo);
        }
    }

    /** Quita el estado de error en cuanto el usuario vuelve a escribir. */
    public static void limpiarAlEditar(TextInputControl... campos) {
        for (TextInputControl campo : campos) {
            campo.textProperty().addListener((obs, a, b) -> limpiarError(campo));
        }
    }

    /**
     * Pinta cada violacion de Bean Validation en su campo. Devuelve los mensajes que
     * no pudieron asociarse a ningun campo del formulario.
     */
    public static List<String> mostrarViolaciones(ConstraintViolationException e,
                                                  Map<String, ? extends Control> campos) {
        List<String> sinCampo = new ArrayList<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            Control campo = campos.get(ultimoNodo(v.getPropertyPath()));
            if (campo != null) {
                marcarError(campo, v.getMessage());
            } else {
                sinCampo.add(v.getMessage());
            }
        }
        return sinCampo;
    }

    /** Solo acepta digitos, hasta {@code maximo} caracteres. */
    public static void soloDigitos(TextInputControl campo, int maximo) {
        campo.setTextFormatter(new TextFormatter<>(cambio ->
                cambio.getControlNewText().matches("\\d{0," + maximo + "}") ? cambio : null));
    }

    /** Importe con hasta 8 enteros y 2 decimales (acepta punto o coma). */
    public static void soloDecimal(TextInputControl campo) {
        campo.setTextFormatter(new TextFormatter<>(cambio ->
                DECIMAL.matcher(cambio.getControlNewText()).matches() ? cambio : null));
    }

    /**
     * Construye en codigo un campo con el mismo estilo de los FXML:
     * etiqueta + control + etiqueta de error oculta.
     */
    public static VBox campo(String texto, Control control) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("campo-label");
        Label error = new Label();
        error.getStyleClass().add("campo-error");
        error.setVisible(false);
        error.setManaged(false);
        VBox contenedor = new VBox(etiqueta, control, error);
        contenedor.getStyleClass().add("campo");
        if (control instanceof TextInputControl) {
            limpiarAlEditar((TextInputControl) control);
        }
        return contenedor;
    }

    /** Convierte el texto de un campo decimal a BigDecimal (acepta coma o punto). */
    public static java.math.BigDecimal decimal(TextInputControl campo) {
        String texto = campo.getText();
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(texto.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Label etiquetaError(Control campo) {
        Parent contenedor = campo.getParent();
        while (contenedor != null && !contenedor.getStyleClass().contains("campo")) {
            contenedor = contenedor.getParent();
        }
        if (contenedor == null) {
            return null;
        }
        Node nodo = contenedor.lookup(".campo-error");
        return nodo instanceof Label l ? l : null;
    }

    private static String ultimoNodo(Path ruta) {
        String nombre = null;
        for (Path.Node nodo : ruta) {
            nombre = nodo.getName();
        }
        return nombre;
    }
}
