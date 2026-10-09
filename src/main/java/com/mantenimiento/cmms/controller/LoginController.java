package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.agente.Trazador;
import com.mantenimiento.cmms.exception.Diagnostico;
import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.Usuario;
import com.mantenimiento.cmms.service.SesionService;
import com.mantenimiento.cmms.service.UsuarioService;
import com.mantenimiento.cmms.ui.Formularios;
import atlantafx.base.util.Animations;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pantalla de acceso: valida credenciales sin congelar la interfaz y
 * <b>comunica siempre su estado</b> ("Verificando credenciales (hash
 * SHA-256 + sal)…"). Un fallo llega estructurado: que paso, por que y que
 * hacer, con codigo de diagnostico. Las contrasenas nunca viajan en claro:
 * {@code UsuarioService} compara hashes.
 */
@Component
@RequiredArgsConstructor
public class LoginController {

    private final UsuarioService usuarioService;
    private final SesionService sesion;
    private final Trazador trazador;

    @FXML private VBox tarjeta;
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtContrasena;
    @FXML private Label lblError;
    @FXML private Label lblEstado;
    @FXML private Button btnIngresar;

    private Runnable alIngresar;
    private Stage stage;
    private boolean verificando;

    /** El {@code JavaFxApplication} entrega aqui el callback para abrir el panel. */
    public void configurar(Runnable alIngresar, Stage stage) {
        this.alIngresar = alIngresar;
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        lblEstado.setVisible(false);
        lblEstado.setManaged(false);
        Formularios.limpiarAlEditar(txtUsuario, txtContrasena);

        // Enter en cualquier campo dispara el ingreso.
        for (javafx.scene.Node nodo : new javafx.scene.Node[]{txtUsuario, txtContrasena}) {
            nodo.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    onIngresar();
                    e.consume();
                }
            });
        }
        Platform.runLater(txtUsuario::requestFocus);
    }

    @FXML
    private void onIngresar() {
        if (verificando) {
            return;
        }
        ocultarError();
        verificando = true;
        btnIngresar.setDisable(true);
        btnIngresar.setText("Verificando…");
        mostrarEstado("Verificando credenciales (hash SHA-256 + sal) contra la tabla usuario…");
        trazador.consulta("usuarios y permisos",
                "LoginController.onIngresar()",
                "usuario \"" + txtUsuario.getText() + "\" · clave oculta por seguridad", 0);

        String usuarioTxt = txtUsuario.getText();
        String clave = txtContrasena.getText();

        Task<Usuario> tarea = new Task<>() {
            @Override
            protected Usuario call() {
                Usuario u = usuarioService.autenticar(usuarioTxt, clave);
                usuarioService.registrarAcceso(u);
                return u;
            }
        };
        tarea.setOnSucceeded(ev -> {
            Usuario usuario = tarea.getValue();
            trazador.decision("Sesion iniciada: " + usuario.getNombre(),
                    "el hash calculado coincide con la cuenta activa",
                    "rol " + usuario.getRol().getEtiqueta());
            terminarIntento();
            sesion.iniciar(usuario);
            if (alIngresar != null) {
                alIngresar.run();
            }
        });
        tarea.setOnFailed(ev -> {
            Throwable causa = tarea.getException();
            terminarIntento();
            if (causa instanceof NegocioException e) {
                Diagnostico.Estructura f = Diagnostico.de(e);
                trazador.error("Inicio de sesion rechazado",
                        f.codigo() + ": " + f.porque(), "usuarios y permisos");
                mostrarError("[" + f.codigo() + "] " + f.quePaso()
                        + " — " + f.accion());
                txtContrasena.clear();
                Platform.runLater(txtContrasena::requestFocus);
            } else {
                trazador.error("Fallo tecnico al iniciar sesion",
                        causa == null ? "desconocido" : causa.getMessage(), "base de datos");
                mostrarError("No se pudo iniciar sesion: "
                        + (causa == null ? "error interno" : causa.getMessage())
                        + ". Reintenta; si persiste, avisa al administrador.");
            }
        });
        Thread hilo = new Thread(tarea, "login");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void terminarIntento() {
        verificando = false;
        btnIngresar.setDisable(false);
        btnIngresar.setText("Ingresar al sistema");
        ocultarEstado();
    }

    private void mostrarEstado(String texto) {
        lblEstado.setText(texto);
        lblEstado.setVisible(true);
        lblEstado.setManaged(true);
        Animations.fadeInDown(lblEstado, Duration.millis(180)).play();
    }

    private void ocultarEstado() {
        lblEstado.setVisible(false);
        lblEstado.setManaged(false);
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
        Animations.fadeInDown(lblError, Duration.millis(200)).play();
        Animations.shakeX(tarjeta, 6).play();
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    /** Cierra la ventana (boton "Salir" del login). */
    @FXML
    private void onSalir() {
        if (stage != null) {
            stage.close();
        } else {
            javafx.application.Platform.exit();
        }
    }
}
