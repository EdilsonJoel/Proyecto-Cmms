package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "tecnico",
        uniqueConstraints = @UniqueConstraint(name = "uk_tecnico_dni", columnNames = "dni"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Tecnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "\\d{8}", message = "El DNI debe tener exactamente 8 digitos")
    @Column(nullable = false, length = 8)
    private String dni;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String nombre;

    @NotBlank(message = "La especialidad es obligatoria")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String especialidad;

    @Pattern(regexp = "|\\d{9}", message = "El telefono debe tener 9 digitos")
    @Column(length = 9)
    private String telefono;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 120)
    @Column(length = 120)
    private String email;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    /** Cuenta de acceso vinculada; permite que el rol TECNICO vea "sus" ordenes. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public String getNombreCompleto() {
        return nombre;
    }
}
