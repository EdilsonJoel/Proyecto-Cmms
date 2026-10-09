package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Equipo o activo fisico que se mantiene: maquinas, compresores, bombas, motores...
 * Es el centro del sistema: contra el se abren ordenes y se programan preventivos.
 */
@Entity
@Table(name = "equipo",
        uniqueConstraints = @UniqueConstraint(name = "uk_equipo_codigo", columnNames = "codigo"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El codigo es obligatorio")
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nombre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaEquipo categoria;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @Size(max = 60)
    @Column(length = 60)
    private String fabricante;

    @Size(max = 60)
    @Column(length = 60)
    private String modelo;

    @Size(max = 60)
    @Column(length = 60)
    private String numeroSerie;

    @Column(name = "fecha_instalacion")
    private LocalDate fechaInstalacion;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private EstadoEquipo estado = EstadoEquipo.OPERATIVO;

    /** Un equipo critico detiene la produccion si falla: recibe prioridad alta. */
    @Builder.Default
    @Column(nullable = false)
    private boolean critico = false;

    @DecimalMin(value = "0", message = "El costo no puede ser negativo")
    @Column(precision = 12, scale = 2)
    private BigDecimal costoAdquisicion;

    @Size(max = 300)
    @Column(length = 300)
    private String observaciones;

    public boolean isOperativo() {
        return estado == EstadoEquipo.OPERATIVO;
    }

    public boolean isDeBaja() {
        return estado == EstadoEquipo.BAJA;
    }

    /** Dias transcurridos desde la instalacion; -1 si no tiene fecha. */
    public long getDiasDesdeInstalacion() {
        return fechaInstalacion == null ? -1 : ChronoUnit.DAYS.between(fechaInstalacion, LocalDate.now());
    }

    /** Antiguedad en anos completos, o -1 si no tiene fecha. */
    public int getAniosDeVida() {
        return fechaInstalacion == null ? -1 : (int) ChronoUnit.YEARS.between(fechaInstalacion, LocalDate.now());
    }
}
