package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/** Lugar fisico donde se ubican los equipos (planta, area, linea). */
@Entity
@Table(name = "ubicacion",
        uniqueConstraints = @UniqueConstraint(name = "uk_ubicacion_nombre", columnNames = "nombre"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Ubicacion {

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
}
