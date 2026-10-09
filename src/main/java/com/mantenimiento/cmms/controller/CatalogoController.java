package com.mantenimiento.cmms.controller;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.CategoriaEquipo;
import com.mantenimiento.cmms.model.Proveedor;
import com.mantenimiento.cmms.model.Ubicacion;
import com.mantenimiento.cmms.service.CatalogoService;
import com.mantenimiento.cmms.service.SesionService;
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
 * Catalogos maestros en tres pestanas: categorias de equipo, ubicaciones
 * fisicas y proveedores. Un unico panel lateral adapta su formulario al tipo.
 */
@Component
@RequiredArgsConstructor
public class CatalogoController implements Refrescable {

    private enum Tipo { CATEGORIA, UBICACION, PROVEEDOR }

    private final CatalogoService catalogoService;
    private final SesionService sesion;
    private final Notificador notificador;

    @FXML private TabPane pestanas;
    @FXML private Tab tabCategorias;
    @FXML private Tab tabUbicaciones;
    @FXML private Tab tabProveedores;

    @FXML private Button btnNuevo;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;

    // ---------------------------------------------------------- categorias
    @FXML private TableView<CategoriaEquipo> tablaCategorias;
    @FXML private TableColumn<CategoriaEquipo, String> colCatNombre;
    @FXML private TableColumn<CategoriaEquipo, String> colCatDescripcion;
    @FXML private TableColumn<CategoriaEquipo, String> colCatColor;

    // --------------------------------------------------------- ubicaciones
    @FXML private TableView<Ubicacion> tablaUbicaciones;
    @FXML private TableColumn<Ubicacion, String> colUbiNombre;
    @FXML private TableColumn<Ubicacion, String> colUbiDescripcion;

    // --------------------------------------------------------- proveedores
    @FXML private TableView<Proveedor> tablaProveedores;
    @FXML private TableColumn<Proveedor, String> colProvNombre;
    @FXML private TableColumn<Proveedor, String> colProvRuc;
    @FXML private TableColumn<Proveedor, String> colProvTelefono;
    @FXML private TableColumn<Proveedor, String> colProvEmail;

    // ------------------------------------------------------------ formulario
    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private VBox formCategoria;
    @FXML private VBox formUbicacion;
    @FXML private VBox formProveedor;
    @FXML private TextField txtCatNombre;
    @FXML private TextField txtCatDescripcion;
    @FXML private TextField txtCatColor;
    @FXML private TextField txtUbiNombre;
    @FXML private TextField txtUbiDescripcion;
    @FXML private TextField txtProvNombre;
    @FXML private TextField txtProvRuc;
    @FXML private TextField txtProvTelefono;
    @FXML private TextField txtProvEmail;

    private Long idEditando;

    @FXML
    public void initialize() {
        // ---- categorias
        Tablas.configurar(tablaCategorias, Feather.TAG, "Sin categorias",
                "Agrupa tus equipos por naturaleza (electrico, mecanico…).", null);
        colCatNombre.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getNombre()));
        colCatDescripcion.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getDescripcion()));
        colCatDescripcion.setCellFactory(Tablas.celdaOpcional());
        colCatColor.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getColor()));
        colCatColor.setCellFactory(Tablas.celdaOpcional());

        // ---- ubicaciones
        Tablas.configurar(tablaUbicaciones, Feather.MAP_PIN, "Sin ubicaciones",
                "Define donde estan los equipos (planta, linea, salon).", null);
        colUbiNombre.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getNombre()));
        colUbiDescripcion.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getDescripcion()));
        colUbiDescripcion.setCellFactory(Tablas.celdaOpcional());

        // ---- proveedores
        Tablas.configurar(tablaProveedores, Feather.TRUCK, "Sin proveedores",
                "Quien te suministra repuestos y servicios.", null);
        colProvNombre.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getNombre()));
        colProvRuc.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getRuc()));
        colProvRuc.setCellFactory(Tablas.celdaOpcional());
        colProvTelefono.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getTelefono()));
        colProvTelefono.setCellFactory(Tablas.celdaOpcional());
        colProvEmail.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getEmail()));
        colProvEmail.setCellFactory(Tablas.celdaOpcional());

        // selecciones y cambio de pestana
        tablaCategorias.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> actualizarBotones());
        tablaUbicaciones.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> actualizarBotones());
        tablaProveedores.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> actualizarBotones());
        pestanas.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> actualizarBotones());

        Formularios.limpiarAlEditar(txtCatNombre, txtCatDescripcion, txtCatColor,
                txtUbiNombre, txtUbiDescripcion, txtProvNombre, txtProvRuc,
                txtProvTelefono, txtProvEmail);
    }

    @Override
    public void refrescar() {
        cargar();
    }

    private void cargar() {
        tablaCategorias.setItems(FXCollections.observableArrayList(catalogoService.categorias()));
        tablaUbicaciones.setItems(FXCollections.observableArrayList(catalogoService.ubicaciones()));
        tablaProveedores.setItems(FXCollections.observableArrayList(catalogoService.proveedores()));
        actualizarBotones();
    }

    private Tipo tipoActivo() {
        if (pestanas.getSelectionModel().getSelectedItem() == tabUbicaciones) {
            return Tipo.UBICACION;
        }
        if (pestanas.getSelectionModel().getSelectedItem() == tabProveedores) {
            return Tipo.PROVEEDOR;
        }
        return Tipo.CATEGORIA;
    }

    private Object seleccionActual() {
        return switch (tipoActivo()) {
            case CATEGORIA -> tablaCategorias.getSelectionModel().getSelectedItem();
            case UBICACION -> tablaUbicaciones.getSelectionModel().getSelectedItem();
            case PROVEEDOR -> tablaProveedores.getSelectionModel().getSelectedItem();
        };
    }

    private void actualizarBotones() {
        boolean hay = seleccionActual() != null;
        boolean gestiona = sesion.puedeGestionar();
        btnNuevo.setDisable(!gestiona);
        btnEditar.setDisable(!hay || !gestiona);
        btnEliminar.setDisable(!hay || !gestiona);
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNuevo() {
        idEditando = null;
        limpiarFormulario();
        Tipo tipo = tipoActivo();
        mostrarFormulario(tipo);
        switch (tipo) {
            case CATEGORIA -> {
                lblTituloFormulario.setText("Nueva categoria");
                lblSubtituloFormulario.setText("Agrupamiento natural de equipos");
                txtCatColor.setText("#2a78d6");
            }
            case UBICACION -> {
                lblTituloFormulario.setText("Nueva ubicacion");
                lblSubtituloFormulario.setText("Lugar fisico dentro de la planta");
            }
            case PROVEEDOR -> {
                lblTituloFormulario.setText("Nuevo proveedor");
                lblSubtituloFormulario.setText("Fuente de repuestos o servicios");
            }
        }
        notificador.abrirPanel(panelFormulario);
        javafx.application.Platform.runLater(() -> {
            if (tipo == Tipo.CATEGORIA) {
                txtCatNombre.requestFocus();
            } else if (tipo == Tipo.UBICACION) {
                txtUbiNombre.requestFocus();
            } else {
                txtProvNombre.requestFocus();
            }
        });
    }

    @FXML
    private void onEditar() {
        Tipo tipo = tipoActivo();
        Object seleccion = seleccionActual();
        if (seleccion == null) {
            notificador.advertencia("Selecciona un registro de la tabla.");
            return;
        }
        idEditando = idDe(seleccion, tipo);
        limpiarFormulario();
        mostrarFormulario(tipo);
        switch (tipo) {
            case CATEGORIA -> {
                CategoriaEquipo c = (CategoriaEquipo) seleccion;
                lblTituloFormulario.setText("Editar categoria");
                lblSubtituloFormulario.setText(c.getNombre());
                txtCatNombre.setText(c.getNombre());
                txtCatDescripcion.setText(c.getDescripcion());
                txtCatColor.setText(c.getColor());
            }
            case UBICACION -> {
                Ubicacion u = (Ubicacion) seleccion;
                lblTituloFormulario.setText("Editar ubicacion");
                lblSubtituloFormulario.setText(u.getNombre());
                txtUbiNombre.setText(u.getNombre());
                txtUbiDescripcion.setText(u.getDescripcion());
            }
            case PROVEEDOR -> {
                Proveedor p = (Proveedor) seleccion;
                lblTituloFormulario.setText("Editar proveedor");
                lblSubtituloFormulario.setText(p.getNombre());
                txtProvNombre.setText(p.getNombre());
                txtProvRuc.setText(p.getRuc());
                txtProvTelefono.setText(p.getTelefono());
                txtProvEmail.setText(p.getEmail());
            }
        }
        notificador.abrirPanel(panelFormulario);
    }

    private Long idDe(Object seleccion, Tipo tipo) {
        return switch (tipo) {
            case CATEGORIA -> ((CategoriaEquipo) seleccion).getId();
            case UBICACION -> ((Ubicacion) seleccion).getId();
            case PROVEEDOR -> ((Proveedor) seleccion).getId();
        };
    }

    private void mostrarFormulario(Tipo tipo) {
        formCategoria.setVisible(tipo == Tipo.CATEGORIA);
        formCategoria.setManaged(tipo == Tipo.CATEGORIA);
        formUbicacion.setVisible(tipo == Tipo.UBICACION);
        formUbicacion.setManaged(tipo == Tipo.UBICACION);
        formProveedor.setVisible(tipo == Tipo.PROVEEDOR);
        formProveedor.setManaged(tipo == Tipo.PROVEEDOR);
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtCatNombre, txtUbiNombre, txtProvNombre, txtProvRuc);
        try {
            switch (tipoActivo()) {
                case CATEGORIA -> {
                    if (txtCatNombre.getText().isBlank()) {
                        Formularios.marcarError(txtCatNombre, "El nombre es obligatorio.");
                        return;
                    }
                    CategoriaEquipo categoria = CategoriaEquipo.builder()
                            .id(idEditando)
                            .nombre(txtCatNombre.getText().trim())
                            .descripcion(txtCatDescripcion.getText())
                            .color(txtCatColor.getText().isBlank() ? null : txtCatColor.getText().trim())
                            .build();
                    catalogoService.guardarCategoria(categoria);
                    notificador.exito(idEditando == null ? "Categoria creada." : "Categoria actualizada.");
                }
                case UBICACION -> {
                    if (txtUbiNombre.getText().isBlank()) {
                        Formularios.marcarError(txtUbiNombre, "El nombre es obligatorio.");
                        return;
                    }
                    Ubicacion ubicacion = Ubicacion.builder()
                            .id(idEditando)
                            .nombre(txtUbiNombre.getText().trim())
                            .descripcion(txtUbiDescripcion.getText())
                            .build();
                    catalogoService.guardarUbicacion(ubicacion);
                    notificador.exito(idEditando == null ? "Ubicacion creada." : "Ubicacion actualizada.");
                }
                case PROVEEDOR -> {
                    if (txtProvNombre.getText().isBlank()) {
                        Formularios.marcarError(txtProvNombre, "El nombre es obligatorio.");
                        return;
                    }
                    String ruc = txtProvRuc.getText().trim();
                    if (!ruc.isBlank() && ruc.length() != 11) {
                        Formularios.marcarError(txtProvRuc, "El RUC debe tener 11 digitos.");
                        return;
                    }
                    Proveedor proveedor = Proveedor.builder()
                            .id(idEditando)
                            .nombre(txtProvNombre.getText().trim())
                            .ruc(ruc.isBlank() ? null : ruc)
                            .telefono(txtProvTelefono.getText())
                            .email(txtProvEmail.getText())
                            .build();
                    catalogoService.guardarProveedor(proveedor);
                    notificador.exito(idEditando == null ? "Proveedor creado." : "Proveedor actualizado.");
                }
            }
            onCerrarFormulario();
            cargar();
        } catch (ConstraintViolationException e) {
            Formularios.mostrarViolaciones(e, Map.of(
                    "nombre", switch (tipoActivo()) {
                        case CATEGORIA -> txtCatNombre;
                        case UBICACION -> txtUbiNombre;
                        case PROVEEDOR -> txtProvNombre;
                    },
                    "ruc", txtProvRuc))
                    .forEach(notificador::advertencia);
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    @FXML
    private void onEliminar() {
        Tipo tipo = tipoActivo();
        Object seleccion = seleccionActual();
        if (seleccion == null) {
            return;
        }
        String nombre = switch (tipo) {
            case CATEGORIA -> ((CategoriaEquipo) seleccion).getNombre();
            case UBICACION -> ((Ubicacion) seleccion).getNombre();
            case PROVEEDOR -> ((Proveedor) seleccion).getNombre();
        };
        String etiqueta = switch (tipo) {
            case CATEGORIA -> "categoria";
            case UBICACION -> "ubicacion";
            case PROVEEDOR -> "proveedor";
        };
        Long id = idDe(seleccion, tipo);

        notificador.confirmar("Eliminar " + etiqueta,
                "¿Eliminar \"" + nombre + "\"? Solo es posible si no tiene registros asociados.",
                "Eliminar " + etiqueta, () -> {
                    try {
                        switch (tipo) {
                            case CATEGORIA -> catalogoService.eliminarCategoria(id);
                            case UBICACION -> catalogoService.eliminarUbicacion(id);
                            case PROVEEDOR -> catalogoService.eliminarProveedor(id);
                        }
                        notificador.exito("Registro eliminado.");
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
        txtCatNombre.clear();
        txtCatDescripcion.clear();
        txtCatColor.clear();
        txtUbiNombre.clear();
        txtUbiDescripcion.clear();
        txtProvNombre.clear();
        txtProvRuc.clear();
        txtProvTelefono.clear();
        txtProvEmail.clear();
        Formularios.limpiarErrores(txtCatNombre, txtUbiNombre, txtProvNombre, txtProvRuc);
    }
}
