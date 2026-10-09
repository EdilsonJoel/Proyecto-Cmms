package com.mantenimiento.cmms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "usuario",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_login", columnNames = "usuario"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "contrasena")
@EqualsAndHashCode(of = "id")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El usuario es obligatorio")
    @Size(min = 3, max = 30, message = "El usuario debe tener entre 3 y 30 caracteres")
    @Pattern(regexp = "[a-zA-Z0-9._-]+",
            message = "Solo se permiten letras, digitos, punto, guion bajo y guion")
    @Column(nullable = false, length = 30)
    private String usuario;

    /** Hash {@code sal:sha256}; nunca se guarda ni se muestra en claro. */
    @Column(nullable = false, length = 80)
    private String contrasena;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String nombre;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 120)
    @Column(length = 120)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultimo_acceso")
    private java.time.LocalDateTime ultimoAcceso;
}
