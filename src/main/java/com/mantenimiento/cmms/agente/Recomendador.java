package com.mantenimiento.cmms.agente;

import com.mantenimiento.cmms.model.EstadoEquipo;
import com.mantenimiento.cmms.model.EstadoOrden;
import com.mantenimiento.cmms.model.Equipo;
import com.mantenimiento.cmms.model.Prioridad;
import com.mantenimiento.cmms.model.Repuesto;
import com.mantenimiento.cmms.repository.EquipoRepository;
import com.mantenimiento.cmms.repository.OrdenTrabajoRepository;
import com.mantenimiento.cmms.service.PreventivoService;
import com.mantenimiento.cmms.service.RepuestoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de recomendaciones: propone acciones concretas (con fuente y criterio)
 * y el usuario decide. El <b>override</b> es de verdad: cada sugerencia puede
 * aplicarse o ignorarse, y las ignoradas no vuelven a aparecer hasta
 * {@link #restaurar()}.
 */
@Component
@RequiredArgsConstructor
public class Recomendador {

    public enum Tipo {
        /** Entrada de compra por stock bajo o agotado. */
        REPOSICION,
        /** Generar las ordenes de los planes preventivos vencidos. */
        PREVENTIVAS,
        /** Redirigir a las ordenes abiertas con fecha vencida. */
        ORDENES_VENCIDAS,
        /** Abrir una orden correctiva sobre un equipo detenido. */
        CORRECTIVA
    }

    /**
     * Sugerencia presentada al usuario.
     *
     * @param id         clave estable para ignorar/aplicar
     * @param tipo       accion que se ejecutaria al pulsar "Aplicar"
     * @param prioridad  clase de chip: peligro, advertencia o info
     * @param titulo     que se propone, en una linea
     * @param detalle    datos concretos que la respaldan
     * @param fuente     de donde sale el dato (tabla/servicio)
     * @param criterio    por que el agente lo propone
     * @param referencia id del repuesto o equipo afectado, si aplica
     * @param cantidad   cantidad sugerida para reposiciones
     */
    public record Sugerencia(String id, Tipo tipo, String prioridad, String titulo,
                             String detalle, String fuente, String criterio,
                             Long referencia, int cantidad) {
    }

    private final RepuestoService repuestoService;
    private final PreventivoService preventivoService;
    private final OrdenTrabajoRepository ordenRepository;
    private final EquipoRepository equipoRepository;

    private final Set<String> ignoradas = ConcurrentHashMap.newKeySet();

    /** Sugerencias vivas (las ignoradas ya no aparecen). */
    public List<Sugerencia> sugerir() {
        List<Sugerencia> lista = new ArrayList<>();
        stock(lista);
        preventivos(lista);
        ordenesVencidas(lista);
        equiposDetenidos(lista);
        return lista.stream().filter(s -> !ignoradas.contains(s.id())).toList();
    }

    private void stock(List<Sugerencia> lista) {
        List<Repuesto> alertas = repuestoService.alertasStock().stream().limit(3).toList();
        for (Repuesto r : alertas) {
            int sugerida = Math.max(1, r.getStockMinimo() * 2 - r.getStock());
            lista.add(new Sugerencia(
                    "STOCK-" + r.getId(),
                    Tipo.REPOSICION,
                    r.getStock() <= 0 ? "peligro" : "advertencia",
                    "Reponer " + r.getCodigo() + " · " + r.getNombre(),
                    "Quedan " + r.getStock() + " " + r.getUnidad()
                            + " en almacen · stock minimo " + r.getStockMinimo(),
                    "tabla repuesto (RepuestoService.alertasStock())",
                    "stock " + r.getStock() + " <= stock minimo " + r.getStockMinimo(),
                    r.getId(),
                    sugerida));
        }
    }

    private void preventivos(List<Sugerencia> lista) {
        long vencidos = preventivoService.contarVencidos();
        if (vencidos > 0) {
            lista.add(new Sugerencia(
                    "PREV-VENCIDOS",
                    Tipo.PREVENTIVAS,
                    "peligro",
                    vencidos == 1 ? "Generar 1 orden preventiva vencida"
                            : "Generar " + vencidos + " ordenes preventivas vencidas",
                    "Planes activos cuya proxima fecha ya paso",
                    "tabla plan_preventivo (PreventivoService.contarVencidos())",
                    "proximaFecha <= hoy y activo = true",
                    null,
                    (int) vencidos));
        }
    }

    private void ordenesVencidas(List<Sugerencia> lista) {
        List<com.mantenimiento.cmms.model.OrdenTrabajo> vencidas =
                ordenRepository.findByFechaProgramadaBeforeAndEstadoIn(
                        LocalDate.now(), List.of(EstadoOrden.ABIERTA));
        if (!vencidas.isEmpty()) {
            boolean algunaCritica = vencidas.stream()
                    .anyMatch(o -> o.getPrioridad() == Prioridad.CRITICA);
            lista.add(new Sugerencia(
                    "OT-VENCIDAS",
                    Tipo.ORDENES_VENCIDAS,
                    algunaCritica ? "peligro" : "advertencia",
                    "Revisar " + vencidas.size()
                            + (vencidas.size() == 1 ? " orden con fecha vencida"
                            : " ordenes con fecha vencida"),
                    "Programadas antes de hoy y todavia abiertas",
                    "tabla orden_trabajo (OrdenTrabajoRepository)",
                    "fechaProgramada < hoy y estado = ABIERTA",
                    null,
                    vencidas.size()));
        }
    }

    private void equiposDetenidos(List<Sugerencia> lista) {
        List<Equipo> detenidos = equipoRepository.findByEstado(EstadoEquipo.DETENIDO).stream()
                .filter(e -> !e.isDeBaja())
                .filter(e -> !ordenRepository.existsByEquipoIdAndEstadoIn(e.getId(),
                        List.of(EstadoOrden.ABIERTA, EstadoOrden.EN_EJECUCION)))
                .limit(2)
                .toList();
        for (Equipo e : detenidos) {
            lista.add(new Sugerencia(
                    "DET-" + e.getId(),
                    Tipo.CORRECTIVA,
                    "peligro",
                    "Abrir orden correctiva · " + e.getCodigo(),
                    e.getNombre() + " esta DETENIDO y no tiene ninguna orden activa",
                    "tabla equipo (EquipoRepository.findByEstado)",
                    "estado = DETENIDO y sin orden en curso",
                    e.getId(),
                    0));
        }
    }

    // ------------------------------------------------------------ override

    /** El usuario descarta la sugerencia: no vuelve a aparecer en la sesion. */
    public void ignorar(String id) {
        ignoradas.add(id);
    }

    /** Devuelve todas las sugerencias descartadas (el usuario puede arrepentirse). */
    public void restaurar() {
        ignoradas.clear();
    }

    public int ignoradasCount() {
        return ignoradas.size();
    }
}
