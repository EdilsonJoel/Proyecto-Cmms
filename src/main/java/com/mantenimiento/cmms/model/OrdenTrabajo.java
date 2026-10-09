package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de trabajo: registro unico de una intervencion de mantenimiento.
 * Concentra el flujo (estados), la mano de obra y los repuestos utilizados,
 * de donde salen los costos del tablero y de los reportes.
 */
@Entity
@Table(name = "orden_trabajo",
        uniqueConstraints = @UniqueConstraint(name = "uk_orden_numero", columnNames = "numero"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"detalles", "equipo", "tecnico"})
@EqualsAndHashCode(of = "id")
public class OrdenTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numero correlativo impreso: OT-0001. */
    /**
     * Numero correlativo impreso (OT-0001). Se asigna siempre en
     * {@code OrdenService.crear}, por eso no lleva {@code @NotBlank} (la
     * validacion del metodo ocurre antes de poder generarlo).
     */
    @Size(max = 15)
    @Column(nullable = false, length = 15)
    private String numero;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "equipo_id", nullable = false)
    private Equipo equipo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tecnico_id")
    private Tecnico tecnico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoOrden tipo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Prioridad prioridad = Prioridad.MEDIA;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private EstadoOrden estado = EstadoOrden.ABIERTA;

    @NotBlank(message = "El titulo es obligatorio")
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String titulo;

    @Size(max = 2000)
    @Column(length = 2000)
    private String descripcion;

    /** Trabajo realizado / causa raiz encontrada al cerrar la orden. */
    @Size(max = 2000)
    @Column(length = 2000)
    private String solucion;

    @Builder.Default
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    /** Fecha comprometida de ejecucion; al vencerla la orden aparece como atrasada. */
    @Column(name = "fecha_programada")
    private LocalDate fechaProgramada;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    /** Horas hombre efectivamente dedicadas. */
    @DecimalMin(value = "0", message = "Las horas no pueden ser negativas")
    @Column(name = "horas_mano_obra", precision = 8, scale = 2)
    private BigDecimal horasManoObra;

    /** Costo de la mano de obra (S/); el total suma ademas los repuestos. */
    @DecimalMin(value = "0", message = "El costo no puede ser negativo")
    @Column(name = "costo_mano_obra", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal costoManoObra = BigDecimal.ZERO;

    /** Usuario que registro la orden (nombre en el momento de crearla). */
    @Size(max = 80)
    @Column(name = "creado_por", length = 80)
    private String creadoPor;

    @Builder.Default
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<DetalleRepuesto> detalles = new ArrayList<>();

    // ------------------------------------------------------------ calculos

    /** Costo de repuestos consumidos en esta orden. */
    public BigDecimal getCostoRepuestos() {
        return detalles.stream()
                .map(DetalleRepuesto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Costo total = mano de obra + repuestos. */
    public BigDecimal getCostoTotal() {
        BigDecimal manoObra = costoManoObra == null ? BigDecimal.ZERO : costoManoObra;
        return manoObra.add(getCostoRepuestos());
    }

    /** Dias transcurridos entre inicio y fin (o hasta hoy si sigue en curso). */
    public long getDuracionDias() {
        if (fechaInicio == null) {
            return 0;
        }
        LocalDateTime fin = fechaFin != null ? fechaFin : LocalDateTime.now();
        return Math.max(0, ChronoUnit.DAYS.between(fechaInicio, fin));
    }

    /** Horas transcurridas entre inicio y fin (o hasta hoy si sigue en curso). */
    public long getDuracionHoras() {
        if (fechaInicio == null) {
            return 0;
        }
        LocalDateTime fin = fechaFin != null ? fechaFin : LocalDateTime.now();
        return Math.max(0, ChronoUnit.HOURS.between(fechaInicio, fin));
    }

    /** Programada para pasado y aun no cerrada: aparece atrasada en el tablero. */
    public boolean isVencida() {
        return fechaProgramada != null
                && fechaProgramada.isBefore(LocalDate.now())
                && !estado.esFinal();
    }

    /** Vence en los proximos 7 dias (alerta preventiva). */
    public boolean isProximaAVencer() {
        return fechaProgramada != null
                && !estado.esFinal()
                && !fechaProgramada.isBefore(LocalDate.now())
                && !fechaProgramada.isAfter(LocalDate.now().plusDays(7));
    }
}
