package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.PlanPreventivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PlanPreventivoRepository
        extends org.springframework.data.jpa.repository.JpaRepository<PlanPreventivo, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<PlanPreventivo> {

    boolean existsByEquipoId(Long idEquipo);

    List<PlanPreventivo> findByActivoTrueOrderByProximaFechaAsc();

    List<PlanPreventivo> findByActivoTrueAndProximaFechaLessThanEqual(LocalDate fecha);

    List<PlanPreventivo> findAllByOrderByProximaFechaAsc();
}
