package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.DetalleRepuesto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleRepuestoRepository extends JpaRepository<DetalleRepuesto, Long> {

    boolean existsByRepuestoId(Long idRepuesto);
}
