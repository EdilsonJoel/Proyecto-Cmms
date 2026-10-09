package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.Tecnico;
import com.mantenimiento.cmms.repository.OrdenTrabajoRepository;
import com.mantenimiento.cmms.repository.TecnicoRepository;
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

import java.util.List;

/** Altas, bajas y busqueda del personal tecnico. */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TecnicoService {

    private static final int TAMANIO_PAGINA = 15;

    private final TecnicoRepository repository;
    private final OrdenTrabajoRepository ordenRepository;
    private final com.mantenimiento.cmms.repository.UsuarioRepository usuarioRepository;
    private final SesionService sesion;

    public Page<Tecnico> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio, Sort.by("nombre"));
        if (filtro == null || filtro.isBlank()) {
            return repository.findAll(pageable);
        }
        return repository.buscar(filtro.trim(), pageable);
    }

    /** Listado con filtro de texto y opcion de mostrar solo los activos. */
    public Page<Tecnico> listar(String filtro, boolean soloActivos, int pagina, int tamanio) {
        if (!soloActivos) {
            return listar(filtro, pagina, tamanio);
        }
        String t = filtro == null ? "" : filtro.trim().toLowerCase();
        List<Tecnico> filtrados = repository.findAll(Sort.by("nombre")).stream()
                .filter(Tecnico::isActivo)
                .filter(x -> t.isEmpty()
                        || x.getNombre().toLowerCase().contains(t)
                        || x.getDni().contains(t)
                        || x.getEspecialidad().toLowerCase().contains(t)
                        || (x.getEmail() != null && x.getEmail().toLowerCase().contains(t)))
                .toList();
        int inicio = Math.min(pagina * tamanio, filtrados.size());
        int fin = Math.min(inicio + tamanio, filtrados.size());
        return new org.springframework.data.domain.PageImpl<>(filtrados.subList(inicio, fin),
                pageableDe(pagina, tamanio), filtrados.size());
    }

    private Pageable pageableDe(int pagina, int tamanio) {
        return PageRequest.of(pagina, tamanio, Sort.by("nombre"));
    }

    /** Ordenes completadas a lo largo de la carrera del tecnico. */
    public long totalCompletadas(Long idTecnico) {
        return ordenRepository.countByTecnicoIdAndEstado(idTecnico,
                com.mantenimiento.cmms.model.EstadoOrden.COMPLETADA);
    }

    public List<Tecnico> activos() {
        return repository.findAll(Sort.by("nombre")).stream()
                .filter(Tecnico::isActivo)
                .toList();
    }

    public List<Tecnico> todos() {
        return repository.findAll(Sort.by("nombre"));
    }

    public Tecnico obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException("El tecnico ya no existe."));
    }

    /**
     * Ficha tecnico ligada a la cuenta con sesion activa (rol TECNICO).
     * Devuelve vacio si la cuenta no tiene ficha de tecnico vinculada.
     */
    public java.util.Optional<Tecnico> deLaSesion() {
        if (sesion.getUsuario() == null) {
            return java.util.Optional.empty();
        }
        return repository.findByUsuarioId(sesion.getUsuario().getId());
    }

    /** Carga actual: ordenes activas asignadas al tecnico. */
    public long cargaActiva(Long idTecnico) {
        return ordenRepository
                .findByTecnicoIdAndEstadoInOrderByFechaCreacionDesc(idTecnico,
                        List.of(com.mantenimiento.cmms.model.EstadoOrden.ABIERTA,
                                com.mantenimiento.cmms.model.EstadoOrden.EN_EJECUCION))
                .size();
    }

    @Transactional
    public Tecnico guardar(@Valid Tecnico tecnico) {
        repository.findByDni(tecnico.getDni()).ifPresent(existente -> {
            boolean esOtro = tecnico.getId() == null || !existente.getId().equals(tecnico.getId());
            if (esOtro) {
                throw new NegocioException("Ya existe un tecnico con DNI " + tecnico.getDni());
            }
        });

        // Resuelve la cuenta de acceso vinculada (o la limpia si no hay).
        if (tecnico.getUsuario() != null && tecnico.getUsuario().getId() != null) {
            tecnico.setUsuario(usuarioRepository.findById(tecnico.getUsuario().getId())
                    .orElseThrow(() -> new NegocioException("La cuenta de usuario seleccionada ya no existe.")));
        } else {
            tecnico.setUsuario(null);
        }

        Tecnico guardado = repository.save(tecnico);
        log.info("Tecnico guardado: {} {}", guardado.getDni(), guardado.getNombre());
        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {
        Tecnico tecnico = obtener(id);
        if (ordenRepository.existsByTecnicoId(id)) {
            throw new NegocioException(
                    "No se puede eliminar: " + tecnico.getNombre() + " tiene ordenes de trabajo registradas.");
        }
        repository.delete(tecnico);
        log.info("Tecnico eliminado: id={}", id);
    }
}
