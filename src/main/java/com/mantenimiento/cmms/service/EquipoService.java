package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.EstadoEquipo;
import com.mantenimiento.cmms.model.EstadoOrden;
import com.mantenimiento.cmms.model.Equipo;
import com.mantenimiento.cmms.model.OrdenTrabajo;
import com.mantenimiento.cmms.repository.EquipoRepository;
import com.mantenimiento.cmms.repository.OrdenTrabajoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Altas, bajas, busqueda e historial de los equipos (activos) de la empresa. */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EquipoService {

    private static final int TAMANIO_PAGINA = 15;

    private final EquipoRepository repository;
    private final OrdenTrabajoRepository ordenRepository;

    public Page<Equipo> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio);
        if (filtro == null || filtro.isBlank()) {
            return repository.findAll(pageable);
        }
        return repository.buscar(filtro.trim(), pageable);
    }

    public List<Equipo> todos() {
        return repository.findAll();
    }

    public List<Equipo> enOperacion() {
        return repository.findByEstado(EstadoEquipo.OPERATIVO);
    }

    public Equipo obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException("El equipo ya no existe."));
    }

    @Transactional
    public Equipo guardar(@Valid Equipo equipo) {
        repository.findByCodigoIgnoreCase(equipo.getCodigo()).ifPresent(existente -> {
            boolean esOtro = equipo.getId() == null || !existente.getId().equals(equipo.getId());
            if (esOtro) {
                throw new NegocioException("Ya existe un equipo con el codigo " + equipo.getCodigo());
            }
        });

        if (equipo.getId() != null && equipo.isDeBaja()) {
            Equipo anterior = obtener(equipo.getId());
            if (anterior.getEstado() != EstadoEquipo.BAJA && tieneOrdenesActivas(equipo.getId())) {
                throw new NegocioException(
                        "No se puede dar de baja: el equipo tiene ordenes de trabajo activas.");
            }
        }

        Equipo guardado = repository.save(equipo);
        log.info("Equipo guardado: {} - {}", guardado.getCodigo(), guardado.getNombre());
        return guardado;
    }

    @Transactional
    public Equipo cambiarEstado(Long id, EstadoEquipo estado) {
        Equipo equipo = obtener(id);
        if (estado == EstadoEquipo.BAJA && tieneOrdenesActivas(id)) {
            throw new NegocioException(
                    "No se puede dar de baja: el equipo tiene ordenes de trabajo activas.");
        }
        if (equipo.getEstado() == EstadoEquipo.BAJA && estado != EstadoEquipo.BAJA) {
            throw new NegocioException(
                    "Un equipo dado de baja no puede volver a operar; registra uno nuevo.");
        }
        equipo.setEstado(estado);
        repository.save(equipo);
        log.info("Equipo {} -> {}", equipo.getCodigo(), estado);
        return equipo;
    }

    @Transactional
    public void eliminar(Long id) {
        Equipo equipo = obtener(id);
        if (ordenRepository.existsByEquipoId(id)) {
            throw new NegocioException(
                    "No se puede eliminar: " + equipo.getNombre() + " tiene ordenes de trabajo en su historial.");
        }
        repository.deleteById(id);
        log.info("Equipo eliminado: id={}", id);
    }

    /** Historial completo del equipo, el mas reciente primero. */
    public List<OrdenTrabajo> historial(Long idEquipo) {
        return ordenRepository.findByEquipoIdOrderByFechaCreacionDesc(idEquipo);
    }

    public boolean tieneOrdenesActivas(Long idEquipo) {
        return ordenRepository.existsByEquipoIdAndEstadoIn(idEquipo,
                List.of(EstadoOrden.ABIERTA, EstadoOrden.EN_EJECUCION));
    }

    // ------------------------------------------------------------- reportes

    /** Conteo de ordenes por equipo, para mostrar en la tabla sin hacer N+1. */
    public Map<Long, Long> conteoOrdenesPorEquipo() {
        return ordenRepository.findAll().stream()
                .collect(Collectors.groupingBy(o -> o.getEquipo().getId(), Collectors.counting()));
    }

    /** Costo acumulado de ordenes completadas por equipo. */
    public Map<Long, BigDecimal> costoPorEquipo() {
        Map<Long, BigDecimal> costos = new java.util.HashMap<>();
        for (OrdenTrabajo o : ordenRepository.findAll()) {
            if (o.getEstado() == EstadoOrden.COMPLETADA) {
                costos.merge(o.getEquipo().getId(), o.getCostoTotal(), BigDecimal::add);
            }
        }
        return costos;
    }

    public long contar() {
        return repository.count();
    }

    public long contarActivos() {
        return repository.count() - repository.countByEstado(EstadoEquipo.BAJA);
    }

    /** Ultima fecha de fin de orden del equipo (o null si nunca se hallo). */
    public LocalDateTime ultimaIntervencion(Long idEquipo) {
        return historial(idEquipo).stream()
                .map(OrdenTrabajo::getFechaFin)
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
    }
}
