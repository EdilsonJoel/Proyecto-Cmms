package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.*;
import com.mantenimiento.cmms.service.CatalogoService;
import com.mantenimiento.cmms.service.EquipoService;
import com.mantenimiento.cmms.service.OrdenService;
import com.mantenimiento.cmms.service.SesionService;
import com.mantenimiento.cmms.ui.*;
import jakarta.validation.ConstraintViolationException;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** CRUD de equipos (activos) con estado operativo e historial de mantenimiento. */
@Component
@RequiredArgsConstructor
public class EquipoController implements Refrescable {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EquipoService equipoService;
    private final CatalogoService catalogoService;
    private final OrdenService ordenService;
    private final SesionService sesion;
    private final Notificador notificador;

    @FXML private Button btnNueva;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Button btnHistorial;

    @FXML private TextField txtBuscar;
    @FXML private ComboBox<EstadoEquipo> cmbFiltroEstado;

    @FXML private TableView<Equipo> tabla;
    @FXML private TableColumn<Equipo, String> colCodigo;
    @FXML private TableColumn<Equipo, Equipo> colNombre;
    @FXML private TableColumn<Equipo, String> colCategoria;
    @FXML private TableColumn<Equipo, String> colUbicacion;
    @FXML private TableColumn<Equipo, Equipo> colEstado;
    @FXML private TableColumn<Equipo, Number> colOrdenes;
    @FXML private Paginador paginador;

    @FXML private VBox panelFormulario;
    @FXML private StackPane fotoCabecera;
    @FXML private Label lblFotoCategoria;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtCodigo;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<CategoriaEquipo> cmbCategoria;
    @FXML private ComboBox<Ubicacion> cmbUbicacion;
    @FXML private TextField txtFabricante;
    @FXML private TextField txtModelo;
    @FXML private TextField txtSerie;
    @FXML private DatePicker dpInstalacion;
    @FXML private ComboBox<EstadoEquipo> cmbEstado;
    @FXML private CheckBox chkCritico;
    @FXML private TextField txtCosto;
    @FXML private TextArea taObservaciones;

    private Long idEditando;
    private Map<Long, Long> conteoOrdenes = Map.of();

    @FXML
    public void initialize() {
        Tablas.configurar(tabla, Feather.BOX, "Sin equipos registrados",
                "Registra los activos que recibe mantenimiento.", this::onDobleClic);

        colCodigo.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getCodigo()));
        colNombre.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colNombre.setCellFactory(Tablas.celdaMiniatura(this::rutaFoto, Equipo::getNombre));
        colNombre.setComparator(Comparator.comparing(Equipo::getNombre, String.CASE_INSENSITIVE_ORDER));
        colCategoria.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getCategoria().getNombre()));
        colUbicacion.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getUbicacion().getNombre()));
        colEstado.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colEstado.setCellFactory(Tablas.celdaPastilla(
                e -> e.getEstado().getEtiqueta() + (e.isCritico() ? " · critico" : ""),
                e -> e.getEstado().getClase()));
        colOrdenes.setCellValueFactory(d -> new SimpleObjectProperty<>(
                conteoOrdenes.getOrDefault(d.getValue().getId(), 0L)));

        Combos.formato(cmbFiltroEstado, e -> e == null ? "Todos los estados" : e.getEtiqueta());
        cmbFiltroEstado.getItems().add(null);
        cmbFiltroEstado.getItems().addAll(EstadoEquipo.values());
        cmbFiltroEstado.valueProperty().addListener((o, a, b) -> paginador.irAPrimera());
        txtBuscar.textProperty().addListener((o, a, b) -> paginador.irAPrimera());
        paginador.setOnCambio(this::cargar);

        Combos.categorias(cmbCategoria);
        Combos.ubicaciones(cmbUbicacion);
        Combos.formato(cmbEstado, e -> e == null ? "Operativo" : e.getEtiqueta());
        cmbEstado.getItems().addAll(EstadoEquipo.values());
        Formularios.soloDecimal(txtCosto);
        Formularios.limpiarAlEditar(txtCodigo, txtNombre, txtFabricante, txtModelo, txtSerie, txtCosto);

        // la foto del drawer acompana la categoria elegida (alta y edicion)
        cmbCategoria.valueProperty().addListener((obs, a, b) -> pintarFotoCabecera(b));
        pintarFotoCabecera(null);

        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, a, b) -> actualizarBotones());
    }

    @Override
    public void refrescar() {
        cargarCombos();
        cargar();
    }

    private void cargar() {
        var pagina = equipoService.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        conteoOrdenes = equipoService.conteoOrdenesPorEquipo();
        List<Equipo> datos = pagina.getContent().stream()
                .filter(e -> cmbFiltroEstado.getValue() == null
                        || e.getEstado() == cmbFiltroEstado.getValue())
                .toList();
        tabla.setItems(FXCollections.observableArrayList(datos));
        paginador.actualizar(pagina);
        actualizarBotones();
    }

    private void cargarCombos() {
        Combos.opciones(cmbCategoria, catalogoService.categorias());
        Combos.opciones(cmbUbicacion, catalogoService.ubicaciones());
    }

    private void actualizarBotones() {
        boolean hay = tabla.getSelectionModel().getSelectedItem() != null;
        boolean gestiona = sesion.puedeGestionar();
        btnNueva.setDisable(!gestiona);
        btnEditar.setDisable(!hay || !gestiona);
        btnEliminar.setDisable(!hay || !gestiona);
        btnHistorial.setDisable(!hay);
    }

    private void onDobleClic(Equipo equipo) {
        if (sesion.puedeGestionar()) {
            onEditar();
        } else {
            onHistorial();
        }
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNueva() {
        cargarCombos();
        idEditando = null;
        limpiarFormulario();
        lblTituloFormulario.setText("Nuevo equipo");
        lblSubtituloFormulario.setText("Registra un activo de la planta");
        cmbEstado.setValue(EstadoEquipo.OPERATIVO);
        notificador.abrirPanel(panelFormulario);
        javafx.application.Platform.runLater(txtCodigo::requestFocus);
    }

    @FXML
    private void onEditar() {
        Equipo equipo = tabla.getSelectionModel().getSelectedItem();
        if (equipo == null) {
            notificador.advertencia("Selecciona un equipo de la tabla.");
            return;
        }
        cargarCombos();
        idEditando = equipo.getId();
        lblTituloFormulario.setText("Editar " + equipo.getCodigo());
        lblSubtituloFormulario.setText(equipo.getNombre());
        txtCodigo.setText(equipo.getCodigo());
        txtNombre.setText(equipo.getNombre());
        cmbCategoria.setValue(equipo.getCategoria());
        cmbUbicacion.setValue(equipo.getUbicacion());
        txtFabricante.setText(equipo.getFabricante());
        txtModelo.setText(equipo.getModelo());
        txtSerie.setText(equipo.getNumeroSerie());
        dpInstalacion.setValue(equipo.getFechaInstalacion());
        cmbEstado.setValue(equipo.getEstado());
        chkCritico.setSelected(equipo.isCritico());
        txtCosto.setText(equipo.getCostoAdquisicion() == null
                ? "" : equipo.getCostoAdquisicion().toPlainString());
        taObservaciones.setText(equipo.getObservaciones());
        notificador.abrirPanel(panelFormulario);
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtCodigo, txtNombre, cmbCategoria, cmbUbicacion);
        try {
            if (txtCodigo.getText().isBlank()) {
                Formularios.marcarError(txtCodigo, "El codigo es obligatorio.");
                return;
            }
            if (txtNombre.getText().isBlank()) {
                Formularios.marcarError(txtNombre, "El nombre es obligatorio.");
                return;
            }
            if (cmbCategoria.getValue() == null) {
                Formularios.marcarError(cmbCategoria, "Selecciona la categoria.");
                return;
            }
            if (cmbUbicacion.getValue() == null) {
                Formularios.marcarError(cmbUbicacion, "Selecciona la ubicacion.");
                return;
            }

            Equipo equipo = Equipo.builder()
                    .id(idEditando)
                    .codigo(txtCodigo.getText().trim().toUpperCase())
                    .nombre(txtNombre.getText().trim())
                    .categoria(cmbCategoria.getValue())
                    .ubicacion(cmbUbicacion.getValue())
                    .fabricante(txtFabricante.getText())
                    .modelo(txtModelo.getText())
                    .numeroSerie(txtSerie.getText())
                    .fechaInstalacion(dpInstalacion.getValue())
                    .estado(cmbEstado.getValue() == null ? EstadoEquipo.OPERATIVO : cmbEstado.getValue())
                    .critico(chkCritico.isSelected())
                    .costoAdquisicion(Formularios.decimal(txtCosto))
                    .observaciones(taObservaciones.getText())
                    .build();

            Equipo guardado = equipoService.guardar(equipo);
            notificador.exito((idEditando == null ? "Equipo " + guardado.getCodigo() + " registrado."
                    : "Equipo actualizado."));
            onCerrarFormulario();
            cargar();
        } catch (ConstraintViolationException e) {
            Formularios.mostrarViolaciones(e, Map.of(
                    "codigo", txtCodigo, "nombre", txtNombre,
                    "categoria", cmbCategoria, "ubicacion", cmbUbicacion,
                    "costoAdquisicion", txtCosto))
                    .forEach(notificador::advertencia);
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onEliminar() {
        Equipo equipo = tabla.getSelectionModel().getSelectedItem();
        if (equipo == null) {
            return;
        }
        notificador.confirmar("Eliminar equipo",
                "¿Eliminar el equipo " + equipo.getCodigo() + "? Solo es posible si no tiene ordenes.",
                "Eliminar equipo", () -> {
                    try {
                        equipoService.eliminar(equipo.getId());
                        notificador.exito("Equipo eliminado.");
                        cargar();
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    @FXML
    private void onHistorial() {
        Equipo equipo = tabla.getSelectionModel().getSelectedItem();
        if (equipo == null) {
            notificador.advertencia("Selecciona un equipo de la tabla.");
            return;
        }
        List<OrdenTrabajo> ordenes = equipoService.historial(equipo.getId());

        VBox caja = new VBox(8);
        caja.getStyleClass().addAll("tarjeta", "dialogo");
        caja.setMaxSize(640, Region.USE_PREF_SIZE);

        Label titulo = new Label("Historial de " + equipo.getCodigo() + " · " + equipo.getNombre());
        titulo.getStyleClass().add("dialogo-titulo");

        Label resumen = new Label(ordenes.size() + " intervenciones  ·  Estado actual: "
                + equipo.getEstado().getEtiqueta()
                + (equipo.getFechaInstalacion() == null ? ""
                : "  ·  Instalado " + equipo.getFechaInstalacion().format(FECHA)));
        resumen.getStyleClass().addAll("chip", equipo.getEstado().getClase());

        VBox lista = new VBox(4);
        lista.getStyleClass().add("lista-ventas");
        if (ordenes.isEmpty()) {
            lista.getChildren().add(new Label("Este equipo aun no tiene ordenes de trabajo."));
        }
        for (OrdenTrabajo o : ordenes) {
            Label chip = new Label(o.getEstado().getEtiqueta());
            chip.getStyleClass().addAll("chip", o.getEstado().getClase());

            VBox info = new VBox(1);
            Label filaTitulo = new Label(o.getNumero() + " · " + o.getTitulo());
            filaTitulo.getStyleClass().add("ranking-nombre");
            Label detalle = new Label((o.getFechaCreacion().toLocalDate().format(FECHA))
                    + " · " + o.getTipo().getEtiqueta()
                    + (o.getTecnico() == null ? "" : " · " + o.getTecnico().getNombre()));
            detalle.getStyleClass().add("ranking-detalle");
            info.getChildren().addAll(filaTitulo, detalle);
            info.setMaxWidth(Double.MAX_VALUE);

            Label monto = new Label("S/ " + Tablas.MONEDA.format(o.getCostoTotal()).replace("S/", "").trim());
            monto.getStyleClass().add("ranking-precio");

            HBox fila = new HBox(10, chip, info, espacio(), monto);
            fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            fila.getStyleClass().add("venta-fila");
            lista.getChildren().add(fila);
        }

        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("drawer-scroll");
        scroll.setPrefHeight(360);

        HBox acciones = new HBox(10, notificador.botonCerrar(null));
        acciones.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        acciones.getStyleClass().add("dialogo-acciones");

        caja.getChildren().addAll(titulo, resumen, scroll, acciones);
        notificador.mostrarContenido(caja);
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
        idEditando = null;
    }

    private void limpiarFormulario() {
        txtCodigo.clear();
        txtNombre.clear();
        cmbCategoria.setValue(null);
        cmbUbicacion.setValue(null);
        txtFabricante.clear();
        txtModelo.clear();
        txtSerie.clear();
        dpInstalacion.setValue(null);
        cmbEstado.setValue(EstadoEquipo.OPERATIVO);
        chkCritico.setSelected(false);
        txtCosto.clear();
        taObservaciones.clear();
        Formularios.limpiarErrores(txtCodigo, txtNombre, cmbCategoria, cmbUbicacion, txtCosto);
    }

    /** Ruta de la foto de categoria de un equipo (fallback: mecanica). */
    private String rutaFoto(Equipo equipo) {
        return rutaFotoCategoria(equipo == null || equipo.getCategoria() == null
                ? null : equipo.getCategoria().getNombre());
    }

    /** Foto de categoria por nombre; {@code null} si el recurso no existe. */
    private String rutaFotoCategoria(String categoria) {
        String clave = categoria == null ? "" : categoria.toLowerCase(Locale.ROOT);
        String archivo = switch (clave) {
            case "electrica" -> "electrica";
            case "neumatica" -> "neumatica";
            case "instrumentacion" -> "instrumentacion";
            case "termohidraulica" -> "termohidraulica";
            default -> "mecanica";
        };
        return Tablas.recurso("/img/equipos/" + archivo + ".jpg");
    }

    /** Pinta la foto de la cabecera del drawer y el pie con la categoria. */
    private void pintarFotoCabecera(CategoriaEquipo categoria) {
        String ruta = rutaFotoCategoria(categoria == null ? null : categoria.getNombre());
        if (ruta != null && fotoCabecera != null) {
            fotoCabecera.setStyle("-fx-background-image: url('" + ruta + "');"
                    + " -fx-background-size: cover; -fx-background-position: center 40%;");
        }
        if (lblFotoCategoria != null) {
            lblFotoCategoria.setText(categoria == null
                    ? "Elige una categoria" : categoria.getNombre());
        }
    }

    private Region espacio() {
        Region r = new Region();
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }
}
