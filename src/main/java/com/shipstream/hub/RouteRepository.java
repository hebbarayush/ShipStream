package com.shipstream.hub;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {
    @Override
    @EntityGraph(attributePaths = {"sourceHub", "destinationHub"})
    List<Route> findAll();
}
