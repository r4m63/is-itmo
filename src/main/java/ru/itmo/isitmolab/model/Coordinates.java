package ru.itmo.isitmolab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Entity
@Table(name = "coordinates")
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

    @OneToMany(mappedBy = "coordinates", fetch = FetchType.LAZY)
    private List<Vehicle> vehicles = new ArrayList<>();

}
