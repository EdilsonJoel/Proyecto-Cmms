package com.mantenimiento.cmms.ui;

import com.mantenimiento.cmms.model.CategoriaEquipo;
import com.mantenimiento.cmms.model.Equipo;
import com.mantenimiento.cmms.model.PlanPreventivo;
import com.mantenimiento.cmms.model.Proveedor;
import com.mantenimiento.cmms.model.Repuesto;
import com.mantenimiento.cmms.model.Tecnico;
import com.mantenimiento.cmms.model.Ubicacion;
import com.mantenimiento.cmms.model.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Function;

/**
 * Formato legible de las entidades dentro de los ComboBox
 * ("E-01 · Compresor de aire" en lugar del toString del objeto).
 */
public final class Combos {

    private Combos() {
    }

    /** Aplica un formato de texto al desplegar y al seleccionar. */
    public static <T> void formato(ComboBox<T> combo, Function<T, String> formato) {
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(T objeto) {
                return objeto == null ? "" : formato.apply(objeto);
            }

            @Override
            public T fromString(String texto) {
                return null;
            }
        });
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio || item == null ? null : formato.apply(item));
            }
        });
    }

    /** Reemplaza las opciones conservando la seleccion si el objeto sigue presente. */
    public static <T> void opciones(ComboBox<T> combo, List<T> elementos) {
        T seleccion = combo.getValue();
        ObservableList<T> lista = FXCollections.observableArrayList(elementos);
        combo.setItems(lista);
        if (seleccion != null && lista.stream().noneMatch(e -> e == seleccion)) {
            combo.setValue(null);
        }
    }

    public static void equipos(ComboBox<Equipo> combo) {
        formato(combo, e -> e.getCodigo() + "  ·  " + e.getNombre());
    }

    public static void tecnicos(ComboBox<Tecnico> combo) {
        formato(combo, t -> t == null ? "" : t.getNombre() + "  (" + t.getEspecialidad() + ")");
    }

    public static void repuestos(ComboBox<Repuesto> combo) {
        formato(combo, r -> r.getCodigo() + "  ·  " + r.getNombre()
                + "  ·  stock " + r.getStock() + " " + r.getUnidad());
    }

    public static void categorias(ComboBox<CategoriaEquipo> combo) {
        formato(combo, c -> c.getNombre());
    }

    public static void ubicaciones(ComboBox<Ubicacion> combo) {
        formato(combo, u -> u.getNombre());
    }

    public static void proveedores(ComboBox<Proveedor> combo) {
        formato(combo, p -> p.getNombre());
    }

    public static void usuarios(ComboBox<Usuario> combo) {
        formato(combo, u -> u == null ? "" : u.getUsuario() + "  ·  " + u.getNombre());
    }

    public static void planes(ComboBox<PlanPreventivo> combo) {
        formato(combo, p -> p.getEquipo().getCodigo() + "  ·  " + p.getNombre());
    }
}
