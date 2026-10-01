package com.plantpal.identification.repository;

import com.plantpal.identification.entity.Identification;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentificationRepository extends JpaRepository<Identification, Long> {

  Page<Identification> findByPlantIdOrderByCreatedAtDesc(Long plantId, Pageable pageable);

  Page<Identification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  @Query(
      "SELECT i FROM Identification i WHERE i.id IN "
          + "(SELECT MAX(i2.id) FROM Identification i2 WHERE i2.plantId IN :plantIds GROUP BY i2.plantId)")
  List<Identification> findLatestPerPlant(@Param("plantIds") List<Long> plantIds);

  @Query(
      "SELECT new com.plantpal.identification.repository.PlantPhotoRow("
          + "p.id, p.nickname, i.id, i.photoUrl, i.dateTaken) "
          + "FROM Identification i, Plant p "
          + "WHERE i.plantId = p.id AND i.userId = :userId AND p.userId = :userId "
          + "AND p.speciesId = :speciesId AND p.status <> com.plantpal.plant.entity.PlantStatus.ARCHIVED "
          + "AND i.photoUrl IS NOT NULL "
          + "ORDER BY p.id ASC, i.dateTaken ASC, i.id ASC")
  List<PlantPhotoRow> findSpeciesPhotoRows(
      @Param("userId") Long userId, @Param("speciesId") Long speciesId);
}
