package com.mantenimiento.cmms.repository;

import com.mantenimiento.cmms.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository
        extends org.springframework.data.jpa.repository.JpaRepository<Usuario, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByUsuarioIgnoreCase(String usuario);

    boolean existsByUsuarioIgnoreCase(String usuario);

    boolean existsByRol(com.mantenimiento.cmms.model.Rol rol);

    List<Usuario> findAllByOrderByUsuarioAsc();
}
