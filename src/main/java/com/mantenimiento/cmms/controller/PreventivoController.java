package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.PlanPreventivo;
import com.mantenimiento.cmms.service.CatalogoService;
import com.mantenimiento.cmms.service.EquipoService;
import com.mantenimiento.cmms.service.PreventivoService;
import com.mantenimiento.cmms.service.TecnicoService;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Planes de mantenimiento preventivo: cada cuanto se atiende un equipo y
 * generacion de las ordenes de trabajo vencidas con un clic.
 */
@Component
@RequiredArgsConstructor
public class PreventivoController implements Refrescable {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PreventivoService preventivoService;
    private final EquipoService equipoService;
    private final TecnicoService tecnicoService;
    private final CatalogoService catalogoService;
    private final com.mantenimiento.cmms.service.SesionService sesion;
    private final Notificador notificador;
    private final com.mantenimiento.cmms.agente.AgenteTareas agente;

    @FXML private Button btnNueva;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Button btnGenerar;

    @FXML private Label lblAviso;
    @FXML private TextField txtBuscar;

    @FXML private TableView<PlanPreventivo> tabla;
    @FXML private TableColumn<PlanPreventivo, String> colEquipo;
    @FXML private TableColumn<PlanPreventivo, String> colTarea;
    @FXML private TableColumn<PlanPreventivo, String> colFrecuencia;
    @FXML private TableColumn<PlanPreventivo, String> colProxima;
    @FXML private TableColumn<PlanPreventivo, String> colTecnico;
    @FXML private TableColumn<PlanPreventivo, PlanPreventivo> colEstado;
    @FXML private Paginador paginador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private ComboBox<com.mantenimiento.cmms.model.Equipo> cmbEquipo;
    @FXML private TextField txtNombre;
    @FXML private TextField txtFrecuencia;
    @FXML private TextField txtHoras;
    @FXML private DatePicker dpProxima;
    @FXML private ComboBox<com.mantenimiento.cmms.model.Tecnico> cmbTecnico;
    @FXML private CheckBox chkActivo;
    @FXML private TextArea taObservaciones;

    private Long idEditando;

    @FXML
    public void initialize() {
        Tablas.configurar(tabla, Feather.CALENDAR, "Sin planes preventivos",
                "Programa cada cuanto se mantiene cada equipo.", this::onDobleClic);

        colEquipo.setCellValueFactory(d -> new SimpleObjectProperty<>(
                d.getValue().getEquipo().getCodigo() + " · " + d.getValue().getEquipo().getNombre()));
        colTarea.setCellValueFactory(d -> new SimpleObjectProperty<>(
                d.getValue().getNombre() == null ? "" : d.getValue().getNombre()));
        colFrecuencia.setCellValueFactory(d -> new SimpleObjectProperty<>(
                "cada " + d.getValue().getFrecuenciaDias() + " dias"));
        colProxima.setCellValueFactory(d -> new SimpleObjectProperty<>(
                d.getValue().getProximaFecha().format(FECHA)));
        colProxima.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio ? null : valor);
                PlanPreventivo p = getTableRow() == null ? null : getTableRow().getItem();
                if (p != null && p.isVencido()) {
                    setStyle("-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;");
                } else {
                    setStyle(null);
                }
            }
        });
        colTecnico.setCellValueFactory(d -> new SimpleObjectProperty<>(
                d.getValue().getTecnico() == null ? null : d.getValue().getTecnico().getNombre()));
        colTecnico.setCellFactory(Tablas.celdaOpcional());
        colEstado.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colEstado.setCellFactory(Tablas.celdaPastilla(
                p -> !p.isActivo() ? "Inactivo"
                        : p.isVencido() ? "Vencido"
                        : p.isProximo() ? "Por vencer" : "Programado",
                p -> !p.isActivo() ? "neutro"
                        : p.isVencido() ? "peligro"
                        : p.isProximo() ? "advertencia" : "exito"));

        txtBuscar.textProperty().addListener((o, a, b) -> paginador.irAPrimera());
        paginador.setOnCambio(this::cargar);
        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, a, b) -> actualizarBotones());

        Combos.equipos(cmbEquipo);
        Combos.tecnicos(cmbTecnico);
        Formularios.soloDigitos(txtFrecuencia, 4);
        Formularios.soloDecimal(txtHoras);
        Formularios.limpiarAlEditar(txtNombre, txtFrecuencia, txtHoras);
    }

    @Override
    public void refrescar() {
        cargar();
        int vencidos = (int) preventivoService.contarVencidos();
        lblAviso.setVisible(vencidos > 0);
        lblAviso.setManaged(vencidos > 0);
        lblAviso.setText(vencidos == 0 ? ""
                : vencidos + (vencidos == 1 ? " plan vencido" : " planes vencidos")
                + " · pulsa \"Generar ordenes\" para crear las OT correspondientes");
        btnGenerar.setText(vencidos == 0 ? "Generar ordenes" : "Generar " + vencidos + " orden(es)");
        btnGenerar.setDisable(vencidos == 0);
    }

    private void cargar() {
        var pagina = preventivoService.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
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
    }

    private void onDobleClic(PlanPreventivo p) {
        if (sesion.puedeGestionar()) {
            onEditar();
        }
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNueva() {
        Combos.opciones(cmbEquipo, equipoService.todos().stream().filter(e -> !e.isDeBaja()).toList());
        Combos.opciones(cmbTecnico, tecnicoService.activos());
        idEditando = null;
        limpiarFormulario();
        lblTituloFormulario.setText("Nuevo plan preventivo");
        lblSubtituloFormulario.setText("Define la frecuencia de mantenimiento");
        chkActivo.setSelected(true);
        txtFrecuencia.setText("30");
        dpProxima.setValue(LocalDate.now().plusDays(30));
        notificador.abrirPanel(panelFormulario);
        javafx.application.Platform.runLater(cmbEquipo::requestFocus);
    }

    @FXML
    private void onEditar() {
        PlanPreventivo plan = tabla.getSelectionModel().getSelectedItem();
        if (plan == null) {
            notificador.advertencia("Selecciona un plan de la tabla.");
            return;
        }
        Combos.opciones(cmbEquipo, equipoService.todos().stream().filter(e -> !e.isDeBaja()).toList());
        Combos.opciones(cmbTecnico, tecnicoService.activos());
        idEditando = plan.getId();
        lblTituloFormulario.setText("Editar plan");
        lblSubtituloFormulario.setText(plan.getEquipo().getCodigo() + " · " + plan.getEquipo().getNombre());
        cmbEquipo.setValue(plan.getEquipo());
        txtNombre.setText(plan.getNombre());
        txtFrecuencia.setText(String.valueOf(plan.getFrecuenciaDias()));
        txtHoras.setText(plan.getHorasEstimadas() == null ? "" : plan.getHorasEstimadas().toPlainString());
        dpProxima.setValue(plan.getProximaFecha());
        cmbTecnico.setValue(plan.getTecnico());
        chkActivo.setSelected(plan.isActivo());
        taObservaciones.setText(plan.getObservaciones());
        notificador.abrirPanel(panelFormulario);
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(cmbEquipo, txtNombre, txtFrecuencia, dpProxima);
        try {
            if (cmbEquipo.getValue() == null) {
                Formularios.marcarError(cmbEquipo, "Selecciona el equipo.");
                return;
            }
            if (txtFrecuencia.getText().isBlank()) {
                Formularios.marcarError(txtFrecuencia, "Indica la frecuencia en dias.");
                return;
            }
            if (dpProxima.getValue() == null) {
                Formularios.marcarError(dpProxima, "Indica la fecha proxima.");
                return;
            }

            PlanPreventivo plan = PlanPreventivo.builder()
                    .id(idEditando)
                    .equipo(cmbEquipo.getValue())
                    .nombre(txtNombre.getText())
                    .frecuenciaDias(Integer.parseInt(txtFrecuencia.getText()))
                    .horasEstimadas(Formularios.decimal(txtHoras))
                    .proximaFecha(dpProxima.getValue())
                    .tecnico(cmbTecnico.getValue())
                    .activo(chkActivo.isSelected())
                    .observaciones(taObservaciones.getText())
                    .build();

            preventivoService.guardar(plan);
            notificador.exito(idEditando == null ? "Plan preventivo creado."
                    : "Plan preventivo actualizado.");
            onCerrarFormulario();
            refrescar();
            cargar();
        } catch (ConstraintViolationException e) {
            Formularios.mostrarViolaciones(e, Map.of(
                    "equipo", cmbEquipo, "nombre", txtNombre,
                    "frecuenciaDias", txtFrecuencia, "proximaFecha", dpProxima))
                    .forEach(notificador::advertencia);
        } catch (NumberFormatException e) {
            Formularios.marcarError(txtFrecuencia, "Ingresa un numero valido.");
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onEliminar() {
        PlanPreventivo plan = tabla.getSelectionModel().getSelectedItem();
        if (plan == null) {
            return;
        }
        notificador.confirmar("Eliminar plan",
                "¿Eliminar el plan de " + plan.getEquipo().getNombre()
                        + "? Dejara de generarse automaticamente.",
                "Eliminar plan", () -> {
                    try {
                        preventivoService.eliminar(plan.getId());
                        notificador.exito("Plan eliminado.");
                        refrescar();
                        cargar();
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    /** Crea automaticamente las ordenes de los planes vencidos o de hoy.
     *  Corre como accion controlable: se puede pausar entre pasos o cancelar
     *  desde el dock sin reiniciar el flujo. */
    @FXML
    private void onGenerar() {
        if (agente.hayAccion()) {
            notificador.advertencia("Ya hay una accion en curso; cancelala o espera desde el dock.");
            return;
        }
        long vencidos = preventivoService.contarVencidos();
        if (vencidos == 0) {
            notificador.info("No hay planes vencidos: todo el preventivo esta al dia.");
            return;
        }
        int[] creadas = new int[1];
        agente.ejecutar("Generar ordenes preventivas", paso -> {
            paso.avanzar(com.mantenimiento.cmms.agente.TipoTraza.CONSULTA,
                    "Consultando planes vencidos",
                    vencidos + " planes con proxima fecha <= hoy",
                    "tabla plan_preventivo",
                    "proximaFecha <= hoy y activo = true");
            paso.avanzar(com.mantenimiento.cmms.agente.TipoTraza.DECISION,
                    "Validando que no haya duplicados",
                    "una orden por plan vencido, sin orden abierta equivalente",
                    "reglas de preventivo",
                    "los planes ya atendidos se descartan");
            creadas[0] = preventivoService.generarOrdenesPendientes();
            paso.avanzar(com.mantenimiento.cmms.agente.TipoTraza.RESULTADO,
                    "Ordenes preventivas generadas",
                    creadas[0] + " ordenes creadas y planes reprogramados",
                    "tabla orden_trabajo",
                    "resultado >= 0");
        }, res -> {
            if (res.cancelado()) {
                notificador.advertencia("Generacion cancelada: no se creo ninguna orden.");
                return;
            }
            if (res.fallo() != null) {
                Exception causa = res.fallo();
                notificador.fallar("PRE-001",
                        "No se pudieron generar las ordenes preventivas",
                        "Fallo interno: " + (causa.getMessage() == null
                                ? causa.getClass().getSimpleName() : causa.getMessage()),
                        "Reintenta desde este mismo boton; si persiste, copia el diagnostico y reportalo al soporte.",
                        this::onGenerar);
                return;
            }
            if (creadas[0] == 0) {
                notificador.info("No hay planes vencidos: todo el preventivo esta al dia.");
            } else {
                notificador.exito(creadas[0] == 1
                        ? "1 orden de mantenimiento preventivo generada."
                        : creadas[0] + " ordenes de mantenimiento preventivo generadas.");
            }
            refrescar();
            cargar();
        });
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
        idEditando = null;
    }

    private void limpiarFormulario() {
        cmbEquipo.setValue(null);
        txtNombre.clear();
        txtFrecuencia.clear();
        txtHoras.clear();
        dpProxima.setValue(null);
        cmbTecnico.setValue(null);
        chkActivo.setSelected(true);
        taObservaciones.clear();
        Formularios.limpiarErrores(cmbEquipo, txtNombre, txtFrecuencia, dpProxima);
    }
}
