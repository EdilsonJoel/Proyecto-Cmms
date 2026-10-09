package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.Rol;
import com.mantenimiento.cmms.model.Usuario;
import com.mantenimiento.cmms.repository.UsuarioRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Acceso y administracion de cuentas: autenticacion con hash de contrasena,
 * alta/edicion/baja de usuarios y reglas de negocio sobre roles.
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UsuarioService {

    private static final int TAMANIO_PAGINA = 15;

    private final UsuarioRepository repository;
    private final SesionService sesion;

    /**
     * Valida credenciales. El mensaje es el mismo para usuario inexistente y
     * contrasena incorrecta para no revelar que cuentas existen en el sistema.
     */
    public Usuario autenticar(String login, String contrasena) {
        if (login == null || login.isBlank() || contrasena == null || contrasena.isBlank()) {
            throw new NegocioException("Ingresa tu usuario y tu contrasena.");
        }
        Usuario usuario = repository.findByUsuarioIgnoreCase(login.trim())
                .orElseThrow(() -> new NegocioException("Usuario o contrasena incorrectos."));
        if (!Seguridad.coincide(contrasena, usuario.getContrasena())) {
            throw new NegocioException("Usuario o contrasena incorrectos.");
        }
        if (!usuario.isActivo()) {
            throw new NegocioException("Tu cuenta esta desactivada. Contacta al administrador.");
        }
        return usuario;
    }

    @Transactional
    public void registrarAcceso(Usuario usuario) {
        usuario.setUltimoAcceso(java.time.LocalDateTime.now());
        repository.save(usuario);
    }

    /** Todas las cuentas ordenadas por login (para combos de vinculacion). */
    public java.util.List<Usuario> todos() {
        return repository.findAll(Sort.by("usuario").ascending());
    }

    public Page<Usuario> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio, Sort.by("usuario").ascending());
        if (filtro == null || filtro.isBlank()) {
            return repository.findAll(pageable);
        }
        String t = filtro.trim();
        return repository.findAll((raiz, q, cb) -> cb.or(
                cb.like(cb.lower(raiz.get("usuario")), "%" + t.toLowerCase() + "%"),
                cb.like(cb.lower(raiz.get("nombre")), "%" + t.toLowerCase() + "%"),
                cb.like(cb.lower(raiz.get("rol")), "%" + t.toUpperCase() + "%")), pageable);
    }

    /**
     * Crea o actualiza una cuenta. La contrasena llega en claro: si esta vacia al
     * editar se conserva la anterior; si viene, se vuelve a hashear con sal nueva.
     */
    @Transactional
    public Usuario guardar(@Valid Usuario usuario, String contrasenaPlano) {
        repository.findByUsuarioIgnoreCase(usuario.getUsuario()).ifPresent(existente -> {
            boolean esOtraCuenta = usuario.getId() == null
                    || !existente.getId().equals(usuario.getId());
            if (esOtraCuenta) {
                throw new NegocioException("Ya existe una cuenta con el usuario \"" + usuario.getUsuario() + "\".");
            }
        });

        boolean esNueva = usuario.getId() == null;
        if (esNueva) {
            if (contrasenaPlano == null || contrasenaPlano.length() < 6) {
                throw new NegocioException("La contrasena debe tener al menos 6 caracteres.");
            }
            usuario.setContrasena(Seguridad.hashear(contrasenaPlano));
        } else if (contrasenaPlano != null && !contrasenaPlano.isBlank()) {
            if (contrasenaPlano.length() < 6) {
                throw new NegocioException("La contrasena debe tener al menos 6 caracteres.");
            }
            usuario.setContrasena(Seguridad.hashear(contrasenaPlano));
        } else {
            usuario.setContrasena(repository.findById(usuario.getId())
                    .map(Usuario::getContrasena)
                    .orElseThrow(() -> new NegocioException("La cuenta ya no existe.")));
        }

        if (usuario.getRol() == null) {
            usuario.setRol(Rol.TECNICO);
        }
        Usuario guardado = repository.save(usuario);
        log.info("Usuario guardado: {} ({})", guardado.getUsuario(), guardado.getRol());
        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario objetivo = repository.findById(id)
                .orElseThrow(() -> new NegocioException("La cuenta ya no existe."));

        if (sesion.getUsuario() != null && sesion.getUsuario().getId().equals(id)) {
            throw new NegocioException("No puedes eliminar la cuenta con la que iniciaste sesion.");
        }
        if (objetivo.getRol() == Rol.ADMIN && esElUnicoAdmin(id)) {
            throw new NegocioException("No se puede eliminar el unico administrador del sistema.");
        }
        repository.delete(objetivo);
        log.info("Usuario eliminado: {}", objetivo.getUsuario());
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Usuario objetivo = repository.findById(id)
                .orElseThrow(() -> new NegocioException("La cuenta ya no existe."));
        if (!activo && sesion.getUsuario() != null && sesion.getUsuario().getId().equals(id)) {
            throw new NegocioException("No puedes desactivar la cuenta con la que iniciaste sesion.");
        }
        if (!activo && objetivo.getRol() == Rol.ADMIN && esElUnicoAdmin(id)) {
            throw new NegocioException("No se puede desactivar el unico administrador del sistema.");
        }
        objetivo.setActivo(activo);
        repository.save(objetivo);
    }

    public long contar() {
        return repository.count();
    }

    private boolean esElUnicoAdmin(Long id) {
        return repository.findAll().stream()
                .filter(u -> u.getRol() == Rol.ADMIN && u.isActivo())
                .count() <= 1
                && repository.findById(id).map(u -> u.getRol() == Rol.ADMIN).orElse(false);
    }
}
