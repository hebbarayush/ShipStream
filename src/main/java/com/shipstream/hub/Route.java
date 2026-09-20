package com.shipstream.hub;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "routes")
@Getter
@NoArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_hub_id")
    private Hub sourceHub;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_hub_id")
    private Hub destinationHub;

    @Column(name = "distance_km", nullable = false)
    private int distanceKm;

    public Route(Hub sourceHub, Hub destinationHub, int distanceKm) {
        this.sourceHub = sourceHub;
        this.destinationHub = destinationHub;
        this.distanceKm = distanceKm;
    }
}
