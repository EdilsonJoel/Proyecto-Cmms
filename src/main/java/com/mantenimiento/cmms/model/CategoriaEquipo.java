package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/** Clasificacion de los equipos: compresores, bombas, motores, etc. */
@Entity
@Table(name = "categoria_equipo",
        uniqueConstraints = @UniqueConstraint(name = "uk_categoria_nombre", columnNames = "nombre"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class CategoriaEquipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String nombre;

    @Size(max = 200)
    @Column(length = 200)
    private String descripcion;

    /** Color hexadecimal usado en graficas y pastillas ("#2f6fed"). */
    @Pattern(regexp = "|#[0-9a-fA-F]{6}", message = "El color debe ser un hexagonal como #2f6fed")
    @Column(length = 7)
    private String color;
}
