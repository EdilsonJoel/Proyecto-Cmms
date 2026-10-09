package com.mantenimiento.cmms.agente;

import com.mantenimiento.cmms.IntegracionBase;
import com.mantenimiento.cmms.exception.NegocioException;
import com.mantenimiento.cmms.model.EstadoEquipo;
import com.mantenimiento.cmms.service.EquipoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transparencia sin instrumentacion manual: el aspecto deja en la bitacora
 * toda consulta a la capa de servicios (fuente, parametros, registros y
 * duracion) y tambien los fallos, sin que el codigo del servicio lo pida.
 */
class TrazasAspectTest extends IntegracionBase {

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private Trazador trazador;

    private List<PasoTraza> desde(int corte) {
        List<PasoTraza> historial = trazador.historial();
        return historial.subList(Math.min(corte, historial.size()), historial.size());
    }

    @Test
    @DisplayName("Toda consulta a un servicio queda trazada sola: fuente, registros y duracion")
    void lasConsultasSeTrazanSolas() {
        int antes = trazador.total();

        equipoService.todos();

        List<PasoTraza> nuevas = desde(antes);
        assertFalse(nuevas.isEmpty(), "el aspecto debia registrar la consulta");

        PasoTraza consulta = nuevas.get(nuevas.size() - 1);
        assertEquals(TipoTraza.CONSULTA, consulta.tipo());
        assertEquals("equipos", consulta.fuente(), "el usuario debe ver la fuente amable");
        assertTrue(consulta.titulo().contains("EquipoService.todos"),
                "la traza identifica el metodo exacto: " + consulta.titulo());
        assertTrue(consulta.detalle().contains("registro"),
                "el resultado resume filas: " + consulta.detalle());
        assertTrue(consulta.detalle().contains("ms"),
                "la duracion siempre es visible: " + consulta.detalle());
    }

    @Test
    @DisplayName("Un fallo de servicio queda trazado con el criterio 'excepcion propagada'")
    void losFallosSeTrazan() {
        int antes = trazador.total();

        assertThrows(NegocioException.class,
                () -> equipoService.cambiarEstado(99_999_999L, EstadoEquipo.DETENIDO));

        List<PasoTraza> nuevas = desde(antes);
        assertTrue(nuevas.stream().anyMatch(p -> p.tipo() == TipoTraza.ERROR
                        && p.titulo().contains("EquipoService.cambiarEstado")
                        && "excepcion propagada al llamador".equals(p.criterio())),
                "el fallo debia quedar en la bitacora con su criterio de decision");
    }
}
