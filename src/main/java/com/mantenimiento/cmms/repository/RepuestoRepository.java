package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.Repuesto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RepuestoRepository extends JpaRepository<Repuesto, Long> {

    Optional<Repuesto> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByProveedorId(Long idProveedor);

    /** Repuestos en cero o por debajo del minimo: alimenta las alertas. */
    @Query("""
            select r from Repuesto r
            where r.activo = true and r.stock <= r.stockMinimo
            order by r.stock asc
            """)
    List<Repuesto> findByActivoTrueAndStockLessThanEqualOrderByStockAsc();

    @Query("""
            select r from Repuesto r
            where lower(r.codigo) like lower(concat('%', :filtro, '%'))
               or lower(r.nombre) like lower(concat('%', :filtro, '%'))
               or lower(coalesce(r.ubicacionAlmacen, '')) like lower(concat('%', :filtro, '%'))
               or lower(coalesce(r.proveedor.nombre, '')) like lower(concat('%', :filtro, '%'))
            order by r.nombre
            """)
    Page<Repuesto> buscar(String filtro, Pageable pageable);
}
