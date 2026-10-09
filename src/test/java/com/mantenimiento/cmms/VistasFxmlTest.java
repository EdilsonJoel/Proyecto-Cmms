package com.mantenimiento.cmms;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prueba de humo de la interfaz: arranca el toolkit de JavaFX y carga cada
 * vista FXML con la fabrica de controladores de Spring, de modo que cualquier
 * error de inyeccion ({@code fx:id} mal escritos, campos inexistentes o
 * {@code onAction} sin metodo) salga aqui y no al correr la aplicacion.
 */
class VistasFxmlTest extends IntegracionBase {

    private static final List<String> VISTAS = List.of(
            "login-view",
            "main-view",
            "dashboard-view",
            "orden-view",
            "preventivo-view",
            "equipo-view",
            "repuesto-view",
            "catalogo-view",
            "tecnico-view",
            "reportes-view",
            "usuario-view");

    @Autowired
    private ApplicationContext aplicacion;

    @BeforeAll
    static void arrancarJavaFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException e) {
            // el toolkit ya estaba iniciado por otra prueba
        }
        Platform.setImplicitExit(false);
    }

    @Test
    @DisplayName("Todas las vistas FXML se cargan con sus controladores de Spring")
    void todasLasVistasSeCargan() throws Exception {
        for (String vista : VISTAS) {
            FXMLLoader cargador = new FXMLLoader(
                    getClass().getResource("/view/" + vista + ".fxml"));
            cargador.setControllerFactory(aplicacion::getBean);
            Parent raiz = cargador.load();

            assertNotNull(raiz, "La vista " + vista + " no genero contenido");
            assertNotNull(cargador.getController(),
                    "La vista " + vista + " no instancio su controlador");
        }
    }
}
