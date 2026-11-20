package com.firstrep.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pilot_interest")
@Getter
@Setter
@NoArgsConstructor
public class PilotInterest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 200)
    private String organisation;

    @Column(length = 2000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
