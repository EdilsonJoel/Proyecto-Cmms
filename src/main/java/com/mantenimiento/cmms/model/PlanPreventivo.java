package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Plan de mantenimiento preventivo: define cada cuanto se debe atender un equipo.
 * Cuando la fecha proxima llega (o se vence), el sistema genera automaticamente
 * la orden de trabajo correspondiente.
 */
@Entity
@Table(name = "plan_preventivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class PlanPreventivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "equipo_id", nullable = false)
    private Equipo equipo;

    /** Nombre de la tarea: "Cambio de aceite", "Revision de rodamientos"... */
    @Size(max = 120)
    @Column(length = 120)
    private String nombre;

    @Min(value = 1, message = "La frecuencia debe ser al menos 1 dia")
    @Column(name = "frecuencia_dias", nullable = false)
    private int frecuenciaDias;

    /** Horas hombre estimadas para planificar la carga de los tecnicos. */
    @DecimalMin(value = "0")
    @Column(name = "horas_estimadas", precision = 8, scale = 2)
    private BigDecimal horasEstimadas;

    @NotNull(message = "La fecha proxima es obligatoria")
    @Column(name = "proxima_fecha", nullable = false)
    private LocalDate proximaFecha;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tecnico_id")
    private Tecnico tecnico;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Size(max = 300)
    @Column(length = 300)
    private String observaciones;

    /** Fecha en que se genero por ultima vez una orden desde este plan. */
    @Column(name = "ultima_orden")
    private LocalDate ultimaOrden;

    public boolean isVencido() {
        return activo && proximaFecha.isBefore(LocalDate.now());
    }

    /** Vence hoy o en los proximos dias. */
    public boolean isProximo() {
        return activo && !isVencido()
                && !proximaFecha.isAfter(LocalDate.now().plusDays(7));
    }

    /** Dias restantes (negativos si ya vencio). */
    public long getDiasRestantes() {
        return ChronoUnit.DAYS.between(LocalDate.now(), proximaFecha);
    }

    /** Titulo por defecto de la orden que genera este plan. */
    public String getTituloOrden() {
        String base = (nombre == null || nombre.isBlank())
                ? "Mantenimiento preventivo programado" : nombre;
        return base + " - " + equipo.getNombre();
    }
}
