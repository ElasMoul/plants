package com.plantpal.chat.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.plantpal.chat.service.impl.GardenContextServiceImpl;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("GardenContextServiceImpl — Unit Tests")
class GardenContextServiceImplTest {

  private static final Long USER_ID = 1L;

  @Mock private PlantRepository plantRepository;

  private GardenContextServiceImpl gardenContextService;

  @Nested
  @DisplayName("buildGardenContext()")
  class BuildGardenContext {

    @Test
    @DisplayName("should format each active plant as '- nickname (label)'")
    void shouldFormatActivePlants() {
      gardenContextService = new GardenContextServiceImpl(plantRepository);
      Plant monstera =
          Plant.builder().id(1L).userId(USER_ID).nickname("Monty").commonName("Monstera").build();
      Plant unidentified = Plant.builder().id(2L).userId(USER_ID).nickname("Mystery Plant").build();
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(new PageImpl<>(List.of(monstera, unidentified)));

      String context = gardenContextService.buildGardenContext(USER_ID);

      assertThat(context).isEqualTo("- Monty (Monstera)\n- Mystery Plant (unknown species)");
    }

    @Test
    @DisplayName("should fall back to the free-text species field when commonName is absent")
    void shouldFallBackToSpecies() {
      gardenContextService = new GardenContextServiceImpl(plantRepository);
      Plant plant =
          Plant.builder().id(1L).userId(USER_ID).nickname("Fern").species("Nephrolepis").build();
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(new PageImpl<>(List.of(plant)));

      String context = gardenContextService.buildGardenContext(USER_ID);

      assertThat(context).isEqualTo("- Fern (Nephrolepis)");
    }

    @Test
    @DisplayName("should return the placeholder when the user has no active plants")
    void shouldReturnPlaceholderWhenEmpty() {
      gardenContextService = new GardenContextServiceImpl(plantRepository);
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(Page.empty());

      String context = gardenContextService.buildGardenContext(USER_ID);

      assertThat(context).isEqualTo("No plants in the garden yet.");
    }
  }

  @Nested
  @DisplayName("caching wiring (T-DEPLOY.2)")
  class CachingWiring {

    // Self-invocation caveat (documented on GardenContextService/ChatServiceImpl): Spring's
    // caching proxy only intercepts external calls, so the only way to verify the annotation is
    // actually in effect is a reflection check — an end-to-end Redis-backed assertion needs a
    // Spring context + Testcontainers, which can't run locally (see backend/BACKEND.md).
    @Test
    @DisplayName("buildGardenContext() should be annotated @Cacheable(\"garden\") keyed by userId")
    void shouldBeCacheableOnGarden() throws NoSuchMethodException {
      Method method = GardenContextServiceImpl.class.getMethod("buildGardenContext", Long.class);
      Cacheable cacheable = method.getAnnotation(Cacheable.class);

      assertThat(cacheable).isNotNull();
      assertThat(cacheable.value()).containsExactly("garden");
      assertThat(cacheable.key()).isEqualTo("#userId");
    }
  }
}
