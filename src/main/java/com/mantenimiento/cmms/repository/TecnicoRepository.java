package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.Tecnico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TecnicoRepository extends JpaRepository<Tecnico, Long> {

    @Query("""
            select t from Tecnico t
            where lower(t.nombre) like lower(concat('%', :filtro, '%'))
               or lower(t.dni) like lower(concat('%', :filtro, '%'))
               or lower(t.especialidad) like lower(concat('%', :filtro, '%'))
            order by t.nombre
            """)
    Page<Tecnico> buscar(String filtro, Pageable pageable);

    java.util.Optional<Tecnico> findByDni(String dni);

    boolean existsByDni(String dni);

    java.util.Optional<Tecnico> findByUsuarioId(Long idUsuario);
}
