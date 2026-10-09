package com.mantenimiento.cmms;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base de las pruebas de integracion: arranca el contexto completo de Spring
 * sobre una base SQLite temporal (una por clase de prueba) con el seeder de
 * demostracion desactivado, para partir siempre de una base limpia.
 */
@SpringBootTest
public abstract class IntegracionBase {

    @org.springframework.beans.factory.annotation.Autowired
    protected com.mantenimiento.cmms.repository.CategoriaEquipoRepository categorias;
    @org.springframework.beans.factory.annotation.Autowired
    protected com.mantenimiento.cmms.repository.UbicacionRepository ubicaciones;
    @org.springframework.beans.factory.annotation.Autowired
    protected com.mantenimiento.cmms.repository.EquipoRepository equipos;
    @org.springframework.beans.factory.annotation.Autowired
    protected com.mantenimiento.cmms.repository.TecnicoRepository tecnicos;
    @org.springframework.beans.factory.annotation.Autowired
    protected com.mantenimiento.cmms.repository.RepuestoRepository repuestos;
    @org.springframework.beans.factory.annotation.Autowired
    protected com.mantenimiento.cmms.repository.OrdenTrabajoRepository ordenes;
    @org.springframework.beans.factory.annotation.Autowired
    protected com.mantenimiento.cmms.repository.MovimientoRepuestoRepository movimientos;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws Exception {
        Path carpeta = Files.createTempDirectory("cmms-test");
        carpeta.toFile().deleteOnExit();
        Path base = carpeta.resolve("prueba.db");

        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + base);
        registro.add("app.seeder.enabled", () -> "false");
        registro.add("spring.jpa.show-sql", () -> "false");
        registro.add("logging.level.org.hibernate.SQL", () -> "WARN");
        registro.add("logging.level.com.mantenimiento.cmms", () -> "WARN");
    }

    // ------------------------------------------------- datos de partida

    protected com.mantenimiento.cmms.model.CategoriaEquipo categoriaBase() {
        return categorias.findAll().stream().findFirst()
                .orElseGet(() -> categorias.save(
                        com.mantenimiento.cmms.model.CategoriaEquipo.builder()
                                .nombre("Mecanica")
                                .descripcion("Pruebas")
                                .color("#2a78d6")
                                .build()));
    }

    protected com.mantenimiento.cmms.model.Ubicacion ubicacionBase() {
        return ubicaciones.findAll().stream().findFirst()
                .orElseGet(() -> ubicaciones.save(
                        com.mantenimiento.cmms.model.Ubicacion.builder()
                                .nombre("Nave de pruebas")
                                .descripcion("Para tests")
                                .build()));
    }

    protected com.mantenimiento.cmms.model.Equipo equipoNuevo(String codigo) {
        return equipos.save(com.mantenimiento.cmms.model.Equipo.builder()
                .codigo(codigo)
                .nombre("Equipo " + codigo)
                .categoria(categoriaBase())
                .ubicacion(ubicacionBase())
                .fabricante("Prueba")
                .modelo("T-1")
                .estado(com.mantenimiento.cmms.model.EstadoEquipo.OPERATIVO)
                .critico(false)
                .build());
    }

    protected com.mantenimiento.cmms.model.Tecnico tecnicoNuevo(String dni, String nombre) {
        return tecnicos.save(com.mantenimiento.cmms.model.Tecnico.builder()
                .dni(dni)
                .nombre(nombre)
                .especialidad("Mecanica")
                .activo(true)
                .build());
    }

    protected com.mantenimiento.cmms.model.Repuesto repuestoNuevo(String codigo, int stock) {
        return repuestos.save(com.mantenimiento.cmms.model.Repuesto.builder()
                .codigo(codigo)
                .nombre("Repuesto " + codigo)
                .stock(stock)
                .stockMinimo(2)
                .unidad("pza")
                .precioCosto(new java.math.BigDecimal("25.00"))
                .ubicacionAlmacen("Estante T-1")
                .activo(true)
                .build());
    }
}
