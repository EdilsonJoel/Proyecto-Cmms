package com.mantenimiento.cmms;

import atlantafx.base.theme.PrimerLight;
import com.mantenimiento.cmms.controller.LoginController;
import com.mantenimiento.cmms.ui.Exportador;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Objects;

/**
 * Arranca Spring (sin servidor web) y administra las dos escenas de la aplicacion:
 * primero el acceso con usuario y contrasena; una vez autenticado, el panel principal.
 */
public class JavaFxApplication extends Application {

    /** Referencia para que el panel pueda cerrar sesion y volver al login. */
    private static JavaFxApplication instancia;

    private ConfigurableApplicationContext context;

    public static JavaFxApplication get() {
        return instancia;
    }

    public JavaFxApplication() {
        instancia = this;
    }

    @Override
    public void init() {
        this.context = new SpringApplicationBuilder(CmmsApplication.class)
                .web(WebApplicationType.NONE)
                .run();
    }

    @Override
    public void start(Stage stage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        // Icono propio en la ventana, la barra de tareas y el alt-tab.
        try (var icono = getClass().getResourceAsStream("/img/logo.png")) {
            stage.getIcons().add(new Image(Objects.requireNonNull(icono, "falta /img/logo.png")));
        }
        // Permite abrir los PDF y Excel generados con la aplicacion del sistema.
        context.getBean(Exportador.class).setHostServices(getHostServices());
        mostrarLogin(stage);
    }

    /** Escena de acceso: usuario y contrasena antes de entrar al panel. */
    private void mostrarLogin(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login-view.fxml"));
        loader.setControllerFactory(context::getBean);
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(hojaEstilos());

        stage.setTitle("ManteniMax | Control de mantenimiento");
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(700);
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();

        loader.<LoginController>getController().configurar(() -> {
            try {
                mostrarPanel(stage);
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo abrir el panel principal", e);
            }
        }, stage);
    }

    /** Escena principal: barra de mando, pildoras de navegacion, vistas, dashboard y reportes. */
    private void mostrarPanel(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
        loader.setControllerFactory(context::getBean);
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(hojaEstilos());

        stage.setTitle("ManteniMax | Sistema de gestion de mantenimiento");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.centerOnScreen();
    }

    /** Vuelve a la pantalla de acceso (cerrar sesion). */
    public void cerrarSesion(Stage stage) {
        try {
            mostrarLogin(stage);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo volver al inicio de sesion", e);
        }
    }

    private String hojaEstilos() {
        return Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm();
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
