package ru.itmo.isitmolab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "coordinates",
        uniqueConstraints = {
                @UniqueConstraint(name = "coordinates_xy_uniq", columnNames = {"x", "y"})
        }
)
public class Coordinates {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @DecimalMax(value = "613")
    @Column(name = "x", nullable = false)
    private Double x;

    @NotNull
    @DecimalMax(value = "962")
    @Column(name = "y", nullable = false)
    private Float y;
}
