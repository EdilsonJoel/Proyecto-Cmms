package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/** Repuesto o pieza de repuesto del almacen de mantenimiento. */
@Entity
@Table(name = "repuesto",
        uniqueConstraints = @UniqueConstraint(name = "uk_repuesto_codigo", columnNames = "codigo"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Repuesto {

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

    @Size(max = 250)
    @Column(length = 250)
    private String descripcion;

    @Min(value = 0, message = "El stock no puede ser negativo")
    @Builder.Default
    @Column(nullable = false)
    private int stock = 0;

    @Min(value = 0, message = "El stock minimo no puede ser negativo")
    @Builder.Default
    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo = 0;

    /** Unidad de medida: pza, m, lt, kit... */
    @Builder.Default
    @Size(max = 10)
    @Column(length = 10)
    private String unidad = "pza";

    @DecimalMin(value = "0", message = "El precio no puede ser negativo")
    @Column(name = "precio_costo", precision = 12, scale = 2)
    private BigDecimal precioCosto;

    /** Estante o bahia donde se guarda. */
    @Size(max = 40)
    @Column(name = "ubicacion_almacen", length = 40)
    private String ubicacionAlmacen;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    public boolean isSinStock() {
        return stock <= 0;
    }

    /** Stock en cero o por debajo del minimo: dispara la alerta de reposicion. */
    public boolean isStockBajo() {
        return stock <= stockMinimo;
    }

    /** Valor total del stock actual. */
    public BigDecimal getValorStock() {
        BigDecimal precio = precioCosto == null ? BigDecimal.ZERO : precioCosto;
        return precio.multiply(BigDecimal.valueOf(stock));
    }
}
