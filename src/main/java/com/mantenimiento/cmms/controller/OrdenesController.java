package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.*;
import com.mantenimiento.cmms.service.*;
import com.mantenimiento.cmms.ui.*;
import atlantafx.base.theme.Styles;
import jakarta.validation.ConstraintViolationException;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Vista central: listado paginado de ordenes de trabajo con filtros y su ciclo de vida
 * (nueva/editar en panel lateral, iniciar, completar, cancelar, PDF y repuestos).
 */
@Component
@RequiredArgsConstructor
public class OrdenesController implements Refrescable {

    private static final int TAMANIO = 15;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OrdenService ordenService;
    private final EquipoService equipoService;
    private final TecnicoService tecnicoService;
    private final RepuestoService repuestoService;
    private final SesionService sesion;
    private final OrdenPdfService pdfService;
    private final Exportador exportador;
    private final Notificador notificador;

    // ------------------------------------------------------------- cabecera
    @FXML private Button btnNueva;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Button btnIniciar;
    @FXML private Button btnCompletar;
    @FXML private Button btnCancelar;
    @FXML private Button btnPdf;

    // --------------------------------------------------------------- filtros
    @FXML private TextField txtBuscar;
    @FXML private ComboBox<EstadoOrden> cmbEstado;
    @FXML private ComboBox<TipoOrden> cmbTipo;

    // ---------------------------------------------------------------- tabla
    @FXML private TableView<OrdenTrabajo> tabla;
    @FXML private TableColumn<OrdenTrabajo, String> colNumero;
    @FXML private TableColumn<OrdenTrabajo, String> colTitulo;
    @FXML private TableColumn<OrdenTrabajo, String> colEquipo;
    @FXML private TableColumn<OrdenTrabajo, OrdenTrabajo> colTipo;
    @FXML private TableColumn<OrdenTrabajo, OrdenTrabajo> colPrioridad;
    @FXML private TableColumn<OrdenTrabajo, OrdenTrabajo> colEstado;
    @FXML private TableColumn<OrdenTrabajo, String> colTecnico;
    @FXML private TableColumn<OrdenTrabajo, String> colProgramada;
    @FXML private TableColumn<OrdenTrabajo, BigDecimal> colCosto;
    @FXML private Paginador paginador;

    // ------------------------------------------------------------- formulario
    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private ComboBox<Equipo> cmbEquipo;
    @FXML private TextField txtTitulo;
    @FXML private ComboBox<TipoOrden> cmbFTipo;
    @FXML private ComboBox<Prioridad> cmbFPrioridad;
    @FXML private DatePicker dpProgramada;
    @FXML private ComboBox<Tecnico> cmbFTecnico;
    @FXML private TextField txtFHoras;
    @FXML private TextField txtFCostoMO;
    @FXML private TextArea taDescripcion;
    @FXML private TitledPane seccionRepuestos;
    @FXML private TableView<DetalleRepuesto> tablaDetalles;
    @FXML private TableColumn<DetalleRepuesto, String> colRepuesto;
    @FXML private TableColumn<DetalleRepuesto, Integer> colCantidad;
    @FXML private TableColumn<DetalleRepuesto, BigDecimal> colSubtotal;
    @FXML private ComboBox<Repuesto> cmbFRepuesto;
    @FXML private TextField txtFCantidad;
    @FXML private Button btnAgregarRep;
    @FXML private Button btnQuitarRep;

    private Long idEditando;

    @FXML
    public void initialize() {
        configurarTabla();
        configurarFiltros();
        configurarFormulario();

        paginador.setOnCambio(this::cargar);
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> actualizarBotones());
    }

    @Override
    public void refrescar() {
        cargarCombos();
        cargar();
    }

    // ------------------------------------------------------------ listado

    private void cargar() {
        Long idTecnico = sesion.esTecnico() ? idTecnicoDeLaSesion() : null;
        var pagina = ordenService.listar(txtBuscar.getText(),
                cmbEstado.getValue(), cmbTipo.getValue(), idTecnico,
                paginador.getPagina(), paginador.getTamanio());
        tabla.setItems(FXCollections.observableArrayList(pagina.getContent()));
        paginador.actualizar(pagina);
        actualizarBotones();
    }

    private Long idTecnicoDeLaSesion() {
        return tecnicoService.deLaSesion().map(Tecnico::getId).orElse(-1L);
    }

    private void configurarTabla() {
        Tablas.configurar(tabla, Feather.CLIPBOARD, "Sin ordenes de trabajo",
                "Crea una orden para registrar una intervencion de mantenimiento.", this::onEditarItem);

        colNumero.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().getNumero()));
        colNumero.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio ? null : valor);
                getStyleClass().setAll("celda-id");
            }
        });
        colTitulo.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().getTitulo()));
        colEquipo.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(
                d.getValue().getEquipo().getCodigo() + " · " + d.getValue().getEquipo().getNombre()));

        colTipo.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colTipo.setCellFactory(Tablas.celdaPastilla(
                o -> o.getTipo().getEtiqueta(),
                o -> switch (o.getTipo()) {
                    case PREVENTIVO -> "exito";
                    case CORRECTIVO -> "advertencia";
                    case PREDICTIVO -> "info";
                }));

        colPrioridad.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colPrioridad.setCellFactory(Tablas.celdaPastilla(
                o -> o.getPrioridad().getEtiqueta(), o -> o.getPrioridad().getClase()));

        colEstado.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colEstado.setCellFactory(Tablas.celdaPastilla(
                o -> o.getEstado().getEtiqueta(), o -> o.getEstado().getClase()));

        colTecnico.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(
                d.getValue().getTecnico() == null ? null : d.getValue().getTecnico().getNombre()));
        colTecnico.setCellFactory(Tablas.celdaOpcional());

        colProgramada.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(
                d.getValue().getFechaProgramada() == null ? null
                        : d.getValue().getFechaProgramada().format(FECHA)));
        colProgramada.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio ? null : (valor == null ? "—" : valor));
                OrdenTrabajo orden = getTableRow() == null ? null : getTableRow().getItem();
                boolean vencida = orden != null && orden.isVencida();
                setStyle(vencida ? "-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;" : null);
            }
        });

        colCosto.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getCostoTotal()));
        colCosto.setCellFactory(Tablas.celdaMoneda());

        colRepuesto.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(
                d.getValue().getRepuesto().getCodigo() + " · " + d.getValue().getRepuesto().getNombre()));
        colCantidad.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().getCantidad()));
        colCantidad.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : valor + " " + getTableRowItemUnidad());
            }

            private String getTableRowItemUnidad() {
                DetalleRepuesto d = getTableRow() == null ? null : getTableRow().getItem();
                return d == null ? "" : d.getRepuesto().getUnidad();
            }
        });
        colSubtotal.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getSubtotal()));
        colSubtotal.setCellFactory(Tablas.celdaMoneda());
    }

    private void configurarFiltros() {
        Combos.formato(cmbEstado, e -> e == null ? "Todos los estados" : e.getEtiqueta());
        cmbEstado.getItems().add(null);
        cmbEstado.getItems().addAll(EstadoOrden.values());
        cmbEstado.valueProperty().addListener((o, a, b) -> paginador.irAPrimera());

        Combos.formato(cmbTipo, t -> t == null ? "Todos los tipos" : t.getEtiqueta());
        cmbTipo.getItems().add(null);
        cmbTipo.getItems().addAll(TipoOrden.values());
        cmbTipo.valueProperty().addListener((o, a, b) -> paginador.irAPrimera());

        txtBuscar.textProperty().addListener((o, a, b) -> paginador.irAPrimera());
    }

    private void configurarFormulario() {
        Combos.equipos(cmbEquipo);
        Combos.tecnicos(cmbFTecnico);
        Combos.repuestos(cmbFRepuesto);
        cmbFTipo.getItems().addAll(TipoOrden.values());
        Combos.formato(cmbFTipo, t -> t == null ? "Selecciona" : t.getEtiqueta());
        cmbFPrioridad.getItems().addAll(Prioridad.values());
        Combos.formato(cmbFPrioridad, p -> p == null ? "Selecciona" : p.getEtiqueta());

        Formularios.soloDecimal(txtFHoras);
        Formularios.soloDecimal(txtFCostoMO);
        Formularios.soloDigitos(txtFCantidad, 4);
        Formularios.limpiarAlEditar(txtTitulo, txtFHoras, txtFCostoMO, txtFCantidad);
    }

    private void cargarCombos() {
        Combos.opciones(cmbEquipo, equipoService.todos().stream().filter(e -> !e.isDeBaja()).toList());
        Combos.opciones(cmbFTecnico, tecnicoService.activos());
        Combos.opciones(cmbFRepuesto, repuestoService.activos());
    }

    private void actualizarBotones() {
        OrdenTrabajo orden = tabla.getSelectionModel().getSelectedItem();
        boolean gestiona = sesion.puedeGestionar();
        boolean propia = orden != null && ordenService.puedeModificar(orden);
        boolean editable = orden != null && !orden.getEstado().esFinal() && propia;

        btnNueva.setDisable(!gestiona);
        btnEditar.setDisable(!(editable && gestiona));
        btnEliminar.setDisable(!(orden != null && orden.getEstado() != EstadoOrden.COMPLETADA
                && orden.getEstado() != EstadoOrden.CANCELADA && gestiona));
        btnIniciar.setDisable(!(editable && orden.getEstado().puedeIniciar()));
        btnCompletar.setDisable(!(editable && orden.getEstado().puedeCompletar()));
        btnCancelar.setDisable(!(editable && orden.getEstado().puedeCancelar()));
        btnPdf.setDisable(orden == null);
    }

    // --------------------------------------------------------- acciones CRUD

    @FXML
    private void onNueva() {
        cargarCombos();
        idEditando = null;
        limpiarFormulario();
        lblTituloFormulario.setText("Nueva orden de trabajo");
        lblSubtituloFormulario.setText("Registra la intervencion y asigna un equipo");
        seccionRepuestos.setVisible(false);
        seccionRepuestos.setManaged(false);
        notificador.abrirPanel(panelFormulario);
        javafx.application.Platform.runLater(cmbEquipo::requestFocus);
    }

    private void onEditarItem(OrdenTrabajo orden) {
        if (orden != null && !orden.getEstado().esFinal() && sesion.puedeGestionar()) {
            onEditar();
        } else if (orden != null) {
            verDetalle(orden);
        }
    }

    @FXML
    private void onEditar() {
        OrdenTrabajo orden = tabla.getSelectionModel().getSelectedItem();
        if (orden == null) {
            notificador.advertencia("Selecciona una orden de la tabla.");
            return;
        }
        if (orden.getEstado().esFinal()) {
            verDetalle(orden);
            return;
        }
        if (!sesion.puedeGestionar()) {
            notificador.advertencia("Solo los supervisores pueden editar ordenes.");
            return;
        }
        cargarCombos();
        idEditando = orden.getId();
        lblTituloFormulario.setText("Editar " + orden.getNumero());
        lblSubtituloFormulario.setText("Actualiza los datos de la orden");

        cmbEquipo.setValue(orden.getEquipo());
        txtTitulo.setText(orden.getTitulo());
        cmbFTipo.setValue(orden.getTipo());
        cmbFPrioridad.setValue(orden.getPrioridad());
        dpProgramada.setValue(orden.getFechaProgramada());
        cmbFTecnico.setValue(orden.getTecnico());
        txtFHoras.setText(orden.getHorasManoObra() == null ? "" : orden.getHorasManoObra().toPlainString());
        txtFCostoMO.setText(orden.getCostoManoObra() == null ? "" : orden.getCostoManoObra().toPlainString());
        taDescripcion.setText(orden.getDescripcion());

        seccionRepuestos.setVisible(true);
        seccionRepuestos.setManaged(true);
        cargarDetalles(orden);
        notificador.abrirPanel(panelFormulario);
    }

    private void verDetalle(OrdenTrabajo orden) {
        VBox caja = new VBox(10);
        caja.getStyleClass().addAll("tarjeta", "dialogo");
        caja.setMaxSize(560, Region.USE_PREF_SIZE);

        Label titulo = new Label(orden.getNumero() + "  ·  " + orden.getTitulo());
        titulo.getStyleClass().add("dialogo-titulo");
        Label chip = new Label(orden.getEstado().getEtiqueta() + "   ·   "
                + orden.getPrioridad().getEtiqueta() + "   ·   " + orden.getTipo().getEtiqueta());
        chip.getStyleClass().addAll("chip", orden.getEstado().getClase());

        StringBuilder texto = new StringBuilder();
        texto.append("Equipo: ").append(orden.getEquipo().getNombre())
                .append(" (").append(orden.getEquipo().getCodigo()).append(")\n");
        texto.append("Tecnico: ").append(orden.getTecnico() == null ? "Sin asignar"
                : orden.getTecnico().getNombre()).append("\n");
        texto.append("Programada: ").append(orden.getFechaProgramada() == null ? "Sin programar"
                : orden.getFechaProgramada().format(FECHA)).append("\n\n");
        texto.append("Descripcion:\n").append(nulo(orden.getDescripcion())).append("\n");
        if (orden.getSolucion() != null && !orden.getSolucion().isBlank()) {
            texto.append("\nTrabajo realizado:\n").append(orden.getSolucion());
        }
        texto.append("\n\nCosto mano de obra: S/ ").append(orden.getCostoManoObra());
        texto.append("\nCosto repuestos: S/ ").append(orden.getCostoRepuestos());
        texto.append("\nCosto total: S/ ").append(orden.getCostoTotal());

        Label cuerpo = new Label(texto.toString());
        cuerpo.setWrapText(true);
        cuerpo.getStyleClass().add("dialogo-mensaje");

        HBox acciones = new HBox(10, notificador.botonCerrar(null));
        acciones.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        caja.getChildren().addAll(titulo, chip, cuerpo, acciones);
        notificador.mostrarContenido(caja);
    }

    private String nulo(String texto) {
        return texto == null || texto.isBlank() ? "Sin observaciones." : texto;
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(cmbEquipo, txtTitulo, cmbFTipo, cmbFPrioridad, dpProgramada);
        try {
            Equipo equipo = cmbEquipo.getValue();
            if (equipo == null) {
                Formularios.marcarError(cmbEquipo, "Selecciona el equipo atendido.");
                return;
            }
            if (txtTitulo.getText().isBlank()) {
                Formularios.marcarError(txtTitulo, "Describe brevemente el trabajo.");
                return;
            }
            if (cmbFTipo.getValue() == null) {
                Formularios.marcarError(cmbFTipo, "Selecciona el tipo.");
                return;
            }
            if (cmbFPrioridad.getValue() == null) {
                Formularios.marcarError(cmbFPrioridad, "Selecciona la prioridad.");
                return;
            }

            OrdenTrabajo orden = OrdenTrabajo.builder()
                    .id(idEditando)
                    .equipo(equipo)
                    .tecnico(cmbFTecnico.getValue())
                    .tipo(cmbFTipo.getValue())
                    .prioridad(cmbFPrioridad.getValue())
                    .titulo(txtTitulo.getText().trim())
                    .descripcion(taDescripcion.getText())
                    .fechaProgramada(dpProgramada.getValue())
                    .horasManoObra(Formularios.decimal(txtFHoras))
                    .costoManoObra(Formularios.decimal(txtFCostoMO))
                    .build();

            OrdenTrabajo guardada = idEditando == null
                    ? ordenService.crear(orden)
                    : ordenService.actualizar(orden);

            notificador.exito((idEditando == null ? "Orden " + guardada.getNumero() + " creada."
                    : "Orden " + guardada.getNumero() + " actualizada."));
            onCerrarFormulario();
            cargar();
        } catch (ConstraintViolationException e) {
            Formularios.mostrarViolaciones(e, Map.of(
                    "titulo", txtTitulo,
                    "fechaProgramada", dpProgramada,
                    "equipo", cmbEquipo))
                    .forEach(notificador::advertencia);
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
        idEditando = null;
    }

    @FXML
    private void onLimpiar() {
        txtBuscar.clear();
        cmbEstado.setValue(null);
        cmbTipo.setValue(null);
    }

    // ----------------------------------------------------------- transiciones

    @FXML
    private void onIniciar() {
        OrdenTrabajo orden = seleccionada();
        if (orden == null) {
            return;
        }
        try {
            OrdenTrabajo iniciada = ordenService.iniciar(orden.getId());
            notificador.exito("Orden " + iniciada.getNumero() + " iniciada. Equipo en mantenimiento.");
            cargar();
            seleccionar(iniciada.getId());
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onCompletar() {
        OrdenTrabajo orden = seleccionada();
        if (orden == null) {
            return;
        }
        TextArea taSolucion = new TextArea();
        taSolucion.setPromptText("Trabajo realizado, causa raiz, observaciones…");
        taSolucion.setPrefRowCount(4);
        TextField txtHoras = new TextField();
        txtHoras.setPromptText("Horas hombre");
        Formularios.soloDecimal(txtHoras);
        TextField txtCosto = new TextField();
        txtCosto.setPromptText("Costo mano de obra (S/)");
        Formularios.soloDecimal(txtCosto);

        VBox campos = new VBox(10,
                Formularios.campo("Trabajo realizado *", taSolucion),
                new HBox(12, Formularios.campo("Horas", txtHoras),
                        Formularios.campo("Costo mano de obra (S/)", txtCosto)));

        notificador.dialogoContenido(Feather.CHECK_CIRCLE, "exito",
                "Completar " + orden.getNumero(), campos, "Completar orden", Styles.SUCCESS, () -> {
                    try {
                        OrdenTrabajo completa = ordenService.completar(orden.getId(),
                                taSolucion.getText(), Formularios.decimal(txtHoras),
                                Formularios.decimal(txtCosto));
                        notificador.exito("Orden " + completa.getNumero()
                                + " completada. Costo total: S/ " + completa.getCostoTotal());
                        notificador.cerrarDialogo();
                        cargar();
                        seleccionar(completa.getId());
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    @FXML
    private void onCancelar() {
        OrdenTrabajo orden = seleccionada();
        if (orden == null) {
            return;
        }
        TextArea taMotivo = new TextArea();
        taMotivo.setPromptText("Motivo de la cancelacion…");
        taMotivo.setPrefRowCount(3);

        notificador.dialogoContenido(Feather.X_CIRCLE, "peligro",
                "Cancelar " + orden.getNumero(),
                Formularios.campo("Motivo (queda en el historial)", taMotivo),
                "Cancelar orden", Styles.DANGER, () -> {
                    try {
                        OrdenTrabajo cancelada = ordenService.cancelar(orden.getId(), taMotivo.getText());
                        notificador.exito("Orden " + cancelada.getNumero() + " cancelada.");
                        notificador.cerrarDialogo();
                        cargar();
                        seleccionar(cancelada.getId());
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    @FXML
    private void onEliminar() {
        OrdenTrabajo orden = seleccionada();
        if (orden == null) {
            return;
        }
        notificador.confirmar("Eliminar orden",
                "¿Eliminar la orden " + orden.getNumero() + "? Esta accion no se puede deshacer.",
                "Eliminar orden", () -> {
                    try {
                        ordenService.eliminar(orden.getId());
                        notificador.exito("Orden eliminada.");
                        cargar();
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    @FXML
    private void onPdf() {
        OrdenTrabajo orden = seleccionada();
        if (orden == null) {
            return;
        }
        Long id = orden.getId();
        String numero = orden.getNumero();
        exportador.abrirTemporal(numero, Exportador.Formato.PDF, destino -> pdfService.generar(id, destino));
    }

    // ------------------------------------------------------------ repuestos

    private void cargarDetalles(OrdenTrabajo orden) {
        tablaDetalles.setItems(FXCollections.observableArrayList(orden.getDetalles()));
        tablaDetalles.setPlaceholder(new Label(orden.getDetalles().isEmpty()
                ? "Sin repuestos registrados" : ""));
        cargarCombos();
    }

    @FXML
    private void onAgregarRepuesto() {
        if (idEditando == null) {
            notificador.advertencia("Guarda la orden antes de agregar repuestos.");
            return;
        }
        Repuesto repuesto = cmbFRepuesto.getValue();
        if (repuesto == null) {
            notificador.advertencia("Selecciona un repuesto.");
            return;
        }
        int cantidad;
        try {
            cantidad = Integer.parseInt(txtFCantidad.getText());
        } catch (NumberFormatException e) {
            Formularios.marcarError(txtFCantidad, "Ingresa la cantidad.");
            return;
        }
        try {
            ordenService.agregarRepuesto(idEditando, repuesto.getId(), cantidad);
            notificador.exito(cantidad + " " + repuesto.getUnidad() + " de "
                    + repuesto.getNombre() + " agregado a la orden.");
            txtFCantidad.clear();
            cmbFRepuesto.setValue(null);
            cargarDetalles(ordenService.obtener(idEditando));
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onQuitarRepuesto() {
        DetalleRepuesto detalle = tablaDetalles.getSelectionModel().getSelectedItem();
        if (detalle == null || idEditando == null) {
            notificador.advertencia("Selecciona la linea de repuesto a quitar.");
            return;
        }
        notificador.confirmar("Quitar repuesto",
                "¿Devolver " + detalle.getCantidad() + " " + detalle.getRepuesto().getUnidad()
                        + " de " + detalle.getRepuesto().getNombre() + " al almacen?",
                "Quitar repuesto", () -> {
                    try {
                        ordenService.quitarRepuesto(idEditando, detalle.getId());
                        notificador.exito("Repuesto devuelto al almacen.");
                        cargarDetalles(ordenService.obtener(idEditando));
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    // ---------------------------------------------------------------- apoyo

    private OrdenTrabajo seleccionada() {
        OrdenTrabajo orden = tabla.getSelectionModel().getSelectedItem();
        if (orden == null) {
            notificador.advertencia("Selecciona una orden de la tabla.");
        }
        return orden;
    }

    private void seleccionar(Long idOrden) {
        tabla.getItems().stream()
                .filter(o -> o.getId().equals(idOrden))
                .findFirst()
                .ifPresent(o -> tabla.getSelectionModel().select(o));
    }

    private void limpiarFormulario() {
        cmbEquipo.setValue(null);
        txtTitulo.clear();
        cmbFTipo.setValue(null);
        cmbFPrioridad.setValue(null);
        dpProgramada.setValue(null);
        cmbFTecnico.setValue(null);
        txtFHoras.clear();
        txtFCostoMO.clear();
        taDescripcion.clear();
        cmbFRepuesto.setValue(null);
        txtFCantidad.clear();
        tablaDetalles.getItems().clear();
        Formularios.limpiarErrores(cmbEquipo, txtTitulo, cmbFTipo, cmbFPrioridad, dpProgramada, txtFCantidad);
    }
}
