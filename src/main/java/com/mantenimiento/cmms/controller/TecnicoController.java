package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.Tecnico;
import com.mantenimiento.cmms.model.Usuario;
import com.mantenimiento.cmms.service.SesionService;
import com.mantenimiento.cmms.service.TecnicoService;
import com.mantenimiento.cmms.service.UsuarioService;
import com.mantenimiento.cmms.ui.*;
import jakarta.validation.ConstraintViolationException;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ficha de tecnicos: especialidad, contacto, carga de trabajo y vinculo con
 * una cuenta de usuario (para que el tecnico inicie sesion y vea "sus" ordenes).
 */
@Component
@RequiredArgsConstructor
public class TecnicoController implements Refrescable {

    private final TecnicoService tecnicoService;
    private final UsuarioService usuarioService;
    private final SesionService sesion;
    private final Notificador notificador;

    @FXML private Button btnNuevo;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;

    @FXML private TextField txtBuscar;
    @FXML private CheckBox chkSoloActivos;

    @FXML private TableView<Tecnico> tabla;
    @FXML private TableColumn<Tecnico, String> colDni;
    @FXML private TableColumn<Tecnico, String> colNombre;
    @FXML private TableColumn<Tecnico, String> colEspecialidad;
    @FXML private TableColumn<Tecnico, String> colContacto;
    @FXML private TableColumn<Tecnico, Tecnico> colEstado;
    @FXML private TableColumn<Tecnico, String> colCuenta;
    @FXML private Paginador paginador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtDni;
    @FXML private TextField txtNombre;
    @FXML private TextField txtEspecialidad;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtEmail;
    @FXML private ComboBox<Usuario> cmbUsuario;
    @FXML private CheckBox chkActivo;
    @FXML private Label lblCarga;

    private Long idEditando;

    @FXML
    public void initialize() {
        Tablas.configurar(tabla, Feather.USERS, "Sin tecnicos",
                "Registra al personal de mantenimiento.", this::onDobleClic);

        colDni.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getDni()));
        colNombre.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getNombre()));
        colNombre.setCellFactory(Tablas.celdaAvatar());
        colEspecialidad.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getEspecialidad()));
        colContacto.setCellValueFactory(d -> new SimpleObjectProperty<>(contacto(d.getValue())));
        colContacto.setCellFactory(Tablas.celdaOpcional());
        colEstado.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colEstado.setCellFactory(Tablas.celdaPastilla(
                t -> t.isActivo() ? "Activo" : "Inactivo",
                t -> t.isActivo() ? "exito" : "neutro"));
        colCuenta.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getUsuario() == null
                ? null : d.getValue().getUsuario().getUsuario() + " (" + d.getValue().getUsuario().getRol().getEtiqueta() + ")"));
        colCuenta.setCellFactory(Tablas.celdaOpcional());

        txtBuscar.textProperty().addListener((o, a, b) -> paginador.irAPrimera());
        chkSoloActivos.selectedProperty().addListener((o, a, b) -> paginador.irAPrimera());
        paginador.setOnCambio(this::cargar);
        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, a, b) -> actualizarBotones());

        Combos.usuarios(cmbUsuario);
        Formularios.limpiarAlEditar(txtDni, txtNombre, txtEspecialidad, txtTelefono, txtEmail);
    }

    @Override
    public void refrescar() {
        cargar();
    }

    private String contacto(Tecnico t) {
        StringBuilder sb = new StringBuilder();
        if (t.getTelefono() != null && !t.getTelefono().isBlank()) {
            sb.append(t.getTelefono());
        }
        if (t.getEmail() != null && !t.getEmail().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("  ·  ");
            }
            sb.append(t.getEmail());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void cargar() {
        var pagina = tecnicoService.listar(txtBuscar.getText(),
                chkSoloActivos.isSelected(), paginador.getPagina(), paginador.getTamanio());
        tabla.setItems(FXCollections.observableArrayList(pagina.getContent()));
        paginador.actualizar(pagina);
        actualizarBotones();
    }

    private void actualizarBotones() {
        boolean hay = tabla.getSelectionModel().getSelectedItem() != null;
        boolean gestiona = sesion.puedeGestionar();
        btnNuevo.setDisable(!gestiona);
        btnEditar.setDisable(!hay || !gestiona);
        btnEliminar.setDisable(!hay || !gestiona);
    }

    private void onDobleClic(Tecnico t) {
        if (sesion.puedeGestionar()) {
            onEditar();
        }
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNuevo() {
        cargarUsuarios();
        idEditando = null;
        limpiarFormulario();
        lblTituloFormulario.setText("Nuevo tecnico");
        lblSubtituloFormulario.setText("Personal de mantenimiento");
        chkActivo.setSelected(true);
        notificador.abrirPanel(panelFormulario);
        javafx.application.Platform.runLater(txtDni::requestFocus);
    }

    @FXML
    private void onEditar() {
        Tecnico tecnico = tabla.getSelectionModel().getSelectedItem();
        if (tecnico == null) {
            notificador.advertencia("Selecciona un tecnico de la tabla.");
            return;
        }
        cargarUsuarios();
        idEditando = tecnico.getId();
        lblTituloFormulario.setText("Editar " + tecnico.getNombre());
        lblSubtituloFormulario.setText("DNI " + tecnico.getDni());
        txtDni.setText(tecnico.getDni());
        txtNombre.setText(tecnico.getNombre());
        txtEspecialidad.setText(tecnico.getEspecialidad());
        txtTelefono.setText(tecnico.getTelefono());
        txtEmail.setText(tecnico.getEmail());
        cmbUsuario.setValue(tecnico.getUsuario());
        chkActivo.setSelected(tecnico.isActivo());
        lblCarga.setVisible(true);
        lblCarga.setManaged(true);
        long activas = tecnicoService.cargaActiva(tecnico.getId());
        lblCarga.setText("Carga actual: " + activas + (activas == 1 ? " orden activa" : " ordenes activas")
                + "  ·  " + tecnicoService.totalCompletadas(tecnico.getId()) + " completadas");
        notificador.abrirPanel(panelFormulario);
    }

    private void cargarUsuarios() {
        // cuentas activas disponibles para vincular (admite ninguna)
        java.util.List<Usuario> cuentas = usuarioService.todos().stream()
                .filter(Usuario::isActivo)
                .toList();
        Combos.opciones(cmbUsuario, cuentas);
        cmbUsuario.getItems().add(0, null);
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtDni, txtNombre, txtEspecialidad, txtEmail);
        try {
            if (txtDni.getText().isBlank() || txtDni.getText().trim().length() != 8) {
                Formularios.marcarError(txtDni, "El DNI debe tener 8 digitos.");
                return;
            }
            if (txtNombre.getText().isBlank()) {
                Formularios.marcarError(txtNombre, "El nombre es obligatorio.");
                return;
            }
            if (txtEspecialidad.getText().isBlank()) {
                Formularios.marcarError(txtEspecialidad, "Indica la especialidad.");
                return;
            }

            Tecnico tecnico = Tecnico.builder()
                    .id(idEditando)
                    .dni(txtDni.getText().trim())
                    .nombre(txtNombre.getText().trim())
                    .especialidad(txtEspecialidad.getText().trim())
                    .telefono(txtTelefono.getText())
                    .email(txtEmail.getText())
                    .usuario(cmbUsuario.getValue())
                    .activo(chkActivo.isSelected())
                    .build();

            Tecnico guardado = tecnicoService.guardar(tecnico);
            notificador.exito((idEditando == null ? "Tecnico " + guardado.getNombre() + " registrado."
                    : "Tecnico actualizado."));
            onCerrarFormulario();
            cargar();
        } catch (ConstraintViolationException e) {
            Formularios.mostrarViolaciones(e, Map.of(
                    "dni", txtDni, "nombre", txtNombre, "especialidad", txtEspecialidad,
                    "email", txtEmail))
                    .forEach(notificador::advertencia);
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onEliminar() {
        Tecnico tecnico = tabla.getSelectionModel().getSelectedItem();
        if (tecnico == null) {
            return;
        }
        notificador.confirmar("Eliminar tecnico",
                "¿Eliminar la ficha de " + tecnico.getNombre() + "? Solo es posible si no tiene ordenes.",
                "Eliminar tecnico", () -> {
                    try {
                        tecnicoService.eliminar(tecnico.getId());
                        notificador.exito("Tecnico eliminado.");
                        cargar();
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
        idEditando = null;
    }

    private void limpiarFormulario() {
        txtDni.clear();
        txtNombre.clear();
        txtEspecialidad.clear();
        txtTelefono.clear();
        txtEmail.clear();
        cmbUsuario.setValue(null);
        chkActivo.setSelected(true);
        lblCarga.setVisible(false);
        lblCarga.setManaged(false);
        Formularios.limpiarErrores(txtDni, txtNombre, txtEspecialidad, txtEmail);
    }
}
