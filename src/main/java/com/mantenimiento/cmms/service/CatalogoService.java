package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.CategoriaEquipo;
import com.mantenimiento.cmms.model.Proveedor;
import com.mantenimiento.cmms.model.Ubicacion;
import com.mantenimiento.cmms.repository.CategoriaEquipoRepository;
import com.mantenimiento.cmms.repository.EquipoRepository;
import com.mantenimiento.cmms.repository.ProveedorRepository;
import com.mantenimiento.cmms.repository.RepuestoRepository;
import com.mantenimiento.cmms.repository.UbicacionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Catalogos auxiliares (categorias de equipo, ubicaciones y proveedores)
 * con sus reglas de unicidad y proteccion contra referencias existentes.
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CatalogoService {

    private final CategoriaEquipoRepository categoriaRepository;
    private final UbicacionRepository ubicacionRepository;
    private final ProveedorRepository proveedorRepository;
    private final EquipoRepository equipoRepository;
    private final RepuestoRepository repuestoRepository;

    // ------------------------------------------------------------ categorias

    public List<CategoriaEquipo> categorias() {
        return categoriaRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public CategoriaEquipo guardarCategoria(@Valid CategoriaEquipo categoria) {
        categoriaRepository.findByNombreIgnoreCase(categoria.getNombre()).ifPresent(existente -> {
            boolean esOtra = categoria.getId() == null || !existente.getId().equals(categoria.getId());
            if (esOtra) {
                throw new NegocioException("Ya existe una categoria llamada \"" + categoria.getNombre() + "\".");
            }
        });
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public void eliminarCategoria(Long id) {
        if (equipoRepository.existsByCategoriaId(id)) {
            throw new NegocioException("No se puede eliminar: hay equipos que pertenecen a esta categoria.");
        }
        categoriaRepository.deleteById(id);
        log.info("Categoria eliminada: id={}", id);
    }

    // ----------------------------------------------------------- ubicaciones

    public List<Ubicacion> ubicaciones() {
        return ubicacionRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public Ubicacion guardarUbicacion(@Valid Ubicacion ubicacion) {
        ubicacionRepository.findByNombreIgnoreCase(ubicacion.getNombre()).ifPresent(existente -> {
            boolean esOtra = ubicacion.getId() == null || !existente.getId().equals(ubicacion.getId());
            if (esOtra) {
                throw new NegocioException("Ya existe una ubicacion llamada \"" + ubicacion.getNombre() + "\".");
            }
        });
        return ubicacionRepository.save(ubicacion);
    }

    @Transactional
    public void eliminarUbicacion(Long id) {
        if (equipoRepository.existsByUbicacionId(id)) {
            throw new NegocioException("No se puede eliminar: hay equipos ubicados en este lugar.");
        }
        ubicacionRepository.deleteById(id);
        log.info("Ubicacion eliminada: id={}", id);
    }

    // ------------------------------------------------------------ proveedores

    public List<Proveedor> proveedores() {
        return proveedorRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public Proveedor guardarProveedor(@Valid Proveedor proveedor) {
        if (proveedor.getRuc() != null && !proveedor.getRuc().isBlank()) {
            proveedorRepository.findByRuc(proveedor.getRuc()).ifPresent(existente -> {
                boolean esOtro = proveedor.getId() == null || !existente.getId().equals(proveedor.getId());
                if (esOtro) {
                    throw new NegocioException("Ya existe un proveedor con RUC " + proveedor.getRuc());
                }
            });
        }
        return proveedorRepository.save(proveedor);
    }

    @Transactional
    public void eliminarProveedor(Long id) {
        if (repuestoRepository.existsByProveedorId(id)) {
            throw new NegocioException(
                    "No se puede eliminar: hay repuestos asociados a este proveedor.");
        }
        proveedorRepository.deleteById(id);
        log.info("Proveedor eliminado: id={}", id);
    }
}
