package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/** Proveedor de repuestos y servicios. */
@Entity
@Table(name = "proveedor",
        uniqueConstraints = @UniqueConstraint(name = "uk_proveedor_ruc", columnNames = "ruc"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nombre;

    @Pattern(regexp = "|\\d{11}", message = "El RUC debe tener 11 digitos")
    @Column(length = 11)
    private String ruc;

    @Pattern(regexp = "|\\d{9}", message = "El telefono debe tener 9 digitos")
    @Column(length = 9)
    private String telefono;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 120)
    @Column(length = 120)
    private String email;
}
