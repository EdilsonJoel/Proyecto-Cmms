package com.mantenimiento.cmms.ui;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import com.mantenimiento.cmms.agente.Trazador;
import com.mantenimiento.cmms.agente.TipoTraza;
import com.mantenimiento.cmms.exception.Diagnostico;
import com.mantenimiento.cmms.exception.NegocioException;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import lombok.RequiredArgsConstructor;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
/**
 * Punto unico para la retroalimentacion visual de la aplicacion: notificaciones
 * flotantes, dialogo de confirmacion, panel lateral para formularios y
 * dialogos con contenido personalizado (historial, kardex, detalle de orden).
 * El {@code MainController} registra aqui las capas de la ventana principal.
 */
@Component
@RequiredArgsConstructor
public class Notificador {

    private static final Duration DURACION_TOAST = Duration.seconds(3.5);

    private final Trazador trazador;

    private ModalPane modalPanel;
    private ModalPane modalDialogo;
    private VBox zonaToasts;

    /** Hay un dialogo centrado abierto: los errores entran como toast para no destruirlo. */
    private boolean dialogoAbierto;

    public void registrar(ModalPane modalPanel, ModalPane modalDialogo, VBox zonaToasts) {
        this.modalPanel = modalPanel;
        this.modalDialogo = modalDialogo;
        this.zonaToasts = zonaToasts;
    }

    // ---------------------------------------------------------------- toasts

    public void exito(String mensaje) {
        toast(mensaje, Feather.CHECK_CIRCLE, Styles.SUCCESS);
    }

    public void advertencia(String mensaje) {
        toast(mensaje, Feather.ALERT_TRIANGLE, Styles.WARNING);
    }

    public void error(String mensaje) {
        toast(mensaje, Feather.X_CIRCLE, Styles.DANGER);
    }

    public void info(String mensaje) {
        toast(mensaje, Feather.INFO, Styles.ACCENT);
    }

    private void toast(String mensaje, Ikon icono, String estilo) {
        Notification n = new Notification(mensaje, new FontIcon(icono));
        n.getStyleClass().addAll(estilo, Styles.ELEVATED_2, "toast");
        n.setPrefWidth(360);
        n.setMaxWidth(360);
        n.setOnClose(e -> cerrarToast(n));

        zonaToasts.getChildren().add(n);
        Animations.fadeInUp(n, Duration.millis(280)).play();

        PauseTransition espera = new PauseTransition(DURACION_TOAST);
        espera.setOnFinished(e -> cerrarToast(n));
        espera.play();
    }

    private void cerrarToast(Notification n) {
        if (!zonaToasts.getChildren().contains(n) || n.getProperties().containsKey("cerrando")) {
            return;
        }
        n.getProperties().put("cerrando", true);
        Timeline salida = Animations.fadeOutRight(n, Duration.millis(250));
        salida.setOnFinished(e -> zonaToasts.getChildren().remove(n));
        salida.play();
    }

    // ---------------------------------------------------- dialogo confirmacion

    /** Muestra un dialogo modal centrado; ejecuta {@code alConfirmar} solo si se acepta. */
    public void confirmar(String titulo, String mensaje, String textoAccion, Runnable alConfirmar) {
        dialogo(Feather.TRASH_2, "peligro", titulo, mensaje,
                "Cancelar", null, textoAccion, Styles.DANGER, alConfirmar);
    }

    /** Dialogo informativo con una sola accion ("Entendido"). */
    public void informar(String titulo, String mensaje) {
        dialogo(Feather.INFO, "info", titulo, mensaje, null, null, "Entendido", Styles.ACCENT, () -> { });
    }

    /**
     * Dialogo con contenido personalizado (campos dentro). El boton principal ejecuta
     * {@code accion}; la accion es la que cierra el dialogo con {@link #cerrarDialogo()}
     * cuando todo va bien, de modo que un error de validacion lo deja abierto.
     */
    public void dialogoContenido(Ikon icono, String claseIcono, String titulo, Node contenido,
                                 String textoAccion, String estilo, Runnable accion) {
        FontIcon iconoGrafico = new FontIcon(icono);
        StackPane circulo = new StackPane(iconoGrafico);
        circulo.getStyleClass().addAll("dialogo-icono", claseIcono);

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("dialogo-titulo");

        Button aceptar = new Button(textoAccion);
        aceptar.getStyleClass().add(estilo);
        aceptar.setOnAction(e -> accion.run());

        Button cancelar = new Button("Volver");
        cancelar.getStyleClass().add(Styles.BUTTON_OUTLINED);
        cancelar.setCancelButton(true);
        cancelar.setOnAction(e -> cerrarDialogo());

        HBox acciones = new HBox(10, cancelar, aceptar);
        Region espacio = new Region();
        HBox.setHgrow(espacio, javafx.scene.layout.Priority.ALWAYS);
        acciones.getChildren().add(0, espacio);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        acciones.getStyleClass().add("dialogo-acciones");

        VBox dialogo = new VBox(14, circulo, lblTitulo, contenido, acciones);
        dialogo.getStyleClass().add("dialogo");
        dialogo.setMaxSize(520, Region.USE_PREF_SIZE);

        modalDialogo.usePredefinedTransitionFactories(null);
        modalDialogo.show(dialogo);
        dialogoAbierto = true;
        aceptar.requestFocus();
    }

    private void dialogo(Ikon iconoDialogo, String claseIcono, String titulo, String mensaje,
                         String textoSecundario, Runnable secundaria,
                         String textoPrincipal, String estiloPrincipal, Runnable principal) {
        FontIcon icono = new FontIcon(iconoDialogo);
        StackPane circulo = new StackPane(icono);
        circulo.getStyleClass().addAll("dialogo-icono", claseIcono);

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("dialogo-titulo");
        Label lblMensaje = new Label(mensaje);
        lblMensaje.getStyleClass().add("dialogo-mensaje");
        lblMensaje.setWrapText(true);

        Button aceptar = new Button(textoPrincipal);
        aceptar.getStyleClass().add(estiloPrincipal);
        aceptar.setOnAction(e -> {
            dialogoAbierto = false;
            modalDialogo.hide();
            principal.run();
        });

        HBox acciones = new HBox(10);
        if (textoSecundario != null) {
            Button cancelar = new Button(textoSecundario);
            cancelar.getStyleClass().add(Styles.BUTTON_OUTLINED);
            cancelar.setCancelButton(true);
            cancelar.setOnAction(e -> {
                dialogoAbierto = false;
                modalDialogo.hide();
                if (secundaria != null) {
                    secundaria.run();
                }
            });
            acciones.getChildren().add(cancelar);
        }
        acciones.getChildren().add(aceptar);
        Region espacio = new Region();
        HBox.setHgrow(espacio, javafx.scene.layout.Priority.ALWAYS);
        acciones.getChildren().add(0, espacio);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        acciones.getStyleClass().add("dialogo-acciones");

        VBox dialogo = new VBox(14, circulo, lblTitulo, lblMensaje, acciones);
        dialogo.getStyleClass().add("dialogo");
        dialogo.setMaxSize(440, Region.USE_PREF_SIZE);

        modalDialogo.usePredefinedTransitionFactories(null);
        modalDialogo.show(dialogo);
        aceptar.requestFocus();
    }

    // ------------------------------------- dialogo con contenido personalizado

    /**
     * Muestra cualquier nodo centrado en una capa modal (historial de un equipo,
     * kardex, formulario de movimiento...). Se cierra con {@link #cerrarDialogo()}
     * o con el boton "Cerrar" que el contenido puede invocar.
     */
    public void mostrarContenido(Node contenido) {
        modalDialogo.usePredefinedTransitionFactories(null);
        modalDialogo.show(contenido);
        dialogoAbierto = true;
    }

    public void cerrarDialogo() {
        dialogoAbierto = false;
        modalDialogo.hide();
    }

    /** Boton estandar para cerrar los dialogos de contenido personalizado. */
    public Button botonCerrar(Runnable alCerrar) {
        Button cerrar = new Button("Cerrar", new FontIcon(Feather.X));
        cerrar.getStyleClass().add(Styles.BUTTON_OUTLINED);
        cerrar.setOnAction(e -> {
            if (alCerrar != null) {
                alCerrar.run();
            }
            cerrarDialogo();
        });
        return cerrar;
    }

    // ------------------------------------------- errores estructurados

    /**
     * Muestra un fallo con su estructura de recuperacion: <b>que paso</b>,
     * <b>por que ocurrio</b> y <b>que hacer ahora</b>, con codigo de
     * diagnostico, boton para copiar el detalle y reintento cuando la accion
     * se puede repetir. Si ya hay un dialogo abierto (formulario en curso) el
     * error entra como toast compacto para no perder lo que el usuario estaba
     * escribiendo.
     *
     * @param reintento accion a repetir al pulsar "Reintentar"; null si no aplica
     */
    public void fallar(NegocioException e, Runnable reintento) {
        fallar(Diagnostico.de(e), reintento);
    }

    /** Variante explicita para excepciones que no son de negocio (I/O, fallo interno). */
    public void fallar(String codigo, String que, String porque, String accion, Runnable reintento) {
        fallar(new Diagnostico.Estructura(codigo, que, porque, accion), reintento);
    }

    private void fallar(Diagnostico.Estructura f, Runnable reintento) {
        trazador.registrar(TipoTraza.ERROR, f.quePaso(),
                "Codigo " + f.codigo() + ". " + f.porque(),
                "reglas de negocio / interfaz", f.accion(), -1);

        if (dialogoAbierto) {
            error(f.codigo() + " · " + f.quePaso() + " → " + f.accion());
            return;
        }

        VBox contenido = new VBox(10,
                bloqueFalla("QUE PASO", f.quePaso(), "error-que"),
                bloqueFalla("POR QUE OCURRIO", f.porque(), "error-porque"),
                bloqueFalla("QUE PUEDES HACER", f.accion(), "error-accion"),
                pieDiagnostico(f));
        contenido.getStyleClass().add("error-estructura");

        dialogoContenido(Feather.X_CIRCLE, "peligro", "La accion no se pudo completar",
                contenido,
                reintento != null ? "Reintentar" : "Entendido",
                Styles.DANGER,
                () -> {
                    cerrarDialogo();
                    if (reintento != null) {
                        reintento.run();
                    }
                });
    }

    private VBox bloqueFalla(String rotulo, String texto, String clase) {
        Label lblRotulo = new Label(rotulo);
        lblRotulo.getStyleClass().add("error-rotulo");
        Label lblTexto = new Label(texto);
        lblTexto.setWrapText(true);
        lblTexto.getStyleClass().add("error-texto");
        VBox bloque = new VBox(3, lblRotulo, lblTexto);
        bloque.getStyleClass().addAll("error-bloque", clase);
        return bloque;
    }

    private HBox pieDiagnostico(Diagnostico.Estructura f) {
        Label chip = new Label(f.codigo());
        chip.getStyleClass().addAll("chip", "peligro");

        Button copiar = new Button("Copiar diagnostico", new FontIcon(Feather.COPY));
        copiar.getStyleClass().add(Styles.BUTTON_OUTLINED);
        copiar.setOnAction(e -> {
            ClipboardContent contenido = new ClipboardContent();
            contenido.putString("[" + f.codigo() + "] " + f.quePaso()
                    + "\nPor que: " + f.porque()
                    + "\nAccion: " + f.accion());
            Clipboard.getSystemClipboard().setContent(contenido);
            exito("Diagnostico copiado al portapapeles.");
        });

        HBox pie = new HBox(8, copiar, chip);
        pie.setAlignment(Pos.CENTER_LEFT);
        pie.getStyleClass().add("error-pie");
        return pie;
    }

    // ------------------------------------------------------------ panel lateral

    /** Desliza un formulario desde la derecha. */
    public void abrirPanel(Node contenido) {
        // Se renuevan las transiciones: el ModalPane las cachea para el primer nodo mostrado.
        modalPanel.usePredefinedTransitionFactories(Side.RIGHT,
                Duration.millis(320), Duration.millis(220));
        modalPanel.show(contenido);
    }

    public void cerrarPanel() {
        modalPanel.hide();
    }
}
