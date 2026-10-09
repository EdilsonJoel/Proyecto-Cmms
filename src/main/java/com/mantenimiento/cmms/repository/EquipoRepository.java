package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.EstadoEquipo;
import com.mantenimiento.cmms.model.Equipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {

    Optional<Equipo> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCategoriaId(Long idCategoria);

    boolean existsByUbicacionId(Long idUbicacion);

    long countByEstado(EstadoEquipo estado);

    List<Equipo> findByEstado(EstadoEquipo estado);

    @Query("""
            select e from Equipo e
            where lower(e.codigo) like lower(concat('%', :filtro, '%'))
               or lower(e.nombre) like lower(concat('%', :filtro, '%'))
               or lower(coalesce(e.numeroSerie, '')) like lower(concat('%', :filtro, '%'))
               or lower(e.categoria.nombre) like lower(concat('%', :filtro, '%'))
               or lower(e.ubicacion.nombre) like lower(concat('%', :filtro, '%'))
            order by e.nombre
            """)
    Page<Equipo> buscar(String filtro, Pageable pageable);
}
