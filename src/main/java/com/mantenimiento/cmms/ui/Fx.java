package com.mantenimiento.cmms.ui;

import javafx.application.Platform;

/**
 * Ejecuta un {@link Runnable} en el hilo de JavaFX si el toolkit esta
 * iniciado; si no (o si ya estamos en ese hilo) lo ejecuta directo.
 * Asi la bitacora y el dock funcionan igual en la aplicacion y en las
 * pruebas unitarias, sin lanzar "Toolkit not initialized".
 */
public final class Fx {

    private Fx() {
    }

    public static void ejecutar(Runnable accion) {
        boolean enFx = false;
        try {
            enFx = Platform.isFxApplicationThread();
        } catch (Throwable ignorada) {
            // toolkit sin iniciar
        }
        if (enFx) {
            accion.run();
            return;
        }
        try {
            Platform.runLater(accion);
        } catch (Throwable noIniciada) {
            accion.run();
        }
    }
}
