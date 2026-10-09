package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.DetalleRepuesto;
import com.mantenimiento.cmms.model.MovimientoRepuesto;
import com.mantenimiento.cmms.model.OrdenTrabajo;
import com.mantenimiento.cmms.model.Repuesto;
import com.mantenimiento.cmms.model.TipoMovimiento;
import com.mantenimiento.cmms.repository.DetalleRepuestoRepository;
import com.mantenimiento.cmms.repository.MovimientoRepuestoRepository;
import com.mantenimiento.cmms.repository.RepuestoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Inventario de repuestos: CRUD, alertas de stock minimo y kardex.
 * Toda salida o entrada mueve el stock y deja un movimiento auditado;
 * cuando la salida responde a una orden, nace ademas una linea de detalle.
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RepuestoService {

    private static final int TAMANIO_PAGINA = 15;

    private final RepuestoRepository repository;
    private final MovimientoRepuestoRepository movimientoRepository;
    private final DetalleRepuestoRepository detalleRepository;
    private final SesionService sesion;
    private final com.mantenimiento.cmms.agente.Trazador trazador;

    public Page<Repuesto> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio);
        if (filtro == null || filtro.isBlank()) {
            return repository.findAll(pageable);
        }
        return repository.buscar(filtro.trim(), pageable);
    }

    public List<Repuesto> todos() {
        return repository.findAll(Sort.by("nombre"));
    }

    public List<Repuesto> activos() {
        return repository.findAll(Sort.by("nombre")).stream()
                .filter(Repuesto::isActivo)
                .toList();
    }

    public Repuesto obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException("El repuesto ya no existe."));
    }

    @Transactional
    public Repuesto guardar(@Valid Repuesto repuesto) {
        repository.findByCodigoIgnoreCase(repuesto.getCodigo()).ifPresent(existente -> {
            boolean esOtro = repuesto.getId() == null || !existente.getId().equals(repuesto.getId());
            if (esOtro) {
                throw new NegocioException("Ya existe un repuesto con el codigo " + repuesto.getCodigo());
            }
        });

        if (repuesto.getId() != null) {
            // El stock no se edita aqui: solo cambia por movimientos del kardex.
            repuesto.setStock(repository.findById(repuesto.getId()).map(Repuesto::getStock).orElse(0));
        }
        Repuesto guardado = repository.save(repuesto);
        log.info("Repuesto guardado: {} {}", guardado.getCodigo(), guardado.getNombre());
        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {
        Repuesto repuesto = obtener(id);
        if (detalleRepository.existsByRepuestoId(id)) {
            throw new NegocioException(
                    "No se puede eliminar: " + repuesto.getNombre() + " fue usado en ordenes de trabajo.");
        }
        if (!movimientoRepository.findByRepuestoIdOrderByFechaDesc(id).isEmpty()) {
            throw new NegocioException(
                    "No se puede eliminar: " + repuesto.getNombre() + " tiene movimientos en el kardex.");
        }
        repository.delete(repuesto);
        log.info("Repuesto eliminado: id={}", id);
    }

    // ------------------------------------------------------------ movimientos

    /**
     * Registra un movimiento y actualiza el stock. Si viene una orden, crea la linea
     * de detalle con el precio vigente y la vincula a la orden.
     *
     * @return el detalle creado, o null si el movimiento no fue parte de una orden.
     */
    @Transactional
    public DetalleRepuesto registrarMovimiento(Long idRepuesto, TipoMovimiento tipo, int cantidad,
                                               String motivo, OrdenTrabajo orden) {
        Repuesto repuesto = obtener(idRepuesto);

        if (tipo != TipoMovimiento.AJUSTE && cantidad <= 0) {
            throw new NegocioException("La cantidad debe ser mayor a cero.");
        }
        if (tipo == TipoMovimiento.AJUSTE && cantidad == 0) {
            throw new NegocioException("El ajuste debe tener una cantidad distinta de cero.");
        }
        int variacion = tipo.variacion(cantidad);
        int nuevoStock = repuesto.getStock() + variacion;
        if (nuevoStock < 0) {
            throw new NegocioException("Stock insuficiente: solo hay " + repuesto.getStock()
                    + " " + repuesto.getUnidad() + " de " + repuesto.getNombre() + ".");
        }

        repuesto.setStock(nuevoStock);
        repository.save(repuesto);

        MovimientoRepuesto movimiento = MovimientoRepuesto.builder()
                .repuesto(repuesto)
                .tipo(tipo)
                .cantidad(Math.abs(cantidad))
                .stockResultante(nuevoStock)
                .fecha(LocalDateTime.now())
                .motivo(motivo)
                .orden(orden)
                .registradoPor(sesion.getNombreUsuario())
                .build();
        movimientoRepository.save(movimiento);
        log.info("Movimiento {} x{} sobre {} -> stock {}", tipo, cantidad, repuesto.getCodigo(), nuevoStock);

        trazador.decision("Stock " + repuesto.getCodigo() + ": "
                        + (nuevoStock - variacion) + " → " + nuevoStock,
                tipo == TipoMovimiento.SALIDA
                        ? "stock actual - cantidad >= 0 (nunca deuda de almacen)"
                        : "variacion del movimiento aplicada al stock",
                "movimiento " + tipo + " x" + Math.abs(cantidad)
                        + (motivo == null ? "" : " · " + motivo)
                        + (orden == null ? "" : " · orden " + orden.getNumero()));

        if (orden == null) {
            return null;
        }
        DetalleRepuesto detalle = DetalleRepuesto.builder()
                .orden(orden)
                .repuesto(repuesto)
                .cantidad(Math.abs(cantidad))
                .precioUnitario(precioVigente(repuesto))
                .build();
        orden.getDetalles().add(detalle);
        return detalle;
    }

    /**
     * Quita una linea de la orden y devuelve el stock con una entrada de retorno.
     * El movimiento de salida original permanece en el kardex por trazabilidad.
     */
    @Transactional
    public void devolverDetalle(Long idOrden, Long idDetalle) {
        DetalleRepuesto detalle = detalleRepository.findById(idDetalle)
                .orElseThrow(() -> new NegocioException("La linea de repuesto ya no existe."));
        if (!detalle.getOrden().getId().equals(idOrden)) {
            throw new NegocioException("La linea no pertenece a esta orden.");
        }
        registrarMovimiento(detalle.getRepuesto().getId(), TipoMovimiento.ENTRADA,
                detalle.getCantidad(), "Retorno por edicion de la orden "
                        + detalle.getOrden().getNumero(), null);
        detalle.getOrden().getDetalles().remove(detalle);
    }

    private BigDecimal precioVigente(Repuesto repuesto) {
        return repuesto.getPrecioCosto() == null ? BigDecimal.ZERO : repuesto.getPrecioCosto();
    }

    // ------------------------------------------------------------- consultas

    /** Kardex completo del repuesto, del mas reciente al mas antiguo. */
    public List<MovimientoRepuesto> kardex(Long idRepuesto) {
        return movimientoRepository.findByRepuestoIdOrderByFechaDesc(idRepuesto);
    }

    /** Repuestos en cero o bajo el minimo: alimenta alertas del tablero. */
    public List<Repuesto> alertasStock() {
        return repository.findByActivoTrueAndStockLessThanEqualOrderByStockAsc();
    }

    public long contarBajoStock() {
        return alertasStock().size();
    }

    public long contar() {
        return repository.count();
    }
}
