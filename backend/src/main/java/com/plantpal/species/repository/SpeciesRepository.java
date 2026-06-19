package com.plantpal.species.repository;

import com.plantpal.species.entity.Species;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeciesRepository extends JpaRepository<Species, Long> {

  Optional<Species> findByScientificName(String scientificName);

  boolean existsByScientificName(String scientificName);
}
