package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Linea de repuesto utilizado dentro de una orden de trabajo. */
@Entity
@Table(name = "detalle_repuesto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class DetalleRepuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "orden_id", nullable = false)
    private OrdenTrabajo orden;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "repuesto_id", nullable = false)
    private Repuesto repuesto;

    @Column(nullable = false)
    private int cantidad;

    /** Precio copiado al momento de usarlo: historico, no cambia si el repuesto se revalua. */
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    public BigDecimal getSubtotal() {
        BigDecimal precio = precioUnitario == null ? BigDecimal.ZERO : precioUnitario;
        return precio.multiply(BigDecimal.valueOf(cantidad));
    }
}
