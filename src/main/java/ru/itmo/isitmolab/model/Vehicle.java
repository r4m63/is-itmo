package ru.itmo.isitmolab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDateTime;

@NamedEntityGraph(
        name = "Vehicle.withCoordinatesAdmin",
        attributeNodes = {
                @NamedAttributeNode("coordinates"),
                @NamedAttributeNode("admin")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(name = "creation_time", nullable = false, updatable = false)
    private LocalDateTime creationTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType type;

    @Positive
    @Column(name = "engine_power")
    private Integer enginePower;

    @Positive
    @Column(name = "number_of_wheels", nullable = false)
    private int numberOfWheels;

    @Positive
    private Integer capacity;

    @Positive
    @Column(name = "distance_travelled")
    private Integer distanceTravelled;

    @Positive
    @Column(name = "fuel_consumption", nullable = false)
    private float fuelConsumption;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false)
    private FuelType fuelType;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "coordinates_id", nullable = false)
    private Coordinates coordinates;

    @PrePersist
    public void prePersist() {
        if (creationTime == null) {
            creationTime = LocalDateTime.now();
        }
    }

}
