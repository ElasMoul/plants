package com.plantpal.identification.repository;

import java.time.Instant;

public record PlantPhotoRow(
    Long plantId, String nickname, Long identificationId, String photoUrl, Instant dateTaken) {}
