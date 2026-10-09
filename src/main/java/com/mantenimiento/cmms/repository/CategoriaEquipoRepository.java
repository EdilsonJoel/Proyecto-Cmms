package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.CategoriaEquipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaEquipoRepository extends JpaRepository<CategoriaEquipo, Long> {

    Optional<CategoriaEquipo> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    List<CategoriaEquipo> findAllByOrderByNombreAsc();
}
