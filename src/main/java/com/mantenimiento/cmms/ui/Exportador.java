package com.mantenimiento.cmms.ui;

import com.mantenimiento.cmms.agente.AgenteTareas;
import com.mantenimiento.cmms.agente.TipoTraza;
import javafx.application.HostServices;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Genera archivos (PDF, Excel) como <b>accion controlable</b> del agente:
 * el dock inferior muestra cada paso, se puede pausar antes de escribir y
 * cancelar sin romper nada. Un fallo llega estructurado (que paso, por que,
 * que hacer) con boton de reintento, y el archivo se abre con la aplicacion
 * predeterminada del sistema desde donde se puede imprimir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Exportador {

    @FunctionalInterface
    public interface Generador {
        void generar(File destino) throws Exception;
    }

    public enum Formato {
        PDF("Documento PDF", "*.pdf", ".pdf"),
        EXCEL("Libro de Excel", "*.xlsx", ".xlsx");

        final String descripcion;
        final String patron;
        final String extension;

        Formato(String descripcion, String patron, String extension) {
            this.descripcion = descripcion;
            this.patron = patron;
            this.extension = extension;
        }
    }

    private final Notificador notificador;
    private final AgenteTareas agente;
    private HostServices hostServices;
    private File ultimaCarpeta = new File(System.getProperty("user.home"));

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    /** Pide al usuario donde guardar, genera el archivo y lo abre al terminar. */
    public void guardar(Window ventana, String titulo, String nombreSugerido, Formato formato, Generador generador) {
        FileChooser selector = new FileChooser();
        selector.setTitle(titulo);
        selector.setInitialFileName(nombreSugerido + formato.extension);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(formato.descripcion, formato.patron));
        if (ultimaCarpeta.isDirectory()) {
            selector.setInitialDirectory(ultimaCarpeta);
        }
        File elegido = selector.showSaveDialog(ventana);
        if (elegido == null) {
            return;
        }
        File destino = elegido.getName().toLowerCase().endsWith(formato.extension)
                ? elegido
                : new File(elegido.getParentFile(), elegido.getName() + formato.extension);
        ultimaCarpeta = destino.getParentFile();

        ejecutar(destino, formato, generador, () -> {
            notificador.exito("Archivo guardado: " + destino.getName());
            abrir(destino);
        });
    }

    /** Genera el archivo en una carpeta temporal y lo abre para verlo o imprimirlo. */
    public void abrirTemporal(String nombre, Formato formato, Generador generador) {
        File destino;
        try {
            File carpeta = Files.createTempDirectory("mantenimiento").toFile();
            carpeta.deleteOnExit();
            destino = new File(carpeta, nombre + formato.extension);
            destino.deleteOnExit();
        } catch (IOException e) {
            notificador.fallar("EXP-002", "No se pudo preparar la carpeta temporal del archivo",
                    "El sistema no pudo crear ni escribir en su carpeta temporal: " + e.getMessage(),
                    "Revisa los permisos del disco o cierra la aplicacion si esta en una carpeta bloqueada.",
                    null);
            return;
        }
        ejecutar(destino, formato, generador, () -> {
            notificador.info("Abriendo " + destino.getName() + "…");
            abrir(destino);
        });
    }

    // ------------------------------------------------------------ ejecucion

    private void ejecutar(File destino, Formato formato, Generador generador, Runnable alTerminar) {
        if (agente.hayAccion()) {
            notificador.advertencia("Ya hay una accion en curso; espera a que termine o cancelala desde el dock.");
            return;
        }
        String nombre = "Generar " + destino.getName();
        agente.ejecutar(nombre, paso -> {
            paso.avanzar(TipoTraza.DECISION,
                    "Validando la ruta de destino",
                    destino.getAbsolutePath(),
                    "explorador del sistema",
                    "el usuario confirmo el archivo en el dialogo de guardado");
            paso.avanzar(TipoTraza.CONSULTA,
                    "Leyendo los datos del periodo",
                    "consultas a orden_trabajo, repuesto y plan_preventivo",
                    "base de datos",
                    "periodo elegido en la vista de reportes");
            paso.avanzar(TipoTraza.CALCULO,
                    "Componiendo el documento " + formato.extension,
                    "Plantilla ejecutiva del CMMS sobre " + destino.getName(),
                    formato == Formato.PDF ? "motor OpenPDF" : "motor Apache POI",
                    "formato " + formato.descripcion);
            // Paso critico: se ejecuta completo; la cancelacion surte efecto despues.
            generador.generar(destino);
            paso.avanzar(TipoTraza.RESULTADO,
                    "Archivo generado en disco",
                    destino.getName() + " (" + destino.length() + " bytes)",
                    "disco local",
                    "escritura completada sin errores");
        }, res -> manejar(res, destino, formato, generador, alTerminar));
    }

    private void manejar(AgenteTareas.Resultado res, File destino, Formato formato,
                         Generador generador, Runnable alTerminar) {
        if (res.cancelado()) {
            if (destino.exists() && !destino.delete()) {
                log.warn("No se pudo limpiar {} tras cancelar", destino);
            }
            notificador.advertencia("Generacion cancelada: no se creo " + destino.getName() + ".");
            return;
        }
        if (res.fallo() != null) {
            Exception causa = res.fallo();
            log.error("No se pudo generar {}", destino, causa);
            notificador.fallar("EXP-001",
                    "No se pudo generar " + destino.getName(),
                    "Fallo de archivos al escribir: " + (causa.getMessage() != null
                            ? causa.getMessage() : causa.getClass().getSimpleName()),
                    "Guarda en otra carpeta. Si el archivo ya existe y esta abierto en Excel o en el visor, cierralo e intentalo de nuevo.",
                    () -> ejecutar(destino, formato, generador, alTerminar));
            return;
        }
        alTerminar.run();
    }

    private void abrir(File archivo) {
        if (hostServices != null) {
            hostServices.showDocument(archivo.toURI().toString());
        }
    }
}
