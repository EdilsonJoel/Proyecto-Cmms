package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.EstadoEquipo;
import com.mantenimiento.cmms.model.EstadoOrden;
import com.mantenimiento.cmms.model.Equipo;
import com.mantenimiento.cmms.model.OrdenTrabajo;
import com.mantenimiento.cmms.model.PlanPreventivo;
import com.mantenimiento.cmms.model.Prioridad;
import com.mantenimiento.cmms.model.Tecnico;
import com.mantenimiento.cmms.model.TipoOrden;
import com.mantenimiento.cmms.repository.EquipoRepository;
import com.mantenimiento.cmms.repository.OrdenTrabajoRepository;
import com.mantenimiento.cmms.repository.PlanPreventivoRepository;
import com.mantenimiento.cmms.repository.TecnicoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;

/**
 * Mantenimiento preventivo: planes por equipo y generacion automatica de
 * ordenes de trabajo cuando la fecha programada llega o se vence.
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PreventivoService {

    private static final int TAMANIO_PAGINA = 15;

    private final PlanPreventivoRepository repository;
    private final EquipoRepository equipoRepository;
    private final TecnicoRepository tecnicoRepository;
    private final OrdenTrabajoRepository ordenRepository;
    private final OrdenService ordenService;
    private final SesionService sesion;
    private final com.mantenimiento.cmms.agente.Trazador trazador;

    public Page<PlanPreventivo> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio);
        if (filtro == null || filtro.isBlank()) {
            return repository.findAll(pageable);
        }
        String t = filtro.trim().toLowerCase();
        return repository.findAll((raiz, q, cb) -> cb.or(
                cb.like(cb.lower(raiz.get("equipo").get("nombre")), "%" + t + "%"),
                cb.like(cb.lower(raiz.get("equipo").get("codigo")), "%" + t + "%"),
                cb.like(cb.lower(raiz.get("nombre")), "%" + t + "%")), pageable);
    }

    public List<PlanPreventivo> todos() {
        return repository.findAllByOrderByProximaFechaAsc();
    }

    /** Planes activos ordenados por urgencia, para la lista de proximos mantenimientos. */
    public List<PlanPreventivo> proximos() {
        return repository.findByActivoTrueOrderByProximaFechaAsc();
    }

    public PlanPreventivo obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException("El plan de mantenimiento ya no existe."));
    }

    @Transactional
    public PlanPreventivo guardar(@Valid PlanPreventivo plan) {
        if (plan.getEquipo() == null) {
            throw new NegocioException("Selecciona el equipo al que aplica el plan.");
        }
        if (plan.getNombre() == null || plan.getNombre().isBlank()) {
            throw new NegocioException("Indica el nombre de la tarea preventiva.");
        }
        Equipo equipo = equipoRepository.findById(plan.getEquipo().getId())
                .orElseThrow(() -> new NegocioException("Selecciona un equipo valido."));
        if (equipo.isDeBaja()) {
            throw new NegocioException("No se puede programar mantenimiento sobre un equipo dado de baja.");
        }
        plan.setEquipo(equipo);
        if (plan.getFrecuenciaDias() < 1) {
            throw new NegocioException("La frecuencia debe ser de al menos 1 dia.");
        }
        if (plan.getProximaFecha() == null) {
            throw new NegocioException("Indica la fecha del proximo mantenimiento.");
        }
        if (plan.getTecnico() != null && plan.getTecnico().getId() != null) {
            Tecnico tecnico = tecnicoRepository.findById(plan.getTecnico().getId())
                    .orElseThrow(() -> new NegocioException("El tecnico seleccionado ya no existe."));
            plan.setTecnico(tecnico);
        } else {
            plan.setTecnico(null);
        }
        PlanPreventivo guardado = repository.save(plan);
        log.info("Plan preventivo guardado: {} cada {} dias", guardado.getEquipo().getCodigo(),
                guardado.getFrecuenciaDias());
        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {
        obtener(id);
        repository.deleteById(id);
        log.info("Plan preventivo eliminado: id={}", id);
    }

    /**
     * Crea una orden de trabajo por cada plan vencido o que venza hoy, y avanza
     * la fecha proxima segun la frecuencia. Es la rutina que el boton
     * "Generar ordenes" ejecuta desde la vista de preventivo.
     *
     * @return cantidad de ordenes generadas.
     */
    @Transactional
    public int generarOrdenesPendientes() {
        List<PlanPreventivo> pendientes = repository
                .findByActivoTrueAndProximaFechaLessThanEqual(LocalDate.now());
        int generadas = 0;
        for (PlanPreventivo plan : pendientes) {
            OrdenTrabajo orden = OrdenTrabajo.builder()
                    .equipo(plan.getEquipo())
                    .tecnico(plan.getTecnico())
                    .tipo(TipoOrden.PREVENTIVO)
                    .prioridad(plan.getEquipo().isCritico() ? Prioridad.ALTA : Prioridad.MEDIA)
                    .titulo(plan.getTituloOrden())
                    .descripcion(descripcionAutomatica(plan))
                    .fechaProgramada(LocalDate.now())
                    .estado(EstadoOrden.ABIERTA)
                    .build();
            OrdenTrabajo creada = ordenService.crear(orden);

            LocalDate nuevaFecha = siguienteFecha(plan);
            plan.setProximaFecha(nuevaFecha);
            plan.setUltimaOrden(LocalDate.now());
            repository.save(plan);
            generadas++;

            trazador.decision("Plan '" + plan.getNombre() + "' genero la orden "
                            + creada.getNumero(),
                    "proximaFecha vencida (<= hoy) y plan activo · equipo "
                            + plan.getEquipo().getCodigo(),
                    "El plan se reprograma a " + nuevaFecha + " (+" + plan.getFrecuenciaDias()
                            + " dias)");
        }
        if (generadas > 0) {
            log.info("Preventivo: {} ordenes generadas automaticamente", generadas);
            trazador.resultado(generadas + (generadas == 1
                            ? " orden preventiva generada"
                            : " ordenes preventivas generadas"),
                    "Cada plan vencido dejo su orden en estado ABIERTA",
                    "tabla orden_trabajo");
        }
        return generadas;
    }

    private String descripcionAutomatica(PlanPreventivo plan) {
        StringBuilder sb = new StringBuilder("Orden generada automaticamente desde el plan de mantenimiento "
                + "preventivo del equipo " + plan.getEquipo().getCodigo() + ".");
        if (plan.getObservaciones() != null && !plan.getObservaciones().isBlank()) {
            sb.append("\nTarea a realizar: ").append(plan.getObservaciones());
        }
        if (plan.getHorasEstimadas() != null) {
            sb.append("\nTiempo estimado: ").append(plan.getHorasEstimadas()).append(" horas.");
        }
        return sb.toString();
    }

    /** Sigue a partir de hoy (no de la fecha vencida) para no arrastrar atrasos. */
    private LocalDate siguienteFecha(PlanPreventivo plan) {
        return LocalDate.now().plusDays(plan.getFrecuenciaDias());
    }

    public long contarVencidos() {
        return repository.findByActivoTrueAndProximaFechaLessThanEqual(LocalDate.now()).size();
    }

    /** Planes activos que vencen dentro de los proximos dias (alerta del tablero). */
    public long contarProximos(int dias) {
        return repository.findByActivoTrueOrderByProximaFechaAsc().stream()
                .filter(p -> !p.getProximaFecha().isBefore(LocalDate.now())
                        && !p.getProximaFecha().isAfter(LocalDate.now().plusDays(dias)))
                .count();
    }

    public long contar() {
        return repository.count();
    }
}
