package com.mantenimiento.cmms.controller;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.util.Animations;
import com.mantenimiento.cmms.JavaFxApplication;
import com.mantenimiento.cmms.agente.AgenteTareas;
import com.mantenimiento.cmms.agente.EstadoAgente;
import com.mantenimiento.cmms.agente.PasoTraza;
import com.mantenimiento.cmms.agente.Trazador;
import com.mantenimiento.cmms.model.Usuario;
import com.mantenimiento.cmms.service.SesionService;
import com.mantenimiento.cmms.ui.Fx;
import com.mantenimiento.cmms.ui.Notificador;
import com.mantenimiento.cmms.ui.Refrescable;
import com.mantenimiento.cmms.ui.Tablas;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ventana principal — "centro de mando":
 * <ul>
 *   <li>barra superior con marca, <b>paleta de comandos</b> (Ctrl+K) y sesion;</li>
 *   <li>navegacion en pildoras con capsula animada (sin lateral generico);</li>
 *   <li><b>dock de estado</b>: que hace el agente ahora, su progreso y los
 *       botones de pausa/cancelar (control constante del usuario);</li>
 *   <li><b>consola plegable</b> con la bitacora del agente (transparencia);</li>
 *   <li>tema claro/oscuro y control de acceso segun el rol de la sesion.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MainController {

    private static final double ALTO_CONSOLA = 300;
    private static final Duration DURACION_CONSOLA = Duration.millis(260);

    private final ApplicationContext context;
    private final Notificador notificador;
    private final SesionService sesion;
    private final Trazador trazador;
    private final EstadoAgente estado;
    private final AgenteTareas agente;

    @FXML private StackPane raiz;
    @FXML private StackPane contenido;
    @FXML private ModalPane modalPanel;
    @FXML private ModalPane modalDialogo;
    @FXML private VBox zonaNotificaciones;

    // barra de mando
    @FXML private Label lblContexto;
    @FXML private Button btnPaleta;
    @FXML private Button btnTema;
    @FXML private Button btnSalir;
    @FXML private Label lblUsuario;
    @FXML private Label lblRol;
    @FXML private Label avatarUsuario;

    // navegacion en pildoras
    @FXML private ToggleGroup menu;
    @FXML private ToggleButton navDashboard;
    @FXML private ToggleButton navOrdenes;
    @FXML private ToggleButton navPreventivo;
    @FXML private ToggleButton navEquipos;
    @FXML private ToggleButton navCatalogos;
    @FXML private ToggleButton navRepuestos;
    @FXML private ToggleButton navTecnicos;
    @FXML private ToggleButton navReportes;
    @FXML private ToggleButton navUsuarios;
    @FXML private Region indicador;
    @FXML private Region navegacion;

    // consola / bitacora
    @FXML private VBox consola;
    @FXML private TableView<PasoTraza> tablaTrazas;
    @FXML private TableColumn<PasoTraza, String> colHora;
    @FXML private TableColumn<PasoTraza, PasoTraza> colTipo;
    @FXML private TableColumn<PasoTraza, PasoTraza> colActividad;
    @FXML private TableColumn<PasoTraza, String> colFuente;
    @FXML private TableColumn<PasoTraza, String> colCriterio;
    @FXML private TableColumn<PasoTraza, String> colDuracion;
    @FXML private ToggleGroup grupoFiltro;
    @FXML private ToggleButton filtroTodo;
    @FXML private ToggleButton filtroConsulta;
    @FXML private ToggleButton filtroCalculo;
    @FXML private ToggleButton filtroDecision;
    @FXML private ToggleButton filtroAviso;
    @FXML private ToggleButton filtroError;
    @FXML private Button btnCopiarTraza;
    @FXML private Button btnExportarTraza;
    @FXML private Button btnLimpiarTraza;
    @FXML private Button btnCerrarConsola;

    // dock de estado
    @FXML private FontIcon icoActividad;
    @FXML private Label lblActividad;
    @FXML private Label chipActividad;
    @FXML private javafx.scene.control.ProgressBar barraActividad;
    @FXML private Button btnPausa;
    @FXML private Button btnCancelarAccion;
    @FXML private Label lblPasos;
    @FXML private Button btnConsola;

    /** Vistas ya cargadas: se construyen una sola vez y se reutilizan. */
    private final Map<String, Vista> vistas = new HashMap<>();
    private boolean oscuro = false;
    private boolean indicadorListo = false;
    private boolean consolaAbierta = false;

    private FilteredList<PasoTraza> trazasFiltradas;

    // paleta de comandos (se construye perezosamente)
    private StackPane overlayPaleta;
    private TextField txtPaleta;
    private ListView<Comando> listaPaleta;
    private FilteredList<Comando> comandosVisibles;

    private record Vista(Parent nodo, Object controlador) {
    }

    /** Entrada de la paleta de comandos. */
    private record Comando(String etiqueta, String grupo, org.kordamp.ikonli.Ikon icono, Runnable accion) {
    }

    @FXML
    public void initialize() {
        // El controlador es singleton de Spring: al reloguear hay que olvidar
        // los nodos de la ventana anterior (la paleta vive en el raiz viejo).
        overlayPaleta = null;
        indicadorListo = false;
        consolaAbierta = false;

        // La capsula resaltada vive dentro de un StackPane y StackPane estira
        // a sus hijos al ancho completo: sin este limite ocuparia toda la barra
        // y todas las pildoras parecieran encendidas a la vez.
        indicador.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        indicador.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        notificador.registrar(modalPanel, modalDialogo, zonaNotificaciones);
        modalPanel.setAlignment(Pos.TOP_RIGHT);
        modalDialogo.setAlignment(Pos.CENTER);

        navDashboard.setUserData("dashboard-view");
        navOrdenes.setUserData("orden-view");
        navPreventivo.setUserData("preventivo-view");
        navEquipos.setUserData("equipo-view");
        navCatalogos.setUserData("catalogo-view");
        navRepuestos.setUserData("repuesto-view");
        navTecnicos.setUserData("tecnico-view");
        navReportes.setUserData("reportes-view");
        navUsuarios.setUserData("usuario-view");

        for (ToggleButton b : botonesMenu()) {
            Tooltip t = new Tooltip("Ir a " + b.getText());
            t.setShowDelay(Duration.millis(250));
            b.setTooltip(t);
        }

        aplicarPermisos();

        // Navegacion: cambia la vista y desliza la capsula.
        menu.selectedToggleProperty().addListener((obs, anterior, actual) -> {
            if (actual == null) {
                menu.selectToggle(anterior);
                return;
            }
            mostrar((String) actual.getUserData());
            moverIndicador(actual);
            lblContexto.setText(((ToggleButton) actual).getText());
        });

        prepararConsola();
        prepararDock();
        prepararAtajos();

        // En el initialize los anchos son 0: la capsula se coloca sola, sin
        // animacion, cuando el layout real de la barra ya tiene medidas.
        navegacion.widthProperty().addListener((obs, anterior, actual) -> {
            if (actual.doubleValue() > 0 && menu.getSelectedToggle() != null) {
                colocarCapsula(menu.getSelectedToggle());
                indicadorListo = true;
            }
        });

        menu.selectToggle(navDashboard);
        programarPrecarga();
    }

    /**
     * Oculta las secciones que el rol actual no puede usar:
     * el tecnico no ve reportes ni administracion; solo el admin ve usuarios.
     */
    private void aplicarPermisos() {
        Usuario usuario = sesion.getUsuario();
        if (usuario == null) {
            return;
        }
        boolean esAdmin = sesion.puedeAdministrarUsuarios();
        boolean gestiona = sesion.puedeGestionar();

        ocultarSiNo(navUsuarios, esAdmin);
        ocultarSiNo(navCatalogos, gestiona);
        ocultarSiNo(navTecnicos, gestiona);
        ocultarSiNo(navReportes, gestiona);
        ocultarSiNo(navPreventivo, gestiona);

        lblUsuario.setText(usuario.getNombre());
        lblRol.setText(usuario.getRol().getEtiqueta());
        avatarUsuario.setText(Tablas.iniciales(usuario.getNombre()));
        avatarUsuario.setAlignment(Pos.CENTER);
    }

    private void ocultarSiNo(ToggleButton boton, boolean visible) {
        boton.setVisible(visible);
        boton.setManaged(visible);
        if (!visible && boton.isSelected()) {
            menu.selectToggle(navDashboard);
        }
    }

    // ----------------------------------------------------------- navegacion

    /** Cambia a otra vista como si se hubiera pulsado su opcion en la barra. */
    public void irA(String vista) {
        botonesMenu().stream()
                .filter(b -> vista.equals(b.getUserData()) && b.isVisible())
                .findFirst()
                .ifPresent(menu::selectToggle);
    }

    private void mostrar(String nombre) {
        Vista vista = vistas.computeIfAbsent(nombre, this::cargar);
        contenido.getChildren().setAll(vista.nodo());
        if (vista.controlador() instanceof Refrescable r) {
            r.refrescar();
        }
        Animations.fadeInUp(vista.nodo(), Duration.millis(240)).play();
    }

    private Vista cargar(String nombre) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + nombre + ".fxml"));
        loader.setControllerFactory(context::getBean);
        loader.setClassLoader(getClass().getClassLoader());
        try {
            Parent nodo = loader.load();
            return new Vista(nodo, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + nombre, e);
        }
    }

    /** Desliza la capsula resaltada hasta la pildora seleccionada. */
    private void moverIndicador(Toggle toggle) {
        Node boton = (Node) toggle;
        if (boton.getBoundsInLocal().getWidth() <= 0 || navegacion.getWidth() <= 0
                || boton.getBoundsInParent().getWidth() <= 0) {
            return; // aun sin medidas: el listener de ancho la coloca sola
        }
        double x = boton.getBoundsInParent().getMinX();
        double ancho = boton.getBoundsInParent().getWidth();
        if (!indicadorListo) {
            indicadorListo = true;
            indicador.setPrefWidth(ancho);
            indicador.setTranslateX(x);
            return;
        }
        new Timeline(new KeyFrame(Duration.millis(260),
                new KeyValue(indicador.translateXProperty(), x, Interpolator.SPLINE(0.4, 0, 0.2, 1)),
                new KeyValue(indicador.prefWidthProperty(), ancho, Interpolator.SPLINE(0.4, 0, 0.2, 1))))
                .play();
    }

    /** Coloca la capsula exactamente sobre la pildora, sin animacion. */
    private void colocarCapsula(Toggle toggle) {
        if (toggle == null) {
            return;
        }
        Node boton = (Node) toggle;
        double ancho = boton.getBoundsInParent().getWidth();
        if (ancho <= 0) {
            return;
        }
        indicador.setPrefWidth(ancho);
        indicador.setTranslateX(boton.getBoundsInParent().getMinX());
    }

    /**
     * Precarga las demas vistas de a una por pulso, para que el primer clic en
     * cada seccion sea instantaneo en lugar de cargar el FXML en ese momento.
     */
    private void programarPrecarga() {
        List<String> pendientes = List.of("orden-view", "equipo-view", "repuesto-view",
                "preventivo-view", "tecnico-view", "catalogo-view",
                "reportes-view", "usuario-view");
        precargar(pendientes, 0);
    }

    /** {@code true} mientras el contexto de Spring siga vivo (no cerrado). */
    private boolean contextoActivo() {
        return context instanceof org.springframework.context.ConfigurableApplicationContext ac
                && ac.isActive();
    }

    private void precargar(List<String> pendientes, int i) {
        if (i >= pendientes.size() || !contextoActivo()) {
            return;
        }
        String nombre = pendientes.get(i);
        if (vistas.containsKey(nombre)) {
            precargar(pendientes, i + 1);
            return;
        }
        javafx.animation.PauseTransition pausa =
                new javafx.animation.PauseTransition(Duration.millis(150));
        pausa.setOnFinished(e -> {
            if (contextoActivo()) {
                try {
                    vistas.put(nombre, cargar(nombre));
                } catch (RuntimeException falla) {
                    // una vista que no se pudo precargar se cargara al navegar
                }
            }
            precargar(pendientes, i + 1);
        });
        pausa.play();
    }

    // ------------------------------------------------------------ bitacora

    private void prepararConsola() {
        trazasFiltradas = new FilteredList<>(trazador.espejo());
        tablaTrazas.setItems(trazasFiltradas);

        colHora.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().horaFormateada()));
        colTipo.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyObjectWrapper<>(d.getValue()));
        colTipo.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(PasoTraza item, boolean vacio) {
                super.updateItem(item, vacio);
                if (vacio || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label chip = new Label(item.tipo().getEtiqueta());
                    chip.getStyleClass().addAll("chip", item.tipo().getClase());
                    setGraphic(chip);
                    setText(null);
                }
            }
        });

        colActividad.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyObjectWrapper<>(d.getValue()));
        colActividad.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(PasoTraza item, boolean vacio) {
                super.updateItem(item, vacio);
                if (vacio || item == null) {
                    setGraphic(null);
                    setText(null);
                    setTooltip(null);
                } else {
                    Label titulo = new Label(item.titulo());
                    titulo.getStyleClass().add("traza-titulo");
                    titulo.setMaxWidth(Double.MAX_VALUE);
                    VBox caja = new VBox(titulo);
                    if (!item.detalle().isBlank()) {
                        Label detalle = new Label(item.detalle());
                        detalle.getStyleClass().add("traza-detalle");
                        detalle.setMaxWidth(Double.MAX_VALUE);
                        detalle.setWrapText(false);
                        caja.getChildren().add(detalle);
                    }
                    caja.setMaxWidth(Double.MAX_VALUE);
                    setGraphic(caja);
                    setText(null);
                    setTooltip(new Tooltip(item.detalle() + (item.criterio().isBlank()
                            ? "" : "\nCriterio: " + item.criterio())));
                }
            }
        });

        colFuente.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().fuente().isBlank() ? "—" : d.getValue().fuente()));
        colCriterio.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().criterio().isBlank() ? d.getValue().detalle() : d.getValue().criterio()));
        colDuracion.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().duracionFormateada()));

        // Filtros por tipo: "Todo" incluye consultas, calculos, decisiones, avisos y control.
        filtroTodo.setUserData(null);
        filtroConsulta.setUserData(com.mantenimiento.cmms.agente.TipoTraza.CONSULTA);
        filtroCalculo.setUserData(com.mantenimiento.cmms.agente.TipoTraza.CALCULO);
        filtroDecision.setUserData(com.mantenimiento.cmms.agente.TipoTraza.DECISION);
        filtroAviso.setUserData(com.mantenimiento.cmms.agente.TipoTraza.AVISO);
        filtroError.setUserData(com.mantenimiento.cmms.agente.TipoTraza.ERROR);
        grupoFiltro.selectedToggleProperty().addListener((obs, o, actual) -> {
            if (actual == null) {
                filtroTodo.setSelected(true);
                return;
            }
            aplicarFiltro((com.mantenimiento.cmms.agente.TipoTraza) actual.getUserData());
        });
        aplicarFiltro(null);

        // Contador vivo + auto-scroll al paso mas reciente.
        trazador.espejo().addListener((ListChangeListener<PasoTraza>) c -> {
            lblPasos.setText(trazador.espejo().size() + " pasos");
            if (consolaAbierta) {
                Platform.runLater(() -> tablaTrazas.scrollTo(trazador.espejo().size() - 1));
            }
        });
        lblPasos.setText("0 pasos");
    }

    private void aplicarFiltro(com.mantenimiento.cmms.agente.TipoTraza tipo) {
        trazasFiltradas.setPredicate(p -> tipo == null
                || p.tipo() == tipo
                || (tipo == com.mantenimiento.cmms.agente.TipoTraza.DECISION
                && (p.tipo() == com.mantenimiento.cmms.agente.TipoTraza.RESULTADO
                || p.tipo() == com.mantenimiento.cmms.agente.TipoTraza.CONTROL)));
    }

    private void alternarConsola(boolean abrir) {
        if (abrir == consolaAbierta) {
            return;
        }
        consolaAbierta = abrir;
        btnConsola.getStyleClass().remove("activo");
        if (abrir) {
            btnConsola.getStyleClass().add("activo");
            consola.setVisible(true);
            new Timeline(new KeyFrame(DURACION_CONSOLA,
                    new KeyValue(consola.prefHeightProperty(), ALTO_CONSOLA,
                            Interpolator.SPLINE(0.4, 0, 0.2, 1)))).play();
            Platform.runLater(() -> tablaTrazas.scrollTo(trazador.espejo().size() - 1));
        } else {
            Timeline cierre = new Timeline(new KeyFrame(DURACION_CONSOLA,
                    new KeyValue(consola.prefHeightProperty(), 0,
                            Interpolator.SPLINE(0.4, 0, 0.2, 1))));
            cierre.setOnFinished(e -> consola.setVisible(false));
            cierre.play();
        }
    }

    @FXML
    private void onAlternarConsola() {
        alternarConsola(!consolaAbierta);
    }

    @FXML
    private void onCerrarConsola() {
        alternarConsola(false);
    }

    @FXML
    private void onCopiarTraza() {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(trazador.copiarTodo());
        Clipboard.getSystemClipboard().setContent(contenido);
        notificador.exito("Bitacora copiada (" + trazador.total() + " pasos).");
    }

    @FXML
    private void onExportarTraza() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar bitacora del agente");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto", "*.txt"));
        selector.setInitialFileName("bitacora-agente.txt");
        File elegido = selector.showSaveDialog(raiz.getScene().getWindow());
        if (elegido == null) {
            return;
        }
        try {
            trazador.exportar(elegido);
            notificador.exito("Bitacora guardada: " + elegido.getName());
        } catch (IOException e) {
            notificador.fallar("BIT-001", "No se pudo guardar la bitacora del agente",
                    "Fallo de archivos al escribir: " + e.getMessage(),
                    "Elige otra carpeta con permisos de escritura e intentalo de nuevo.",
                    this::onExportarTraza);
        }
    }

    @FXML
    private void onLimpiarTraza() {
        trazador.limpiar();
        lblPasos.setText("0 pasos");
        notificador.info("Bitacora limpiada.");
    }

    // --------------------------------------------------------------- dock

    private void prepararDock() {
        lblActividad.textProperty().bind(estado.actividadProperty());
        barraActividad.progressProperty().bind(estado.progresoProperty());
        btnPausa.disableProperty().bind(estado.enCursoProperty().not());
        btnCancelarAccion.disableProperty().bind(estado.enCursoProperty().not());

        estado.chipProperty().addListener((obs, o, n) -> pintarChip(n == null ? "" : n));
        pintarChip("");

        estado.pausadoProperty().addListener((obs, o, pausado) -> {
            btnPausa.setText(pausado ? "Reanudar" : "Pausar");
            ((FontIcon) btnPausa.getGraphic())
                    .setIconCode(pausado ? Feather.PLAY : Feather.PAUSE);
        });
    }

    private void pintarChip(String chip) {
        chipActividad.getStyleClass().removeIf(c -> List.of("info", "neutro", "exito",
                "advertencia", "peligro").contains(c));
        boolean vacio = chip.isEmpty();
        chipActividad.setVisible(!vacio);
        chipActividad.setManaged(!vacio);
        if (vacio) {
            return;
        }
        chipActividad.setText(chip);
        chipActividad.getStyleClass().add(switch (chip) {
            case "BD" -> "info";
            case "calc" -> "neutro";
            case "decision", "ok" -> "exito";
            case "aviso" -> "advertencia";
            case "error" -> "peligro";
            default -> "info";
        });
    }

    @FXML
    private void onPausa() {
        if (estado.pausadoProperty().get()) {
            agente.reanudar();
        } else {
            agente.pausar();
        }
    }

    @FXML
    private void onCancelarAccion() {
        agente.cancelar();
        notificador.info("Cancelacion solicitada: la accion se detiene en el proximo paso.");
    }

    // -------------------------------------------------- paleta de comandos

    private void prepararAtajos() {
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.K) {
                alternarPaleta();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.B) {
                alternarConsola(!consolaAbierta);
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE && overlayPaleta != null
                    && overlayPaleta.isVisible()) {
                cerrarPaleta();
                e.consume();
            }
        });
    }

    @FXML
    private void onPaleta() {
        alternarPaleta();
    }

    private void alternarPaleta() {
        if (overlayPaleta == null) {
            construirPaleta();
        }
        if (overlayPaleta.isVisible()) {
            cerrarPaleta();
        } else {
            abrirPaleta();
        }
    }

    private void construirPaleta() {
        txtPaleta = new TextField();
        txtPaleta.setPromptText("Busca una vista o una accion…");
        txtPaleta.getStyleClass().add("paleta-input");

        listaPaleta = new ListView<>();
        listaPaleta.getStyleClass().add("paleta-lista");
        listaPaleta.setPrefHeight(330);
        listaPaleta.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Comando item, boolean vacio) {
                super.updateItem(item, vacio);
                if (vacio || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox fila = new HBox(10,
                            new FontIcon(item.icono()),
                            new Label(item.etiqueta()));
                    fila.setAlignment(Pos.CENTER_LEFT);
                    Label grupo = new Label(item.grupo());
                    grupo.getStyleClass().add("paleta-grupo");
                    HBox caja = new HBox(10, fila);
                    javafx.scene.layout.Region espacio = new javafx.scene.layout.Region();
                    HBox.setHgrow(espacio, javafx.scene.layout.Priority.ALWAYS);
                    caja.getChildren().addAll(espacio, grupo);
                    caja.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(caja);
                    setText(null);
                }
            }
        });

        Label pistas = new Label("↑↓ navegar   ·   Enter ejecutar   ·   Esc cerrar");
        pistas.getStyleClass().add("paleta-pistas");

        VBox caja = new VBox(10, txtPaleta, listaPaleta, pistas);
        caja.getStyleClass().add("paleta");
        caja.setMaxWidth(600);

        overlayPaleta = new StackPane(caja);
        overlayPaleta.getStyleClass().add("paleta-overlay");
        overlayPaleta.setViewOrder(-90);
        overlayPaleta.setVisible(false);
        StackPane.setAlignment(caja, Pos.TOP_CENTER);
        StackPane.setMargin(caja, new Insets(96, 0, 0, 0));
        raiz.getChildren().add(raiz.getChildren().size() - 1, overlayPaleta);

        overlayPaleta.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == overlayPaleta) {
                cerrarPaleta();
            }
        });

        txtPaleta.textProperty().addListener((obs, o, n) -> filtrarComandos(n));
        txtPaleta.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN) {
                listaPaleta.requestFocus();
                listaPaleta.getSelectionModel().selectNext();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                ejecutarSeleccion();
                e.consume();
            }
        });
        listaPaleta.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                ejecutarSeleccion();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                cerrarPaleta();
                e.consume();
            }
        });
        listaPaleta.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ejecutarSeleccion();
            }
        });
    }

    private void abrirPaleta() {
        List<Comando> todos = comandos();
        comandosVisibles = new FilteredList<>(javafx.collections.FXCollections.observableArrayList(todos));
        listaPaleta.setItems(comandosVisibles);
        overlayPaleta.setVisible(true);
        txtPaleta.clear();
        filtrarComandos("");
        txtPaleta.requestFocus();
    }

    private void cerrarPaleta() {
        overlayPaleta.setVisible(false);
    }

    private void filtrarComandos(String texto) {
        if (comandosVisibles == null) {
            return;
        }
        String consulta = normalizar(texto == null ? "" : texto);
        String[] tokens = consulta.split("\\s+");
        comandosVisibles.setPredicate(c -> {
            String blanco = normalizar(c.etiqueta() + " " + c.grupo());
            for (String t : tokens) {
                if (!t.isBlank() && !blanco.contains(t)) {
                    return false;
                }
            }
            return true;
        });
        if (!comandosVisibles.isEmpty()) {
            listaPaleta.getSelectionModel().selectFirst();
        }
    }

    private void ejecutarSeleccion() {
        Comando elegido = listaPaleta.getSelectionModel().getSelectedItem();
        if (elegido == null) {
            return;
        }
        cerrarPaleta();
        elegido.accion().run();
    }

    private List<Comando> comandos() {
        List<Comando> lista = new ArrayList<>();
        for (ToggleButton b : botonesMenu()) {
            if (b.isVisible()) {
                Ikon icono = ((FontIcon) b.getGraphic()).getIconCode();
                lista.add(new Comando("Ir a " + b.getText(), "Modulos", icono,
                        () -> menu.selectToggle(b)));
            }
        }
        lista.add(new Comando("Cambiar a modo " + (oscuro ? "claro" : "oscuro"),
                "Preferencias", oscuro ? Feather.SUN : Feather.MOON,
                this::onCambiarTema));
        lista.add(new Comando(
                (consolaAbierta ? "Ocultar" : "Mostrar") + " la bitacora del agente",
                "Agente", Feather.TERMINAL,
                () -> alternarConsola(!consolaAbierta)));
        lista.add(new Comando("Copiar la bitacora al portapapeles", "Agente",
                Feather.COPY, this::onCopiarTraza));
        lista.add(new Comando("Limpiar la bitacora", "Agente",
                Feather.TRASH_2, this::onLimpiarTraza));
        lista.add(new Comando("Ver atajos de teclado", "Ayuda",
                Feather.COMMAND, this::mostrarAtajos));
        lista.add(new Comando("Cerrar sesion", "Sesion",
                Feather.LOG_OUT, this::onSalir));
        return lista;
    }

    private void mostrarAtajos() {
        notificador.informar("Atajos de teclado",
                "Ctrl+K — paleta de comandos (buscar o ejecutar cualquier vista)\n"
                        + "Ctrl+B — mostrar u ocultar la bitacora del agente\n"
                        + "Esc — cerrar la paleta\n"
                        + "Enter — ejecutar la opcion seleccionada en la paleta");
    }

    private static String normalizar(String texto) {
        String minus = texto.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(minus, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onCambiarTema() {
        oscuro = !oscuro;
        Application.setUserAgentStylesheet(oscuro
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        if (oscuro) {
            raiz.getStyleClass().add("dark");
        } else {
            raiz.getStyleClass().remove("dark");
        }
        ((FontIcon) btnTema.getGraphic()).setIconCode(oscuro ? Feather.SUN : Feather.MOON);
        btnTema.setText(oscuro ? "Modo claro" : "Modo oscuro");
        Animations.fadeIn(contenido, Duration.millis(300)).play();
    }

    /** Cierra la sesion y vuelve a la pantalla de acceso. */
    @FXML
    private void onSalir() {
        Usuario usuario = sesion.getUsuario();
        String nombre = usuario == null ? "" : usuario.getNombre();
        notificador.confirmar("Cerrar sesion",
                "¿Deseas salir de la sesion de " + nombre + "?", "Cerrar sesion",
                () -> {
                    sesion.cerrar();
                    vistas.clear();
                    Stage stage = (Stage) raiz.getScene().getWindow();
                    JavaFxApplication.get().cerrarSesion(stage);
                });
    }

    private List<ToggleButton> botonesMenu() {
        return List.of(navDashboard, navOrdenes, navPreventivo, navEquipos, navCatalogos,
                navRepuestos, navTecnicos, navReportes, navUsuarios);
    }
}
