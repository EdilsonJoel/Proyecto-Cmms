package com.mantenimiento.cmms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cifrado de contrasenas: SHA-256 con sal unica por usuario, almacenado
 * en el formato "sal:hash" y con verificacion en dos direcciones.
 */
class SeguridadTest {

    @Test
    @DisplayName("El hash se guarda en formato sal:hash y no contiene la contrasena")
    void formatoDelHash() {
        String guardado = Seguridad.hashear("admin123");

        assertNotNull(guardado);
        assertTrue(guardado.contains(":"), "debe llevar el separador sal:hash");
        assertFalse(guardado.contains("admin123"), "la clave jamas se guarda en claro");
        String[] partes = guardado.split(":");
        assertEquals(2, partes.length);
        assertFalse(partes[0].isBlank());
        assertFalse(partes[1].isBlank());
    }

    @Test
    @DisplayName("La contrasena correcta coincide con su hash")
    void contrasenaCorrecta() {
        String guardado = Seguridad.hashear("jefe123");

        assertTrue(Seguridad.coincide("jefe123", guardado));
    }

    @Test
    @DisplayName("Una contrasena distinta no coincide")
    void contrasenaIncorrecta() {
        String guardado = Seguridad.hashear("jefe123");

        assertFalse(Seguridad.coincide("jefe124", guardado));
        assertFalse(Seguridad.coincide("", guardado));
        assertFalse(Seguridad.coincide(null, guardado));
    }

    @Test
    @DisplayName("La misma contrasena genera hashes distintos (sal diferente)")
    void salUnica() {
        String primero = Seguridad.hashear("admin123");
        String segundo = Seguridad.hashear("admin123");

        assertNotEquals(primero, segundo);
        assertTrue(Seguridad.coincide("admin123", primero));
        assertTrue(Seguridad.coincide("admin123", segundo));
    }

    @Test
    @DisplayName("Un hash corrupto o vacio no valida ninguna contrasena")
    void hashInvalido() {
        assertFalse(Seguridad.coincide("admin123", null));
        assertFalse(Seguridad.coincide("admin123", ""));
        assertFalse(Seguridad.coincide("admin123", "basura"));
    }

    @Test
    @DisplayName("El verificador tolera valores nulos o corruptos sin lanzar excepciones")
    void entradasInvalidas() {
        String guardado = Seguridad.hashear("admin123");
        assertFalse(Seguridad.coincide(null, guardado));
        assertFalse(Seguridad.coincide("admin123", null));
        assertFalse(Seguridad.coincide("admin123", "sin-separador"));
        assertEquals(64, guardado.substring(guardado.indexOf(':') + 1).length(),
                "SHA-256 en hexadecimal son 64 caracteres");
    }
}
