package com.shipstream.hub;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hubs")
@Getter
@NoArgsConstructor
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private int capacity;

    /** Coordinates, used to compute ETAs via routes and to simulate GPS movement between hubs. */
    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    public Hub(String name, String location, int capacity, double latitude, double longitude) {
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
