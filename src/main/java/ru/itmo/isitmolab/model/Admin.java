package ru.itmo.isitmolab.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin")
public class Admin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false, unique = true)
    private String login;

    @Column(name = "pass_hash", nullable = false)
    private String passHash;

    @Column(name = "salt", nullable = false)
    private String salt;

    @Column(name = "creation_time", nullable = false, updatable = false,
            columnDefinition = "timestamp default now()")
    private LocalDateTime creationTime;
}
