package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.Rol;
import com.mantenimiento.cmms.model.Usuario;
import com.mantenimiento.cmms.service.SesionService;
import com.mantenimiento.cmms.service.UsuarioService;
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

import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Cuentas de acceso: login, rol (permisos) y estado activo/inactivo. */
@Component
@RequiredArgsConstructor
public class UsuarioController implements Refrescable {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UsuarioService usuarioService;
    private final SesionService sesion;
    private final Notificador notificador;

    @FXML private Button btnNuevo;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Button btnEstado;

    @FXML private TextField txtBuscar;

    @FXML private TableView<Usuario> tabla;
    @FXML private TableColumn<Usuario, String> colUsuario;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colEmail;
    @FXML private TableColumn<Usuario, Usuario> colRol;
    @FXML private TableColumn<Usuario, String> colUltimoAcceso;
    @FXML private TableColumn<Usuario, Usuario> colEstado;
    @FXML private Paginador paginador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtUsuario;
    @FXML private TextField txtNombre;
    @FXML private TextField txtEmail;
    @FXML private ComboBox<Rol> cmbRol;
    @FXML private PasswordField pwdContrasena;
    @FXML private Label lblAyudaContrasena;
    @FXML private CheckBox chkActivo;

    private Long idEditando;

    @FXML
    public void initialize() {
        Tablas.configurar(tabla, Feather.LOCK, "Sin cuentas",
                "Crea los accesos al sistema con su rol.", this::onDobleClic);

        colUsuario.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getUsuario()));
        colNombre.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getNombre()));
        colEmail.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getEmail()));
        colEmail.setCellFactory(Tablas.celdaOpcional());
        colRol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colRol.setCellFactory(Tablas.celdaPastilla(
                u -> u.getRol().getEtiqueta(),
                u -> switch (u.getRol()) {
                    case ADMIN -> "peligro";
                    case SUPERVISOR -> "info";
                    case TECNICO -> "neutro";
                }));
        colUltimoAcceso.setCellValueFactory(d -> new SimpleObjectProperty<>(
                d.getValue().getUltimoAcceso() == null ? null
                        : d.getValue().getUltimoAcceso().format(FECHA_HORA)));
        colUltimoAcceso.setCellFactory(Tablas.celdaOpcional());
        colEstado.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colEstado.setCellFactory(Tablas.celdaPastilla(
                u -> u.isActivo() ? "Activo" : "Inactivo",
                u -> u.isActivo() ? "exito" : "peligro"));

        txtBuscar.textProperty().addListener((o, a, b) -> paginador.irAPrimera());
        paginador.setOnCambio(this::cargar);
        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, a, b) -> actualizarBotones());

        Combos.formato(cmbRol, r -> r == null ? "" : r.getEtiqueta());
        cmbRol.getItems().addAll(Rol.values());
        Formularios.limpiarAlEditar(txtUsuario, txtNombre, txtEmail);
    }

    @Override
    public void refrescar() {
        cargar();
    }

    private void cargar() {
        var pagina = usuarioService.listar(txtBuscar.getText(),
                paginador.getPagina(), paginador.getTamanio());
        tabla.setItems(FXCollections.observableArrayList(pagina.getContent()));
        paginador.actualizar(pagina);
        actualizarBotones();
    }

    private void actualizarBotones() {
        Usuario u = tabla.getSelectionModel().getSelectedItem();
        boolean hay = u != null;
        boolean soyYo = hay && sesion.getUsuario() != null
                && sesion.getUsuario().getId().equals(u.getId());
        btnNuevo.setDisable(false);
        btnEditar.setDisable(!hay || soyYo);
        btnEliminar.setDisable(!hay || soyYo);
        btnEstado.setDisable(!hay || soyYo);
        if (hay) {
            btnEstado.setText(u.isActivo() ? "Desactivar" : "Activar");
        } else {
            btnEstado.setText("Activar");
        }
    }

    private void onDobleClic(Usuario u) {
        onEditar();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNuevo() {
        idEditando = null;
        limpiarFormulario();
        lblTituloFormulario.setText("Nueva cuenta");
        lblSubtituloFormulario.setText("Define usuario, rol y contrasena");
        cmbRol.setValue(Rol.TECNICO);
        chkActivo.setSelected(true);
        notificador.abrirPanel(panelFormulario);
        javafx.application.Platform.runLater(txtUsuario::requestFocus);
    }

    @FXML
    private void onEditar() {
        Usuario usuario = tabla.getSelectionModel().getSelectedItem();
        if (usuario == null) {
            notificador.advertencia("Selecciona una cuenta de la tabla.");
            return;
        }
        idEditando = usuario.getId();
        lblTituloFormulario.setText("Editar " + usuario.getUsuario());
        lblSubtituloFormulario.setText("Rol: " + usuario.getRol().getEtiqueta());
        txtUsuario.setText(usuario.getUsuario());
        txtNombre.setText(usuario.getNombre());
        txtEmail.setText(usuario.getEmail());
        cmbRol.setValue(usuario.getRol());
        pwdContrasena.clear();
        lblAyudaContrasena.setText("Deja la contrasena vacia para mantener la actual.");
        chkActivo.setSelected(usuario.isActivo());
        notificador.abrirPanel(panelFormulario);
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtUsuario, txtNombre, txtEmail, pwdContrasena, cmbRol);
        try {
            if (txtUsuario.getText().isBlank()) {
                Formularios.marcarError(txtUsuario, "El usuario es obligatorio.");
                return;
            }
            if (txtNombre.getText().isBlank()) {
                Formularios.marcarError(txtNombre, "El nombre es obligatorio.");
                return;
            }
            if (cmbRol.getValue() == null) {
                Formularios.marcarError(cmbRol, "Selecciona el rol.");
                return;
            }

            Usuario usuario = Usuario.builder()
                    .id(idEditando)
                    .usuario(txtUsuario.getText().trim())
                    .nombre(txtNombre.getText().trim())
                    .email(txtEmail.getText())
                    .rol(cmbRol.getValue())
                    .activo(chkActivo.isSelected())
                    .build();

            Usuario guardado = usuarioService.guardar(usuario, pwdContrasena.getText());
            notificador.exito((idEditando == null ? "Cuenta " + guardado.getUsuario() + " creada."
                    : "Cuenta actualizada."));
            onCerrarFormulario();
            cargar();
        } catch (ConstraintViolationException e) {
            Formularios.mostrarViolaciones(e, Map.of(
                    "usuario", txtUsuario, "nombre", txtNombre, "email", txtEmail))
                    .forEach(notificador::advertencia);
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onEliminar() {
        Usuario usuario = tabla.getSelectionModel().getSelectedItem();
        if (usuario == null) {
            return;
        }
        notificador.confirmar("Eliminar cuenta",
                "¿Eliminar la cuenta \"" + usuario.getUsuario() + "\"? Esta accion no se puede deshacer.",
                "Eliminar cuenta", () -> {
                    try {
                        usuarioService.eliminar(usuario.getId());
                        notificador.exito("Cuenta eliminada.");
                        cargar();
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    /** Activa o desactiva el acceso sin borrar el historial. */
    @FXML
    private void onCambiarEstado() {
        Usuario usuario = tabla.getSelectionModel().getSelectedItem();
        if (usuario == null) {
            return;
        }
        boolean nuevo = !usuario.isActivo();
        notificador.confirmar(nuevo ? "Activar cuenta" : "Desactivar cuenta",
                (nuevo ? "¿Activar " : "¿Desactivar ") + "\"" + usuario.getUsuario() + "\""
                        + (nuevo ? "?" : "? El usuario dejara de poder iniciar sesion."),
                nuevo ? "Activar" : "Desactivar", () -> {
                    try {
                        usuarioService.cambiarEstado(usuario.getId(), nuevo);
                        notificador.exito(nuevo ? "Cuenta activada." : "Cuenta desactivada.");
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
        txtUsuario.clear();
        txtNombre.clear();
        txtEmail.clear();
        cmbRol.setValue(null);
        pwdContrasena.clear();
        chkActivo.setSelected(true);
        lblAyudaContrasena.setText("Minimo 6 caracteres. Se guarda cifrada (SHA-256 con sal).");
        Formularios.limpiarErrores(txtUsuario, txtNombre, txtEmail, pwdContrasena, cmbRol);
    }
}
