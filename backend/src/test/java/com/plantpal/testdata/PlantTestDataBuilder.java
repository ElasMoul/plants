package com.plantpal.testdata;

import com.plantpal.plant.dto.CreatePlantRequest;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import java.time.LocalDate;

public final class PlantTestDataBuilder {

  private PlantTestDataBuilder() {}

  public static PlantBuilder aPlant() {
    return new PlantBuilder();
  }

  public static CreatePlantRequestBuilder aCreatePlantRequest() {
    return new CreatePlantRequestBuilder();
  }

  public static UpdatePlantRequestBuilder anUpdatePlantRequest() {
    return new UpdatePlantRequestBuilder();
  }

  public static final class PlantBuilder {

    private Long id = 1L;
    private Long userId = 1L;
    private String nickname = "My Monstera";
    private String species = "Monstera deliciosa";
    private String commonName = "Swiss Cheese Plant";
    private String location = "Living Room";
    private PlantStatus status = PlantStatus.ACTIVE;
    private LocalDate acquiredAt = LocalDate.of(2024, 1, 15);

    public PlantBuilder withId(Long id) {
      this.id = id;
      return this;
    }

    public PlantBuilder withUserId(Long userId) {
      this.userId = userId;
      return this;
    }

    public PlantBuilder withNickname(String nickname) {
      this.nickname = nickname;
      return this;
    }

    public PlantBuilder withStatus(PlantStatus status) {
      this.status = status;
      return this;
    }

    public Plant build() {
      return Plant.builder()
          .id(id)
          .userId(userId)
          .nickname(nickname)
          .species(species)
          .commonName(commonName)
          .location(location)
          .status(status)
          .acquiredAt(acquiredAt)
          .build();
    }
  }

  public static final class CreatePlantRequestBuilder {

    private String nickname = "My Monstera";
    private String location = "Living Room";
    private String notes = "Bought from the flower market";
    private LocalDate acquiredAt = LocalDate.of(2024, 1, 15);

    public CreatePlantRequestBuilder withNickname(String nickname) {
      this.nickname = nickname;
      return this;
    }

    public CreatePlantRequestBuilder withLocation(String location) {
      this.location = location;
      return this;
    }

    public CreatePlantRequest build() {
      CreatePlantRequest request = new CreatePlantRequest();
      request.setNickname(nickname);
      request.setLocation(location);
      request.setNotes(notes);
      request.setAcquiredAt(acquiredAt);
      return request;
    }
  }

  public static final class UpdatePlantRequestBuilder {

    private String nickname = "Updated Monstera";
    private String location = "Bedroom";

    public UpdatePlantRequestBuilder withNickname(String nickname) {
      this.nickname = nickname;
      return this;
    }

    public UpdatePlantRequestBuilder withLocation(String location) {
      this.location = location;
      return this;
    }

    public UpdatePlantRequest build() {
      UpdatePlantRequest request = new UpdatePlantRequest();
      request.setNickname(nickname);
      request.setLocation(location);
      return request;
    }
  }
}
