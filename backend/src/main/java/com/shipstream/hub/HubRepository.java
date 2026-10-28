package com.shipstream.hub;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubRepository extends JpaRepository<Hub, Long> {
    Optional<Hub> findByName(String name);
}
