package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.*;
import com.mantenimiento.cmms.repository.EquipoRepository;
import com.mantenimiento.cmms.repository.MovimientoRepuestoRepository;
import com.mantenimiento.cmms.repository.OrdenTrabajoRepository;
import com.mantenimiento.cmms.repository.TecnicoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Corazon del sistema: ciclo de vida de las ordenes de trabajo.
 * <p>
 * Reglas centrales:
 * <ul>
 *   <li>Transiciones de estado validadas (no se puede completar algo cancelado).</li>
 *   <li>Iniciar exige tecnico asignado y pone el equipo "en mantenimiento".</li>
 *   <li>Al cerrar o cancelar, el equipo vuelve a "operativo" si no queda otra orden en curso.</li>
 *   <li>Los repuestos se consumen con stock suficiente y dejan kardex.</li>
 * </ul>
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrdenService {

    private static final int TAMANIO_PAGINA = 15;

    private final OrdenTrabajoRepository repository;
    private final EquipoRepository equipoRepository;
    private final TecnicoRepository tecnicoRepository;
    private final MovimientoRepuestoRepository movimientoRepository;
    private final RepuestoService repuestoService;
    private final SesionService sesion;
    private final com.mantenimiento.cmms.agente.Trazador trazador;

    // ------------------------------------------------------------- consulta

    public Page<OrdenTrabajo> listar(String filtro, EstadoOrden estado, TipoOrden tipo,
                                     int pagina, int tamanio) {
        return listar(filtro, estado, tipo, null, pagina, tamanio);
    }

    /**
     * Listado paginado con filtros. Cuando {@code idTecnico} no es nulo (sesion de
     * rol TECNICO) solo devuelve las ordenes asignadas a ese tecnico.
     */
    public Page<OrdenTrabajo> listar(String filtro, EstadoOrden estado, TipoOrden tipo,
                                     Long idTecnico, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio, Sort.by("fechaCreacion").descending());
        Specification<OrdenTrabajo> spec = Specification.where(null);

        if (filtro != null && !filtro.isBlank()) {
            String t = filtro.trim().toLowerCase();
            spec = spec.and((raiz, q, cb) -> cb.or(
                    cb.like(cb.lower(raiz.get("numero")), "%" + t + "%"),
                    cb.like(cb.lower(raiz.get("titulo")), "%" + t + "%"),
                    cb.like(cb.lower(raiz.get("equipo").get("nombre")), "%" + t + "%"),
                    cb.like(cb.lower(raiz.get("equipo").get("codigo")), "%" + t + "%"),
                    cb.like(cb.lower(raiz.get("tecnico").get("nombre")), "%" + t + "%")));
        }
        if (estado != null) {
            spec = spec.and((raiz, q, cb) -> cb.equal(raiz.get("estado"), estado));
        }
        if (tipo != null) {
            spec = spec.and((raiz, q, cb) -> cb.equal(raiz.get("tipo"), tipo));
        }
        if (idTecnico != null) {
            spec = spec.and((raiz, q, cb) -> cb.equal(raiz.get("tecnico").get("id"), idTecnico));
        }
        return repository.findAll(spec, pageable);
    }

    /** El tecnico solo puede modificar ordenes asignadas a su propia persona. */
    public boolean puedeModificar(OrdenTrabajo orden) {
        if (!sesion.esTecnico()) {
            return true;
        }
        return orden.getTecnico() != null
                && orden.getTecnico().getUsuario() != null
                && sesion.getUsuario() != null
                && orden.getTecnico().getUsuario().getId().equals(sesion.getUsuario().getId());
    }

    public OrdenTrabajo obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException("La orden de trabajo ya no existe."));
    }

    public List<OrdenTrabajo> activas() {
        return repository.findByEstadoInOrderByPrioridadDescFechaProgramadaAsc(
                List.of(EstadoOrden.ABIERTA, EstadoOrden.EN_EJECUCION));
    }

    public List<OrdenTrabajo> ultimas() {
        return repository.findTop5ByOrderByFechaCreacionDesc();
    }

    public List<OrdenTrabajo> historialEquipo(Long idEquipo) {
        return repository.findByEquipoIdOrderByFechaCreacionDesc(idEquipo);
    }

    // --------------------------------------------------------------- altas

    @Transactional
    public OrdenTrabajo crear(@Valid OrdenTrabajo orden) {
        Equipo equipo = equipoRepository.findById(orden.getEquipo().getId())
                .orElseThrow(() -> new NegocioException("Selecciona un equipo valido."));
        if (equipo.isDeBaja()) {
            throw new NegocioException("No se puede abrir una orden sobre un equipo dado de baja.");
        }
        orden.setEquipo(equipo);
        validarTecnico(orden.getTecnico());

        orden.setNumero(siguienteNumero());
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaCreacion(LocalDateTime.now());
        orden.setFechaInicio(null);
        orden.setFechaFin(null);
        orden.setCreadoPor(sesion.getNombreUsuario());
        if (orden.getCostoManoObra() == null) {
            orden.setCostoManoObra(BigDecimal.ZERO);
        }
        OrdenTrabajo guardada = repository.save(orden);
        log.info("Orden creada: {} sobre {}", guardada.getNumero(), equipo.getCodigo());
        trazador.resultado("Orden " + guardada.getNumero() + " creada (ABIERTA)",
                "Equipo " + equipo.getCodigo() + " · prioridad "
                        + orden.getPrioridad() + " · programada " + orden.getFechaProgramada(),
                "tabla orden_trabajo");
        return guardada;
    }

    @Transactional
    public OrdenTrabajo actualizar(@Valid OrdenTrabajo actualizada) {
        OrdenTrabajo actual = obtener(actualizada.getId());
        if (actual.getEstado().esFinal()) {
            throw new NegocioException(
                    "La orden " + actual.getNumero() + " ya fue cerrada y no admite ediciones.");
        }
        Equipo equipo = equipoRepository.findById(actualizada.getEquipo().getId())
                .orElseThrow(() -> new NegocioException("Selecciona un equipo valido."));
        if (equipo.isDeBaja()) {
            throw new NegocioException("No se puede asignar una orden a un equipo dado de baja.");
        }
        validarTecnico(actualizada.getTecnico());

        actual.setEquipo(equipo);
        actual.setTecnico(actualizada.getTecnico());
        actual.setTipo(actualizada.getTipo());
        actual.setPrioridad(actualizada.getPrioridad());
        actual.setTitulo(actualizada.getTitulo());
        actual.setDescripcion(actualizada.getDescripcion());
        actual.setFechaProgramada(actualizada.getFechaProgramada());
        actual.setHorasManoObra(actualizada.getHorasManoObra());
        actual.setCostoManoObra(actualizada.getCostoManoObra() == null
                ? BigDecimal.ZERO : actualizada.getCostoManoObra());
        OrdenTrabajo guardada = repository.save(actual);
        log.info("Orden actualizada: {}", guardada.getNumero());
        return guardada;
    }

    // ------------------------------------------------------- transiciones

    /** ABIERTA -> EN_EJECUCION: exige tecnico y marca el equipo en mantenimiento. */
    @Transactional
    public OrdenTrabajo iniciar(Long id) {
        OrdenTrabajo orden = obtener(id);
        if (!orden.getEstado().puedeIniciar()) {
            throw new NegocioException("Solo se pueden iniciar ordenes abiertas (estado actual: "
                    + orden.getEstado().getEtiqueta() + ").");
        }
        if (orden.getTecnico() == null) {
            throw new NegocioException("Asigna un tecnico antes de iniciar la orden.");
        }
        orden.setEstado(EstadoOrden.EN_EJECUCION);
        orden.setFechaInicio(LocalDateTime.now());
        if (orden.getFechaProgramada() == null) {
            orden.setFechaProgramada(LocalDate.now());
        }
        if (!orden.getEquipo().isDeBaja()) {
            orden.getEquipo().setEstado(EstadoEquipo.EN_MANTENIMIENTO);
            equipoRepository.save(orden.getEquipo());
        }
        OrdenTrabajo guardada = repository.save(orden);
        log.info("Orden iniciada: {}", guardada.getNumero());
        trazador.decision("Orden " + guardada.getNumero() + " puesta EN EJECUCION",
                "estado previo ABIERTA y tecnico asignado",
                "El equipo " + orden.getEquipo().getCodigo() + " pasa a EN_MANTENIMIENTO");
        return guardada;
    }

    /**
     * -> COMPLETADA: registra el trabajo realizado, horas y costo de mano de obra
     * y libera el equipo si no queda ninguna otra orden en curso.
     */
    @Transactional
    public OrdenTrabajo completar(Long id, String solucion, BigDecimal horas, BigDecimal costoManoObra) {
        OrdenTrabajo orden = obtener(id);
        if (!orden.getEstado().puedeCompletar()) {
            throw new NegocioException("La orden " + orden.getNumero() + " no se puede completar desde el estado "
                    + orden.getEstado().getEtiqueta() + ".");
        }
        if (solucion == null || solucion.isBlank()) {
            throw new NegocioException("Describe el trabajo realizado antes de completar la orden.");
        }
        if (orden.getFechaInicio() == null) {
            orden.setFechaInicio(LocalDateTime.now());
        }
        orden.setSolucion(solucion.trim());
        orden.setHorasManoObra(horas);
        orden.setCostoManoObra(costoManoObra == null ? BigDecimal.ZERO : costoManoObra);
        orden.setEstado(EstadoOrden.COMPLETADA);
        orden.setFechaFin(LocalDateTime.now());
        liberarEquipo(orden);
        OrdenTrabajo guardada = repository.save(orden);
        log.info("Orden completada: {} costo total {}", guardada.getNumero(), guardada.getCostoTotal());
        trazador.decision("Orden " + guardada.getNumero() + " COMPLETADA · costo total S/ "
                        + guardada.getCostoTotal(),
                "costoManoObra + suma(cantidad x precioUnitario de los repuestos)",
                "mano de obra S/ " + orden.getCostoManoObra()
                        + " · repuestos S/ " + guardada.getCostoTotal()
                        .subtract(orden.getCostoManoObra() == null
                                ? BigDecimal.ZERO : orden.getCostoManoObra()));
        return guardada;
    }

    /** -> CANCELADA: cierra sin costo de mano de obra y libera el equipo. */
    @Transactional
    public OrdenTrabajo cancelar(Long id, String motivo) {
        OrdenTrabajo orden = obtener(id);
        if (!orden.getEstado().puedeCancelar()) {
            throw new NegocioException("La orden " + orden.getNumero() + " ya esta en estado "
                    + orden.getEstado().getEtiqueta() + " y no se puede cancelar.");
        }
        orden.setEstado(EstadoOrden.CANCELADA);
        orden.setFechaFin(LocalDateTime.now());
        if (motivo != null && !motivo.isBlank()) {
            String previa = orden.getSolucion() == null ? "" : orden.getSolucion() + "\n";
            orden.setSolucion(previa + "Cancelada: " + motivo.trim());
        }
        liberarEquipo(orden);
        OrdenTrabajo guardada = repository.save(orden);
        log.info("Orden cancelada: {}", guardada.getNumero());
        trazador.decision("Orden " + guardada.getNumero() + " CANCELADA",
                "cierre sin costo de mano de obra; el motivo queda en el historial",
                motivo == null || motivo.isBlank() ? "sin motivo informado" : "motivo: " + motivo.trim());
        return guardada;
    }

    @Transactional
    public void eliminar(Long id) {
        OrdenTrabajo orden = obtener(id);
        if (orden.getEstado() == EstadoOrden.COMPLETADA) {
            throw new NegocioException(
                    "No se puede eliminar una orden completada: forma parte del historial y los costos.");
        }
        if (movimientoRepository.countByOrdenId(id) > 0) {
            throw new NegocioException(
                    "No se puede eliminar: la orden " + orden.getNumero() + " ya consumo repuestos.");
        }
        repository.delete(orden);
        log.info("Orden eliminada: {}", orden.getNumero());
    }

    // -------------------------------------------------------------- repuestos

    /** Consume stock de un repuesto y lo anexa a la orden. */
    @Transactional
    public void agregarRepuesto(Long idOrden, Long idRepuesto, int cantidad) {
        OrdenTrabajo orden = obtener(idOrden);
        if (orden.getEstado().esFinal()) {
            throw new NegocioException("No se pueden agregar repuestos a una orden cerrada.");
        }
        repuestoService.registrarMovimiento(idRepuesto, TipoMovimiento.SALIDA, cantidad,
                "Consumo en orden " + orden.getNumero(), orden);
        repository.save(orden);
    }

    /** Devuelve al almacen una linea de repuesto de la orden. */
    @Transactional
    public void quitarRepuesto(Long idOrden, Long idDetalle) {
        OrdenTrabajo orden = obtener(idOrden);
        if (orden.getEstado().esFinal()) {
            throw new NegocioException("No se pueden quitar repuestos de una orden cerrada.");
        }
        repuestoService.devolverDetalle(idOrden, idDetalle);
        repository.save(orden);
    }

    // --------------------------------------------------------------- apoyo

    /** El equipo solo se libera cuando no queda ninguna otra orden en ejecucion. */
    private void liberarEquipo(OrdenTrabajo orden) {
        Equipo equipo = orden.getEquipo();
        if (equipo.getEstado() != EstadoEquipo.EN_MANTENIMIENTO) {
            return;
        }
        boolean otraEnCurso = repository.findAll().stream()
                .anyMatch(o -> !o.getId().equals(orden.getId())
                        && o.getEquipo().getId().equals(equipo.getId())
                        && o.getEstado() == EstadoOrden.EN_EJECUCION);
        if (otraEnCurso) {
            trazador.aviso("El equipo " + equipo.getCodigo() + " permanece EN_MANTENIMIENTO",
                    "queda otra orden del mismo equipo aun EN EJECUCION");
        } else {
            equipo.setEstado(EstadoEquipo.OPERATIVO);
            equipoRepository.save(equipo);
            trazador.decision("Equipo " + equipo.getCodigo() + " liberado a OPERATIVO",
                    "no queda ninguna orden EN_EJECUCION para este equipo",
                    "la orden " + orden.getNumero() + " ya no lo bloquea");
        }
    }

    private void validarTecnico(Tecnico tecnico) {
        if (tecnico == null || tecnico.getId() == null) {
            return;
        }
        Tecnico guardado = tecnicoRepository.findById(tecnico.getId())
                .orElseThrow(() -> new NegocioException("El tecnico seleccionado ya no existe."));
        if (!guardado.isActivo()) {
            throw new NegocioException("El tecnico " + guardado.getNombre() + " esta inactivo.");
        }
    }

    /** Correlativo OT-0001, saltando numeros ya usados. */
    private String siguienteNumero() {
        long base = repository.count();
        String numero;
        do {
            base++;
            numero = String.format("OT-%04d", base);
        } while (repository.findByNumeroIgnoreCase(numero).isPresent());
        return numero;
    }

    public long contarPorEstado(EstadoOrden estado) {
        return repository.countByEstado(estado);
    }
}
