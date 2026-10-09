package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Kardex: cada entrada, salida o ajuste de stock queda registrado con su motivo
 * y, cuando aplica, con la orden de trabajo que origino el movimiento.
 */
@Entity
@Table(name = "movimiento_repuesto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class MovimientoRepuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "repuesto_id", nullable = false)
    private Repuesto repuesto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimiento tipo;

    /** Cantidad siempre positiva; el signo lo define el tipo de movimiento. */
    @Column(nullable = false)
    private int cantidad;

    /** Stock resultante despues del movimiento (para auditar el kardex). */
    @Column(name = "stock_resultante", nullable = false)
    private int stockResultante;

    @Builder.Default
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Size(max = 200)
    @Column(length = 200)
    private String motivo;

    /** Orden que origino la salida de repuestos (null para entradas/ajustes). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "orden_id")
    private OrdenTrabajo orden;

    /** Usuario que registro el movimiento. */
    @Size(max = 80)
    @Column(length = 80)
    private String registradoPor;
}
