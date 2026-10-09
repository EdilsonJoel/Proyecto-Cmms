package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.EstadoOrden;
import com.mantenimiento.cmms.model.OrdenTrabajo;
import com.mantenimiento.cmms.model.TipoOrden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrdenTrabajoRepository
        extends org.springframework.data.jpa.repository.JpaRepository<OrdenTrabajo, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<OrdenTrabajo> {

    Optional<OrdenTrabajo> findByNumeroIgnoreCase(String numero);

    long countByEstado(EstadoOrden estado);

    boolean existsByEquipoId(Long idEquipo);

    boolean existsByEquipoIdAndEstadoIn(Long idEquipo, Collection<EstadoOrden> estados);

    boolean existsByTecnicoId(Long idTecnico);

    long countByTecnicoIdAndEstado(Long idTecnico, EstadoOrden estado);

    List<OrdenTrabajo> findByEquipoIdOrderByFechaCreacionDesc(Long idEquipo);

    List<OrdenTrabajo> findByTecnicoIdAndEstadoInOrderByFechaCreacionDesc(Long idTecnico,
                                                                          Collection<EstadoOrden> estados);

    List<OrdenTrabajo> findByEstadoInOrderByPrioridadDescFechaProgramadaAsc(Collection<EstadoOrden> estados);

    List<OrdenTrabajo> findTop5ByOrderByFechaCreacionDesc();

    List<OrdenTrabajo> findByFechaCreacionBetween(LocalDateTime desde, LocalDateTime hasta);

    List<OrdenTrabajo> findByFechaProgramadaBeforeAndEstadoIn(java.time.LocalDate fecha,
                                                              Collection<EstadoOrden> estados);

    @Query("""
            select o from OrdenTrabajo o
            where lower(o.numero) like lower(concat('%', :filtro, '%'))
               or lower(o.titulo) like lower(concat('%', :filtro, '%'))
               or lower(o.equipo.nombre) like lower(concat('%', :filtro, '%'))
               or lower(o.equipo.codigo) like lower(concat('%', :filtro, '%'))
               or lower(coalesce(o.tecnico.nombre, '')) like lower(concat('%', :filtro, '%'))
            order by o.fechaCreacion desc
            """)
    Page<OrdenTrabajo> buscar(String filtro, Pageable pageable);
}
