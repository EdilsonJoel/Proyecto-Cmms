package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    boolean existsByRuc(String ruc);

    java.util.Optional<Proveedor> findByRuc(String ruc);

    List<Proveedor> findAllByOrderByNombreAsc();
}
