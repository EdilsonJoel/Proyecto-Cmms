package com.mantenimiento.cmms.controller;

import atlantafx.base.theme.Styles;
import com.mantenimiento.cmms.agente.AgenteTareas;
import com.mantenimiento.cmms.agente.Recomendador;
import com.mantenimiento.cmms.agente.TipoTraza;
import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.Equipo;
import com.mantenimiento.cmms.model.OrdenTrabajo;
import com.mantenimiento.cmms.model.PlanPreventivo;
import com.mantenimiento.cmms.model.Prioridad;
import com.mantenimiento.cmms.model.TipoMovimiento;
import com.mantenimiento.cmms.model.TipoOrden;
import com.mantenimiento.cmms.service.DashboardService;
import com.mantenimiento.cmms.service.DashboardService.*;
import com.mantenimiento.cmms.service.EquipoService;
import com.mantenimiento.cmms.service.OrdenService;
import com.mantenimiento.cmms.service.PreventivoService;
import com.mantenimiento.cmms.service.RepuestoService;
import com.mantenimiento.cmms.ui.Formularios;
import com.mantenimiento.cmms.ui.Notificador;
import com.mantenimiento.cmms.ui.Refrescable;
import com.mantenimiento.cmms.ui.Tablas;
import atlantafx.base.util.Animations;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tablero principal: KPIs con su <b>origen y confianza</b> (BD vs calculo),
 * graficas, ranking, alertas y — la capa del agente — <b>acciones sugeridas</b>
 * que el usuario aplica o descarta (override). La generacion de preventivos
 * corre como accion controlable del dock (pausable/cancelable).
 */
@Component
@RequiredArgsConstructor
public class DashboardController implements Refrescable {

    private final DashboardService dashboardService;
    private final PreventivoService preventivoService;
    private final Notificador notificador;
    private final Recomendador recomendador;
    private final RepuestoService repuestoService;
    private final EquipoService equipoService;
    private final OrdenService ordenService;
    private final AgenteTareas agente;
    private final MainController main;

    // KPIs
    @FXML private Label lblKpiDisponibilidad;
    @FXML private Label lblKpiDisponibilidadDetalle;
    @FXML private Label lblKpiOrdenes;
    @FXML private Label lblKpiOrdenesDetalle;
    @FXML private Label lblKpiCosto;
    @FXML private Label lblKpiCostoDetalle;
    @FXML private Label lblKpiMttr;
    @FXML private Label lblKpiMttrDetalle;

    // Graficas
    @FXML private AreaChart<String, Number> graficoCostos;
    @FXML private PieChart graficoEstados;
    @FXML private BarChart<String, Number> graficoTipos;

    // Listas
    @FXML private VBox listaTopCostos;
    @FXML private VBox listaAlertas;
    @FXML private VBox listaUltimas;
    @FXML private VBox listaCarga;
    @FXML private VBox listaSugerencias;

    @FXML private Button btnPreventivo;
    @FXML private Button btnRestaurar;

    @Override
    public void refrescar() {
        Resumen r = dashboardService.obtenerResumen();
        pintarKpis(r.kpis());
        pintarCostos(r.costoMensual());
        pintarEstados(r.porEstado());
        pintarTipos(r.porTipo());
        pintarTopCostos(r.topCostos());
        pintarAlertas(r);
        pintarSugerencias();
        pintarUltimasOrdenes(r.ultimas());
        pintarCarga(r.cargaTecnicos());

        long vencidos = r.kpis().planesVencidos();
        btnPreventivo.setVisible(vencidos > 0);
        btnPreventivo.setManaged(vencidos > 0);
        btnPreventivo.setText("Generar " + vencidos + (vencidos == 1 ? " orden vencida" : " ordenes vencidas"));
    }

    // --------------------------------------------------------------- KPIs

    private void pintarKpis(Indicadores k) {
        lblKpiDisponibilidad.setText(String.format(java.util.Locale.US, "%.1f%%", k.disponibilidad()));
        lblKpiDisponibilidadDetalle.setText(k.operativos() + " de " + k.equiposActivos()
                + " equipos operativos · BD n=" + k.equiposActivos());

        long activas = k.abiertas() + k.enEjecucion();
        lblKpiOrdenes.setText(String.valueOf(activas));
        lblKpiOrdenesDetalle.setText((k.vencidas() > 0
                ? k.vencidas() + " vencidas · " + k.abiertas() + " abiertas"
                : k.abiertas() + " abiertas · " + k.enEjecucion() + " en ejecucion") + " · BD");
        lblKpiOrdenesDetalle.setStyle(k.vencidas() > 0
                ? "-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;" : "");

        lblKpiCosto.setText("S/ " + Tablas.MONEDA.format(k.costoMes()).replace("S/", "").trim());
        if (k.variacionCosto() == null) {
            lblKpiCostoDetalle.setText("Sin datos del mes anterior · BD");
        } else {
            double v = k.variacionCosto();
            lblKpiCostoDetalle.setText(String.format(java.util.Locale.US, "%+.1f%% vs mes anterior · BD", v));
            lblKpiCostoDetalle.setStyle(v > 0 ? "-fx-text-fill: -color-danger-fg;"
                    : v < 0 ? "-fx-text-fill: -color-success-fg;" : "");
        }

        lblKpiMttr.setText(k.mttrHoras() + " h");
        lblKpiMttrDetalle.setText(k.completadas30() + " ordenes cerradas (30 d) · calc · conf. "
                + confianza(k.completadas30()));
    }

    /** Confianza del calculo sin alarmar: tono neutro, basado en el tamano de muestra. */
    private static String confianza(long n) {
        return n >= 30 ? "alta (n=" + n + ")"
                : n >= 10 ? "media (n=" + n + ")"
                : "baja (n=" + n + ")";
    }

    // ------------------------------------------------------------- graficas

    private void pintarCostos(List<PuntoCosto> puntos) {
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (PuntoCosto p : puntos) {
            serie.getData().add(new XYChart.Data<>(p.etiqueta(), p.valor()));
        }
        serie.setName("Costo mensual");
        graficoCostos.getData().setAll(serie);
        graficoCostos.setAnimated(true);
    }

    private void pintarEstados(List<Serie> series) {
        List<PieChart.Data> datos = series.stream()
                .filter(s -> s.valor() > 0)
                .map(s -> new PieChart.Data(s.etiqueta(), s.valor()))
                .toList();
        if (datos.isEmpty()) {
            graficoEstados.getData().setAll(new PieChart.Data("Sin ordenes", 1));
        } else {
            graficoEstados.getData().setAll(datos);
        }
        graficoEstados.setLabelsVisible(true);
        graficoEstados.setLegendVisible(false);
        graficoEstados.setAnimated(true);
    }

    private void pintarTipos(List<Serie> series) {
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Ordenes");
        for (Serie s : series) {
            serie.getData().add(new XYChart.Data<>(s.etiqueta(), s.valor()));
        }
        graficoTipos.getData().setAll(serie);
        graficoTipos.setAnimated(true);
    }

    // --------------------------------------------------------------- listas

    private void pintarTopCostos(List<CostoEquipo> costos) {
        listaTopCostos.getChildren().clear();
        if (costos.isEmpty()) {
            listaTopCostos.getChildren().add(vacio("Todavia sin costos completados"));
            return;
        }
        int posicion = 1;
        for (CostoEquipo c : costos) {
            Label num = new Label(String.valueOf(posicion++));
            num.getStyleClass().add("ranking-posicion");

            VBox info = new VBox(1);
            Label nombre = new Label(c.equipo());
            nombre.getStyleClass().add("ranking-nombre");
            Label codigo = new Label(c.codigo() + "  ·  " + c.ordenes() + " ordenes");
            codigo.getStyleClass().add("ranking-detalle");
            info.getChildren().addAll(nombre, codigo);

            Label monto = new Label("S/ " + Tablas.MONEDA.format(c.costo()).replace("S/", "").trim());
            monto.getStyleClass().add("ranking-precio");

            HBox fila = new HBox(10, num, info, espacio(), monto);
            fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            fila.getStyleClass().add("venta-fila");
            listaTopCostos.getChildren().add(fila);
        }
    }

    private void pintarAlertas(Resumen r) {
        listaAlertas.getChildren().clear();
        Indicadores k = r.kpis();

        if (k.vencidas() > 0) {
            listaAlertas.getChildren().add(alerta("peligro", k.vencidas() + " ordenes vencidas",
                    "Revisa las fechas programadas"));
        }
        if (k.planesVencidos() > 0) {
            listaAlertas.getChildren().add(alerta("peligro", k.planesVencidos()
                    + " preventivos vencidos", "Genera las ordenes pendientes"));
        }
        if (k.repuestosBajoStock() > 0) {
            listaAlertas.getChildren().add(alerta("advertencia",
                    k.repuestosBajoStock() + " repuestos bajo stock",
                    k.repuestosSinStock() + " sin existencias"));
        }
        if (k.preventivosProximos() > 0) {
            listaAlertas.getChildren().add(alerta("info",
                    k.preventivosProximos() + " preventivos en 7 dias", "Proximos a vencer"));
        }
        for (PlanPreventivo plano : r.planesUrgentes()) {
            listaAlertas.getChildren().add(alerta(plano.isVencido() ? "peligro" : "info",
                    plano.getEquipo().getCodigo(), plano.getNombre() == null
                            ? "Mantenimiento preventivo" : plano.getNombre()
                            + " · " + (plano.getDiasRestantes() < 0
                            ? "vencido hace " + Math.abs(plano.getDiasRestantes()) + " d"
                            : "en " + plano.getDiasRestantes() + " d")));
        }
        if (k.repuestosSinStock() > 0) {
            listaAlertas.getChildren().add(alerta("peligro",
                    k.repuestosSinStock() + " repuestos sin stock", "Reposicion urgente"));
        }
        if (listaAlertas.getChildren().isEmpty()) {
            listaAlertas.getChildren().add(alerta("exito", "Sin alertas", "Todo bajo control"));
        }
    }

    private void pintarUltimasOrdenes(List<OrdenTrabajo> ultimas) {
        listaUltimas.getChildren().clear();
        if (ultimas.isEmpty()) {
            listaUltimas.getChildren().add(vacio("Todavia no hay ordenes registradas"));
            return;
        }
        for (OrdenTrabajo o : ultimas) {
            Label chip = new Label(o.getNumero());
            chip.getStyleClass().addAll("chip", o.getEstado().getClase());

            VBox info = new VBox(1);
            Label titulo = new Label(o.getTitulo());
            titulo.getStyleClass().add("ranking-nombre");
            titulo.setMaxWidth(Double.MAX_VALUE);
            Label det = new Label(o.getTipo().getEtiqueta()
                    + " · " + (o.getTecnico() == null ? "Sin tecnico asignado" : o.getTecnico().getNombre())
                    + " · " + o.getFechaCreacion().toLocalDate());
            det.getStyleClass().add("ranking-detalle");
            info.getChildren().addAll(titulo, det);
            info.setMaxWidth(Double.MAX_VALUE);

            Label estado = new Label(o.getEstado().getEtiqueta());
            estado.getStyleClass().addAll("chip", o.getEstado().getClase());

            HBox fila = new HBox(10, chip, info, espacio(), estado);
            fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            fila.getStyleClass().add("venta-fila");
            listaUltimas.getChildren().add(fila);
        }
    }

    private void pintarCarga(List<CargaTecnico> carga) {
        listaCarga.getChildren().clear();
        if (carga.isEmpty()) {
            listaCarga.getChildren().add(vacio("Sin tecnicos con ordenes activas"));
            return;
        }
        for (CargaTecnico c : carga) {
            VBox info = new VBox(1);
            Label nombre = new Label(c.tecnico());
            nombre.getStyleClass().add("ranking-nombre");
            nombre.setMaxWidth(Double.MAX_VALUE);
            Label det = new Label(c.especialidad() + " · " + c.completadas() + " completadas");
            det.getStyleClass().add("ranking-detalle");
            info.getChildren().addAll(nombre, det);
            info.setMaxWidth(Double.MAX_VALUE);

            Label activas = new Label(c.activas() + " activas");
            activas.getStyleClass().addAll("chip", c.activas() > 0 ? "info" : "neutro");

            HBox fila = new HBox(10, info, espacio(), activas);
            fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            fila.getStyleClass().add("venta-fila");
            listaCarga.getChildren().add(fila);
        }
    }

    // --------------------------------------- acciones sugeridas (override)

    private void pintarSugerencias() {
        listaSugerencias.getChildren().clear();
        int descartadas = recomendador.ignoradasCount();
        btnRestaurar.setVisible(descartadas > 0);
        btnRestaurar.setManaged(descartadas > 0);
        btnRestaurar.setText("Restaurar descartadas (" + descartadas + ")");

        List<Recomendador.Sugerencia> sugerencias = recomendador.sugerir();
        if (sugerencias.isEmpty()) {
            Label sinNada = new Label(descartadas > 0
                    ? "Sin sugerencias activas. Las descartadas puedes restaurarlas arriba."
                    : "Sin sugerencias: todo bajo control.");
            sinNada.getStyleClass().add("sugerencia-estado");
            listaSugerencias.getChildren().add(sinNada);
            return;
        }
        for (Recomendador.Sugerencia s : sugerencias) {
            listaSugerencias.getChildren().add(filaSugerencia(s));
        }
    }

    private Node filaSugerencia(Recomendador.Sugerencia s) {
        Label chip = new Label(s.prioridad().equals("peligro") ? "!"
                : s.prioridad().equals("advertencia") ? "▲" : "i");
        chip.getStyleClass().addAll("chip", s.prioridad());

        VBox info = new VBox(2);
        Label titulo = new Label(s.titulo());
        titulo.getStyleClass().add("sugerencia-titulo");
        Label detalle = new Label(s.detalle());
        detalle.getStyleClass().add("sugerencia-detalle");
        Label fuente = new Label("fuente: " + s.fuente() + "   ·   criterio: " + s.criterio());
        fuente.getStyleClass().add("sugerencia-fuente");
        fuente.setWrapText(true);
        info.getChildren().addAll(titulo, detalle, fuente);
        info.setMaxWidth(Double.MAX_VALUE);

        Button ignorar = new Button("Ignorar", new FontIcon(Feather.X));
        ignorar.getStyleClass().add(Styles.BUTTON_OUTLINED);
        ignorar.setOnAction(e -> {
            recomendador.ignorar(s.id());
            notificador.info("Sugerencia descartada. Puedes restaurarla con el boton de la cabecera.");
            refrescar();
        });

        Button aplicar = new Button(textoAplicar(s), new FontIcon(Feather.CHECK_CIRCLE));
        aplicar.getStyleClass().add("accent");
        aplicar.setOnAction(e -> aplicar(s));

        HBox fila = new HBox(10, chip, info, espacio(), ignorar, aplicar);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.getStyleClass().add("sugerencia-fila");
        return fila;
    }

    private static String textoAplicar(Recomendador.Sugerencia s) {
        return switch (s.tipo()) {
            case REPOSICION -> "Reponer";
            case PREVENTIVAS -> "Generar";
            case ORDENES_VENCIDAS -> "Ver ordenes";
            case CORRECTIVA -> "Abrir correctiva";
        };
    }

    private void aplicar(Recomendador.Sugerencia s) {
        switch (s.tipo()) {
            case REPOSICION -> dialogoReposicion(s);
            case PREVENTIVAS -> generarConAgente();
            case ORDENES_VENCIDAS -> {
                main.irA("orden-view");
                notificador.info("Filtra por estado Abierta para revisar las fechas vencidas.");
            }
            case CORRECTIVA -> abrirCorrectiva(s);
        }
    }

    private void dialogoReposicion(Recomendador.Sugerencia s) {
        TextField cantidad = new TextField(String.valueOf(s.cantidad()));
        Formularios.soloDigitos(cantidad, 6);

        notificador.dialogoContenido(Feather.PLUS_CIRCLE, "info",
                "Reponer " + s.titulo(),
                Formularios.campo("Cantidad a ingresar", cantidad),
                "Registrar entrada", Styles.ACCENT, () -> {
                    int c;
                    try {
                        c = Integer.parseInt(cantidad.getText().trim());
                    } catch (NumberFormatException ex) {
                        Formularios.marcarError(cantidad, "Ingresa un numero entero.");
                        return;
                    }
                    if (c <= 0) {
                        Formularios.marcarError(cantidad, "Debe ser mayor a cero.");
                        return;
                    }
                    try {
                        repuestoService.registrarMovimiento(s.referencia(),
                                TipoMovimiento.ENTRADA, c,
                                "Reposicion sugerida por el agente", null);
                        notificador.cerrarDialogo();
                        notificador.exito("Entrada registrada: +" + c + " unidades en kardex.");
                        refrescar();
                    } catch (NegocioException e) {
                        notificador.fallar(e, null);
                    }
                });
    }

    private void abrirCorrectiva(Recomendador.Sugerencia s) {
        try {
            Equipo equipo = equipoService.obtener(s.referencia());
            OrdenTrabajo creada = ordenService.crear(OrdenTrabajo.builder()
                    .equipo(equipo)
                    .tipo(TipoOrden.CORRECTIVO)
                    .prioridad(Prioridad.ALTA)
                    .titulo("Correctiva · " + equipo.getCodigo())
                    .descripcion(s.detalle() + ". Orden abierta desde las acciones sugeridas.")
                    .fechaProgramada(LocalDate.now())
                    .build());
            notificador.exito("Orden " + creada.getNumero() + " creada (correctiva).");
            refrescar();
        } catch (NegocioException e) {
            notificador.fallar(e, null);
        }
    }

    // ------------------------------------------------------------ acciones

    @FXML
    private void onRefrescar() {
        refrescar();
        notificador.info("Tablero actualizado.");
    }

    @FXML
    private void onRestaurarSugerencias() {
        recomendador.restaurar();
        notificador.info("Sugerencias descartadas restauradas.");
        refrescar();
    }

    /** Genera los preventivos vencidos como accion controlable del dock. */
    @FXML
    private void onGenerarPreventivos() {
        generarConAgente();
    }

    private void generarConAgente() {
        if (agente.hayAccion()) {
            notificador.advertencia("Ya hay una accion en curso; cancelala o espera desde el dock.");
            return;
        }
        long vencidos = preventivoService.contarVencidos();
        if (vencidos == 0) {
            notificador.info("No hay planes vencidos: todo el preventivo esta al dia.");
            return;
        }
        int[] creadas = new int[1];
        agente.ejecutar("Generar ordenes preventivas", paso -> {
            paso.avanzar(TipoTraza.CONSULTA,
                    "Consultando planes vencidos",
                    vencidos + " planes con proxima fecha <= hoy",
                    "tabla plan_preventivo",
                    "proximaFecha <= hoy y activo = true");
            paso.avanzar(TipoTraza.DECISION,
                    "Validando que no haya duplicados",
                    "una orden por plan vencido, sin orden abierta equivalente",
                    "reglas de preventivo",
                    "los planes ya atendidos se descartan");
            creadas[0] = preventivoService.generarOrdenesPendientes();
            paso.avanzar(TipoTraza.RESULTADO,
                    "Ordenes preventivas generadas",
                    creadas[0] + " ordenes creadas y planes reprogramados",
                    "tabla orden_trabajo",
                    "resultado >= 0");
        }, res -> {
            if (res.cancelado()) {
                notificador.advertencia("Generacion cancelada: no se creo ninguna orden.");
                return;
            }
            if (res.fallo() != null) {
                notificador.fallar(oNegocio(res.fallo()), this::generarConAgente);
                return;
            }
            if (creadas[0] == 0) {
                notificador.info("No hay planes vencidos: todo el preventivo esta al dia.");
            } else {
                notificador.exito(creadas[0] == 1
                        ? "1 orden de mantenimiento preventivo generada."
                        : creadas[0] + " ordenes de mantenimiento preventivo generadas.");
            }
            refrescar();
        });
    }

    private static NegocioException oNegocio(Exception e) {
        return e instanceof NegocioException n ? n
                : new NegocioException("PRE-001",
                "No se pudieron generar las ordenes preventivas",
                "Fallo interno: " + (e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage()),
                "Reintenta; si persiste, copia el diagnostico desde la bitacora y reportalo al soporte.");
    }

    // ---------------------------------------------------------------- apoyo

    private Node alerta(String clase, String texto, String detalle) {
        Label chip = new Label(clase.equals("exito") ? "OK"
                : clase.equals("peligro") ? "!" : clase.equals("advertencia") ? "▲" : "i");
        chip.getStyleClass().addAll("chip", clase);

        VBox info = new VBox(1);
        Label lbl = new Label(texto);
        lbl.getStyleClass().add("ranking-nombre");
        lbl.setMaxWidth(Double.MAX_VALUE);
        Label det = new Label(detalle);
        det.getStyleClass().add("ranking-detalle");
        info.getChildren().addAll(lbl, det);
        info.setMaxWidth(Double.MAX_VALUE);

        HBox fila = new HBox(10, chip, info);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.getStyleClass().add("venta-fila");
        return fila;
    }

    private Label vacio(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("ranking-detalle");
        return l;
    }

    private Region espacio() {
        Region r = new Region();
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }
}
