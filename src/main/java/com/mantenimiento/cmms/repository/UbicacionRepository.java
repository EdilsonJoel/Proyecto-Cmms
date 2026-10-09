package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.Ubicacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {

    Optional<Ubicacion> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    List<Ubicacion> findAllByOrderByNombreAsc();
}
