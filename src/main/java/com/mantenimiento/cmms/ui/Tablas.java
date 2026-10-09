package com.mantenimiento.cmms.ui;

import com.mantenimiento.cmms.model.Repuesto;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Configuracion visual comun de las tablas de las vistas CRUD. */
public final class Tablas {

    public static final Locale LOCALE = Locale.forLanguageTag("es-PE");
    public static final NumberFormat MONEDA = NumberFormat.getCurrencyInstance(LOCALE);

    private Tablas() {
    }

    /** Estilo base, columnas ajustadas al ancho, estado vacio y doble clic para editar. */
    public static <T> void configurar(TableView<T> tabla, String textoVacio, Consumer<T> alDobleClic) {
        configurar(tabla, Feather.INBOX, "Sin resultados", textoVacio, alDobleClic);
    }

    /** Igual que {@link #configurar(TableView, String, Consumer)} con icono y titulo propios para el estado vacio. */
    public static <T> void configurar(TableView<T> tabla, Ikon iconoVacio, String tituloVacio, String textoVacio,
                                      Consumer<T> alDobleClic) {
        tabla.getStyleClass().add("tabla");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        FontIcon icono = new FontIcon(iconoVacio);
        icono.getStyleClass().add("vacio-icono");
        Label titulo = new Label(tituloVacio);
        titulo.getStyleClass().add("vacio-titulo");
        Label detalle = new Label(textoVacio);
        detalle.getStyleClass().add("vacio-detalle");
        VBox vacio = new VBox(8, icono, titulo, detalle);
        vacio.setAlignment(Pos.CENTER);
        tabla.setPlaceholder(vacio);

        tabla.setRowFactory(tv -> {
            TableRow<T> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    alDobleClic.accept(fila.getItem());
                }
            });
            return fila;
        });
    }

    /** Celda "#12" en gris. */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaId() {
        return col -> new TableCell<>() {
            {
                getStyleClass().add("celda-id");
            }

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : "#" + valor);
            }
        };
    }

    /** Celda con avatar de iniciales seguido del texto. */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaAvatar() {
        return col -> new TableCell<>() {
            private final Label iniciales = new Label();
            private final StackPane avatar = new StackPane(iniciales);
            private final Label nombre = new Label();
            private final HBox caja = new HBox(10, avatar, nombre);

            {
                avatar.getStyleClass().add("avatar");
                caja.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio || valor == null) {
                    setGraphic(null);
                    return;
                }
                iniciales.setText(iniciales(valor));
                nombre.setText(valor);
                setGraphic(caja);
            }
        };
    }

    /**
     * Cache de fotos para miniaturas de tabla: se carga una sola vez por URL
     * (carga asincrona) y se reutiliza en todas las filas.
     */
    private static final Map<String, Image> CACHE_FOTOS = new ConcurrentHashMap<>();

    /** Devuelve la imagen de la URL (classpath o absoluta) usando cache compartida. */
    public static Image foto(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return CACHE_FOTOS.computeIfAbsent(url, u -> new Image(u, true));
    }

    /** URL externa de un recurso de classpath, o {@code null} si no existe. */
    public static String recurso(String ruta) {
        var url = Tablas.class.getResource(ruta);
        return url == null ? null : url.toExternalForm();
    }

    /**
     * Celda con miniatura redondeada (foto) seguida del texto. La columna debe
     * ser de tipo entidad ({@code new SimpleObjectProperty<>(e)} en el
     * cellValueFactory); si la ruta resuelve a {@code null} se muestra un
     * avatar de iniciales como alternativa.
     */
    public static <T> Callback<TableColumn<T, T>, TableCell<T, T>> celdaMiniatura(
            Function<T, String> rutaFoto, Function<T, String> texto) {
        return col -> new TableCell<>() {
            private final ImageView foto = new ImageView();
            private final Label iniciales = new Label();
            private final StackPane avatar = new StackPane(iniciales);
            private final Label nombre = new Label();
            private final HBox caja = new HBox(10);

            {
                foto.setFitWidth(44);
                foto.setFitHeight(32);
                foto.setPreserveRatio(false);
                foto.setSmooth(true);
                Rectangle clip = new Rectangle(44, 32);
                clip.setArcWidth(10);
                clip.setArcHeight(10);
                foto.setClip(clip);
                avatar.getStyleClass().addAll("avatar", "avatar-mini");
                avatar.setMinSize(44, 32);
                avatar.setMaxSize(44, 32);
                avatar.setPrefSize(44, 32);
                caja.setAlignment(Pos.CENTER_LEFT);
                caja.setSpacing(10);
            }

            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                if (vacio || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                nombre.setText(texto.apply(item));
                Image imagen = foto(rutaFoto.apply(item));
                if (imagen != null) {
                    foto.setImage(imagen);
                    caja.getChildren().setAll(foto, nombre);
                } else {
                    iniciales.setText(iniciales(texto.apply(item)));
                    caja.getChildren().setAll(avatar, nombre);
                }
                setGraphic(caja);
            }
        };
    }

    /**
     * Pastilla de color generica: la columna debe ser de tipo entidad
     * ({@code new SimpleObjectProperty<>(c.getValue())} en el cellValueFactory).
     */
    public static <T> Callback<TableColumn<T, T>, TableCell<T, T>> celdaPastilla(
            Function<T, String> texto, Function<T, String> clase) {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                if (vacio || item == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(texto.apply(item));
                chip.getStyleClass().setAll("chip", clase.apply(item));
                setGraphic(chip);
            }
        };
    }

    /** Celda con el texto dentro de una "pastilla" de color (valor String simple). */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaChip(String textoVacio) {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio) {
                    setGraphic(null);
                    return;
                }
                boolean sinValor = valor == null || valor.isBlank();
                chip.setText(sinValor ? textoVacio : valor);
                chip.getStyleClass().setAll("chip");
                if (sinValor) {
                    chip.getStyleClass().add("neutro");
                }
                setGraphic(chip);
            }
        };
    }

    /** Celda de importe alineada a la derecha. */
    public static <T> Callback<TableColumn<T, BigDecimal>, TableCell<T, BigDecimal>> celdaMoneda() {
        return col -> new TableCell<>() {
            {
                getStyleClass().add("celda-importe");
            }

            @Override
            protected void updateItem(BigDecimal valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : MONEDA.format(valor));
            }
        };
    }

    /** Texto gris para valores opcionales vacios. */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaOpcional() {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                boolean sinValor = !vacio && (valor == null || valor.isBlank());
                setText(vacio ? null : sinValor ? "—" : valor);
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("sin-valor"), sinValor);
            }
        };
    }

    /** Stock del repuesto en pastilla: roja sin stock, ambar en el minimo, verde si alcanza. */
    public static Callback<TableColumn<Repuesto, Repuesto>, TableCell<Repuesto, Repuesto>> celdaStockRepuesto() {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(Repuesto r, boolean vacio) {
                super.updateItem(r, vacio);
                if (vacio || r == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(r.isSinStock() ? "Sin stock" : r.getStock() + " " + r.getUnidad());
                chip.getStyleClass().setAll("chip",
                        r.isSinStock() ? "peligro" : r.isStockBajo() ? "advertencia" : "exito");
                setGraphic(chip);
            }
        };
    }

    /** Hasta dos iniciales en mayuscula: "Maria Quispe Rojas" → "MQ". */
    public static String iniciales(String texto) {
        String[] partes = texto.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length && sb.length() < 2; i++) {
            if (!partes[i].isEmpty()) {
                sb.append(Character.toUpperCase(partes[i].charAt(0)));
            }
        }
        return sb.toString();
    }
}
