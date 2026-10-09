package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.MovimientoRepuesto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoRepuestoRepository extends JpaRepository<MovimientoRepuesto, Long> {

    List<MovimientoRepuesto> findByRepuestoIdOrderByFechaDesc(Long idRepuesto);

    List<MovimientoRepuesto> findAllByOrderByFechaDesc();

    long countByOrdenId(Long idOrden);
}
