package com.shipstream.hub;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {
    @Override
    @EntityGraph(attributePaths = {"sourceHub", "destinationHub"})
    List<Route> findAll();

    /** Direct route only - no multi-hop path-finding (deliberately out of scope, see Project-idea.md). */
    Optional<Route> findBySourceHubIdAndDestinationHubId(Long sourceHubId, Long destinationHubId);

    /** Used when deleting a hub: its routes (either direction) have to go first, or the FK blocks it. */
    long deleteBySourceHubIdOrDestinationHubId(Long sourceHubId, Long destinationHubId);
}
