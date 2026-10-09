package com.mantenimiento.cmms.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Principio de error recuperable: todo fallo se convierte en
 * "que paso / por que / que hacer" con un codigo de diagnostico.
 * Nunca queda un mensaje suelto sin siguiente paso.
 */
class DiagnosticoTest {

    @Test
    @DisplayName("Una excepcion estructurada conserva sus cuatro bloques")
    void respetaLaEstructuraPropia() {
        NegocioException e = new NegocioException("REP-004",
                "Stock insuficiente del repuesto X",
                "El kardex no cubre la salida.",
                "Registra una entrada en Kardex.");

        Diagnostico.Estructura f = Diagnostico.de(e);
        assertEquals("REP-004", f.codigo());
        assertEquals("Stock insuficiente del repuesto X", f.quePaso());
        assertEquals("El kardex no cubre la salida.", f.porque());
        assertEquals("Registra una entrada en Kardex.", f.accion());
    }

    @Test
    @DisplayName("El stock insuficiente se deriva a REP-004 con accion de Kardex")
    void derivaStockInsuficiente() {
        Diagnostico.Estructura f = Diagnostico.de(
                new NegocioException("Stock insuficiente: solo hay 2 pza de RP-1."));

        assertEquals("REP-004", f.codigo());
        assertTrue(f.porque().toLowerCase().contains("kardex"));
        assertTrue(f.accion().contains("Kardex"));
    }

    @Test
    @DisplayName("Credenciales invalidas se deriva a SEG-001 con orientacion de acceso")
    void derivaCredenciales() {
        NegocioException e = new NegocioException(
                "Usuario o contrasena incorrectos. Intento 1 de 3.");
        Diagnostico.Estructura f = Diagnostico.de(e);

        assertEquals("SEG-001", f.codigo());
        assertTrue(f.porque().toLowerCase().contains("hash")
                || f.porque().toLowerCase().contains("coinciden"));
        assertFalse(f.accion().isBlank());
    }

    @Test
    @DisplayName("Falta de solucion al completar se deriva a OT-006")
    void derivaSolucionFaltante() {
        Diagnostico.Estructura f = Diagnostico.de(
                new NegocioException("Describe el trabajo realizado antes de completar."));

        assertEquals("OT-006", f.codigo());
        assertTrue(f.accion().contains("Solucion"));
    }

    @Test
    @DisplayName("Falta de tecnico se deriva a OT-003 con la accion correcta")
    void derivaTecnico() {
        Diagnostico.Estructura f = Diagnostico.de(
                new NegocioException("Asigna un tecnico antes de iniciar la orden."));

        assertEquals("OT-003", f.codigo());
        assertTrue(f.accion().toLowerCase().contains("tecnico"));
    }

    @Test
    @DisplayName("Transicion de estado invalida se deriva a OT-002 explicando el ciclo")
    void derivaTransicion() {
        Diagnostico.Estructura f = Diagnostico.de(
                new NegocioException("La orden OT-0007 ya fue cerrada y no admite ediciones."));

        assertEquals("OT-002", f.codigo());
        assertTrue(f.porque().contains("ABIERTA"));
    }

    @Test
    @DisplayName("Mensajes con acentos tambien se derivan (normalizacion)")
    void derivaConAcentos() {
        Diagnostico.Estructura f = Diagnostico.de(
                new NegocioException("La descripción no puede quedar vacía."));

        // sin regla especifica cae al generico, pero siempre con los tres bloques
        assertNotNull(f.codigo());
        assertFalse(f.porque().isBlank());
        assertFalse(f.accion().isBlank());
    }

    @Test
    @DisplayName("Un mensaje desconocido nunca deja bloques vacios (caso de fallo primero)")
    void fallbackNuncaVacio() {
        Diagnostico.Estructura f = Diagnostico.de(new NegocioException("Algo raro ocurrio."));

        assertEquals("NEG-001", f.codigo());
        assertEquals("Algo raro ocurrio.", f.quePaso());
        assertFalse(f.porque().isBlank());
        assertFalse(f.accion().isBlank());
    }

    @Test
    @DisplayName("Un fallo interno con causa se etiqueta como PERSIST-001")
    void derivaFalloConCausa() {
        NegocioException e = new NegocioException("No se pudo guardar el registro",
                new RuntimeException("database is locked"));

        Diagnostico.Estructura f = Diagnostico.de(e);
        assertEquals("PERSIST-001", f.codigo());
        assertTrue(f.porque().contains("database is locked"));
    }
}
