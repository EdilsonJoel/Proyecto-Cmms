package com.mantenimiento.cmms.service;

import com.mantenimiento.cmms.model.Rol;
import com.mantenimiento.cmms.model.Usuario;
import org.springframework.stereotype.Component;

/**
 * Mantiene el usuario autenticado durante la vida de la aplicacion.
 * El panel lateral y cada controlador consultan aqui para aplicar permisos.
 */
@Component
public class SesionService {

    private Usuario usuario;

    public void iniciar(Usuario usuario) {
        this.usuario = usuario;
    }

    public void cerrar() {
        this.usuario = null;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public boolean haySesion() {
        return usuario != null;
    }

    public String getNombreUsuario() {
        return usuario == null ? "" : usuario.getNombre();
    }

    public Rol getRol() {
        return usuario == null ? null : usuario.getRol();
    }

    /** Cierto si el rol actual es alguno de los indicados. */
    public boolean es(Rol... roles) {
        if (usuario == null) {
            return false;
        }
        for (Rol rol : roles) {
            if (usuario.getRol() == rol) {
                return true;
            }
        }
        return false;
    }

    /** Solo administradores gestionan usuarios; supervisores gestionan la operacion. */
    public boolean puedeGestionar() {
        return usuario != null && usuario.getRol().puedeGestionar();
    }

    public boolean puedeAdministrarUsuarios() {
        return usuario != null && usuario.getRol().puedeAdministrarUsuarios();
    }

    public boolean esTecnico() {
        return usuario != null && usuario.getRol().esTecnico();
    }
}
