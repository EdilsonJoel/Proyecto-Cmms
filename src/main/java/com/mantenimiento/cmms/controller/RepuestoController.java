package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.*;
import com.mantenimiento.cmms.service.CatalogoService;
import com.mantenimiento.cmms.service.RepuestoService;
import com.mantenimiento.cmms.service.SesionService;
import com.mantenimiento.cmms.ui.*;
import jakarta.validation.ConstraintViolationException;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/** Inventario de repuestos: CRUD, movimientos de stock (kardex) y consulta del historial. */
@Component
@RequiredArgsConstructor
public class RepuestoController implements Refrescable {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final RepuestoService repuestoService;
    private final CatalogoService catalogoService;
    private final SesionService sesion;
    private final Notificador notificador;

    @FXML private Button btnNueva;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Button btnMovimiento;
    @FXML private Button btnKardex;

    @FXML private TextField txtBuscar;

    @FXML private TableView<Repuesto> tabla;
    @FXML private TableColumn<Repuesto, String> colCodigo;
    @FXML private TableColumn<Repuesto, Repuesto> colNombre;
    @FXML private TableColumn<Repuesto, Repuesto> colStock;
    @FXML private TableColumn<Repuesto, String> colMinimo;
    @FXML private TableColumn<Repuesto, BigDecimal> colPrecio;
    @FXML private TableColumn<Repuesto, BigDecimal> colValor;
    @FXML private TableColumn<Repuesto, String> colProveedor;
    @FXML private Paginador paginador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtCodigo;
    @FXML private TextField txtNombre;
    @FXML private TextArea taDescripcion;
    @FXML private TextField txtStockMinimo;
    @FXML private TextField txtUnidad;
    @FXML private TextField txtPrecio;
    @FXML private TextField txtUbicacionAlmacen;
    @FXML private ComboBox<Proveedor> cmbProveedor;
    @FXML private CheckBox chkActivo;

    private Long idEditando;

    @FXML
    public void initialize() {
        Tablas.configurar(tabla, Feather.PACKAGE, "Sin repuestos",
                "Registra las piezas del almacen de mantenimiento.", this::onDobleClic);

        colCodigo.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getCodigo()));
        colNombre.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colNombre.setCellFactory(Tablas.celdaMiniatura(this::rutaFoto, Repuesto::getNombre));
        colNombre.setComparator(Comparator.comparing(Repuesto::getNombre, String.CASE_INSENSITIVE_ORDER));
        colStock.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colStock.setCellFactory(Tablas.celdaStockRepuesto());
        colMinimo.setCellValueFactory(d -> new SimpleObjectProperty<>(
                d.getValue().getStockMinimo() + " " + d.getValue().getUnidad()));
        colPrecio.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getPrecioCosto()));
        colPrecio.setCellFactory(Tablas.celdaMoneda());
        colValor.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getValorStock()));
        colValor.setCellFactory(Tablas.celdaMoneda());
        colProveedor.setCellValueFactory(d -> new SimpleObjectProperty<>(
                d.getValue().getProveedor() == null ? null : d.getValue().getProveedor().getNombre()));
        colProveedor.setCellFactory(Tablas.celdaOpcional());

        txtBuscar.textProperty().addListener((o, a, b) -> paginador.irAPrimera());
        paginador.setOnCambio(this::cargar);
        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, a, b) -> actualizarBotones());

        Combos.proveedores(cmbProveedor);
        Formularios.soloDigitos(txtStockMinimo, 6);
        Formularios.soloDecimal(txtPrecio);
        Formularios.limpiarAlEditar(txtCodigo, txtNombre, txtStockMinimo, txtUnidad, txtPrecio,
                txtUbicacionAlmacen);
    }

    @Override
    public void refrescar() {
        Combos.opciones(cmbProveedor, catalogoService.proveedores());
        cargar();
    }

    private void cargar() {
        var pagina = repuestoService.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        tabla.setItems(FXCollections.observableArrayList(pagina.getContent()));
        paginador.actualizar(pagina);
        actualizarBotones();
    }

    private void actualizarBotones() {
        boolean hay = tabla.getSelectionModel().getSelectedItem() != null;
        boolean gestiona = sesion.puedeGestionar();
        btnNueva.setDisable(!gestiona);
        btnEditar.setDisable(!hay || !gestiona);
        btnEliminar.setDisable(!hay || !gestiona);
        btnMovimiento.setDisable(!hay || !gestiona);
        btnKardex.setDisable(!hay);
    }

    private void onDobleClic(Repuesto r) {
        if (sesion.puedeGestionar()) {
            onEditar();
        } else {
            onKardex();
        }
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNueva() {
        Combos.opciones(cmbProveedor, catalogoService.proveedores());
        idEditando = null;
        limpiarFormulario();
        lblTituloFormulario.setText("Nuevo repuesto");
        lblSubtituloFormulario.setText("Pieza del almacen de mantenimiento");
        chkActivo.setSelected(true);
        txtUnidad.setText("pza");
        notificador.abrirPanel(panelFormulario);
        javafx.application.Platform.runLater(txtCodigo::requestFocus);
    }

    @FXML
    private void onEditar() {
        Repuesto repuesto = tabla.getSelectionModel().getSelectedItem();
        if (repuesto == null) {
            notificador.advertencia("Selecciona un repuesto de la tabla.");
            return;
        }
        Combos.opciones(cmbProveedor, catalogoService.proveedores());
        idEditando = repuesto.getId();
        lblTituloFormulario.setText("Editar " + repuesto.getCodigo());
        lblSubtituloFormulario.setText("El stock solo cambia con movimientos de almacen");
        txtCodigo.setText(repuesto.getCodigo());
        txtNombre.setText(repuesto.getNombre());
        taDescripcion.setText(repuesto.getDescripcion());
        txtStockMinimo.setText(String.valueOf(repuesto.getStockMinimo()));
        txtUnidad.setText(repuesto.getUnidad());
        txtPrecio.setText(repuesto.getPrecioCosto() == null ? "" : repuesto.getPrecioCosto().toPlainString());
        txtUbicacionAlmacen.setText(repuesto.getUbicacionAlmacen());
        cmbProveedor.setValue(repuesto.getProveedor());
        chkActivo.setSelected(repuesto.isActivo());
        notificador.abrirPanel(panelFormulario);
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtCodigo, txtNombre, txtStockMinimo, txtPrecio);
        try {
            if (txtCodigo.getText().isBlank()) {
                Formularios.marcarError(txtCodigo, "El codigo es obligatorio.");
                return;
            }
            if (txtNombre.getText().isBlank()) {
                Formularios.marcarError(txtNombre, "El nombre es obligatorio.");
                return;
            }
            int minimo = 0;
            try {
                minimo = txtStockMinimo.getText().isBlank() ? 0 : Integer.parseInt(txtStockMinimo.getText());
            } catch (NumberFormatException ex) {
                Formularios.marcarError(txtStockMinimo, "Ingresa un numero.");
                return;
            }

            Repuesto repuesto = Repuesto.builder()
                    .id(idEditando)
                    .codigo(txtCodigo.getText().trim().toUpperCase())
                    .nombre(txtNombre.getText().trim())
                    .descripcion(taDescripcion.getText())
                    .stockMinimo(minimo)
                    .unidad(txtUnidad.getText().isBlank() ? "pza" : txtUnidad.getText().trim())
                    .precioCosto(Formularios.decimal(txtPrecio))
                    .ubicacionAlmacen(txtUbicacionAlmacen.getText())
                    .proveedor(cmbProveedor.getValue())
                    .activo(chkActivo.isSelected())
                    .build();

            Repuesto guardado = repuestoService.guardar(repuesto);
            notificador.exito((idEditando == null ? "Repuesto " + guardado.getCodigo() + " registrado."
                    : "Repuesto actualizado."));
            onCerrarFormulario();
            cargar();
        } catch (ConstraintViolationException e) {
            Formularios.mostrarViolaciones(e, Map.of(
                    "codigo", txtCodigo, "nombre", txtNombre, "precioCosto", txtPrecio))
                    .forEach(notificador::advertencia);
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onEliminar() {
        Repuesto repuesto = tabla.getSelectionModel().getSelectedItem();
        if (repuesto == null) {
            return;
        }
        notificador.confirmar("Eliminar repuesto",
                "¿Eliminar " + repuesto.getNombre() + "? Solo es posible si no fue usado en ordenes.",
                "Eliminar repuesto", () -> {
                    try {
                        repuestoService.eliminar(repuesto.getId());
                        notificador.exito("Repuesto eliminado.");
                        cargar();
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    /** Entrada, salida o ajuste de stock con motivo (genera registro de kardex). */
    @FXML
    private void onMovimiento() {
        Repuesto repuesto = tabla.getSelectionModel().getSelectedItem();
        if (repuesto == null) {
            return;
        }
        ComboBox<TipoMovimiento> cmbTipo = new ComboBox<>();
        cmbTipo.getItems().addAll(TipoMovimiento.values());
        cmbTipo.setValue(TipoMovimiento.ENTRADA);
        Combos.formato(cmbTipo, t -> t == null ? "" : t.getEtiqueta());

        TextField txtCantidad = new TextField();
        txtCantidad.setPromptText("Cantidad (" + repuesto.getUnidad() + ")");
        Formularios.soloDigitos(txtCantidad, 6);
        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Ej. Ingreso por compra OC-001");

        VBox contenido = new VBox(10,
                new Label("Stock actual: " + repuesto.getStock() + " " + repuesto.getUnidad()),
                Formularios.campo("Tipo de movimiento", cmbTipo),
                Formularios.campo("Cantidad", txtCantidad),
                Formularios.campo("Motivo", txtMotivo));

        notificador.dialogoContenido(Feather.ARCHIVE, "info",
                "Movimiento · " + repuesto.getCodigo(), contenido,
                "Registrar movimiento", StylesAccento(), () -> {
                    try {
                        int cantidad = Integer.parseInt(txtCantidad.getText());
                        repuestoService.registrarMovimiento(repuesto.getId(), cmbTipo.getValue(),
                                cantidad, txtMotivo.getText(), null);
                        notificador.exito("Movimiento registrado. Stock: "
                                + repuestoService.obtener(repuesto.getId()).getStock()
                                + " " + repuesto.getUnidad());
                        notificador.cerrarDialogo();
                        cargar();
                    } catch (NumberFormatException | java.time.DateTimeException ex) {
                        notificador.advertencia("Ingresa una cantidad valida.");
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    private String StylesAccento() {
        return atlantafx.base.theme.Styles.ACCENT;
    }

    /** Historial de entradas, salidas y ajustes del repuesto. */
    @FXML
    private void onKardex() {
        Repuesto repuesto = tabla.getSelectionModel().getSelectedItem();
        if (repuesto == null) {
            notificador.advertencia("Selecciona un repuesto.");
            return;
        }
        var movimientos = repuestoService.kardex(repuesto.getId());

        VBox caja = new VBox(8);
        caja.getStyleClass().addAll("tarjeta", "dialogo");
        caja.setMaxSize(660, Region.USE_PREF_SIZE);

        Label titulo = new Label("Kardex · " + repuesto.getCodigo() + " " + repuesto.getNombre());
        titulo.getStyleClass().add("dialogo-titulo");

        Label stock = new Label("Stock actual: " + repuesto.getStock() + " " + repuesto.getUnidad()
                + "  ·  Minimo: " + repuesto.getStockMinimo());
        stock.getStyleClass().addAll("chip", repuesto.isSinStock() ? "peligro"
                : repuesto.isStockBajo() ? "advertencia" : "exito");

        VBox lista = new VBox(4);
        lista.getStyleClass().add("lista-ventas");
        if (movimientos.isEmpty()) {
            lista.getChildren().add(new Label("Sin movimientos registrados."));
        }
        for (MovimientoRepuesto m : movimientos) {
            Label chip = new Label(m.getTipo().getEtiqueta());
            chip.getStyleClass().addAll("chip", m.getTipo().getClase());

            VBox info = new VBox(1);
            Label filaTitulo = new Label(m.getCantidad() + " " + repuesto.getUnidad()
                    + (m.getMotivo() == null ? "" : " · " + m.getMotivo()));
            filaTitulo.getStyleClass().add("ranking-nombre");
            Label detalle = new Label(m.getFecha().format(FECHA_HORA)
                    + (m.getOrden() == null ? "" : " · " + m.getOrden().getNumero())
                    + (m.getRegistradoPor() == null ? "" : " · " + m.getRegistradoPor()));
            detalle.getStyleClass().add("ranking-detalle");
            info.getChildren().addAll(filaTitulo, detalle);
            info.setMaxWidth(Double.MAX_VALUE);

            Label saldo = new Label("Saldo " + m.getStockResultante());
            saldo.getStyleClass().add("ranking-precio");

            HBox fila = new HBox(10, chip, info, espacio(), saldo);
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

        caja.getChildren().addAll(titulo, stock, scroll, acciones);
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
        taDescripcion.clear();
        txtStockMinimo.clear();
        txtUnidad.clear();
        txtPrecio.clear();
        txtUbicacionAlmacen.clear();
        cmbProveedor.setValue(null);
        chkActivo.setSelected(true);
        Formularios.limpiarErrores(txtCodigo, txtNombre, txtStockMinimo, txtPrecio);
    }

    /**
     * Miniatura del repuesto por tipo de pieza: reutiliza fotos por palabra
     * clave y, si no coincide ninguna, la foto generica de mecanica.
     */
    private String rutaFoto(Repuesto repuesto) {
        if (repuesto == null) {
            return null;
        }
        String t = ((repuesto.getNombre() == null ? "" : repuesto.getNombre()) + " "
                + (repuesto.getDescripcion() == null ? "" : repuesto.getDescripcion()))
                .toLowerCase(Locale.ROOT);
        String ruta = null;
        if (t.contains("rodamiento") || t.contains("reten") || t.contains("sello")
                || t.contains("junta") || t.contains("aro ")) {
            ruta = "/img/repuestos/rodamiento.jpg";
        } else if (t.contains("correa")) {
            ruta = "/img/repuestos/correa.jpg";
        } else if (t.contains("filtro")) {
            ruta = "/img/repuestos/filtro.jpg";
        } else if (t.contains("aceite") || t.contains("grasa") || t.contains("refrigerante")
                || t.contains("lubric")) {
            ruta = "/img/repuestos/aceite.jpg";
        } else if (t.contains("valvul") || t.contains("cilindro") || t.contains("manguera")
                || t.contains("neumatic") || t.contains("presion")) {
            ruta = "/img/equipos/neumatica.jpg";
        } else if (t.contains("sensor") || t.contains("termopar") || t.contains("transmisor")
                || t.contains("manometro") || t.contains("presostato") || t.contains("instrument")) {
            ruta = "/img/equipos/instrumentacion.jpg";
        } else if (t.contains("contactor") || t.contains("fusible") || t.contains("bobina")
                || t.contains("protector") || t.contains("pulsador") || t.contains("lampara")
                || t.contains("enchufe") || t.contains("variador") || t.contains("enfriador")
                || t.contains("electroventilador") || t.contains("cable") || t.contains("rj45")) {
            ruta = "/img/equipos/electrica.jpg";
        } else {
            ruta = "/img/equipos/mecanica.jpg";
        }
        return Tablas.recurso(ruta);
    }

    private Region espacio() {
        Region r = new Region();
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }
}
