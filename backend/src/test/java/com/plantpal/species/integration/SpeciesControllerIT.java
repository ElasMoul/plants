package com.plantpal.species.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.GitHubModelsClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.species.entity.Species;
import com.plantpal.species.entity.SpeciesStatus;
import com.plantpal.species.repository.SpeciesRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("SpeciesController — Integration Tests")
class SpeciesControllerIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private SpeciesRepository speciesRepository;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;
  @MockBean private GitHubModelsClient gitHubModelsClient;
  @MockBean private DeepSeekClient deepSeekClient;
  @MockBean private OllamaClient ollamaClient;

  private String tokenA;

  @BeforeEach
  void setUp() {
    tokenA = (String) registerAndLogin(uniqueEmail(), "password123").get("token");
  }

  @Test
  @DisplayName("GET /{id} should return a species to any authenticated user (no ownership check)")
  void shouldReturnSpeciesToAnyAuthenticatedUser() {
    Species species = seedSpecies("Monstera deliciosa");

    // A second, unrelated user should still be able to read it — Species is shared, not owned.
    String tokenB = (String) registerAndLogin(uniqueEmail(), "password123").get("token");
    ResponseEntity<Map> response = getWithToken("/api/v1/species/" + species.getId(), tokenB);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    assertThat(data.get("scientificName")).isEqualTo("Monstera deliciosa");
  }

  @Test
  @DisplayName("GET /{id} should return 404 for a nonexistent species")
  void shouldReturn404ForNonexistentSpecies() {
    ResponseEntity<Map> response = getWithToken("/api/v1/species/999999", tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /mine should only include species the caller owns a plant of")
  void shouldOnlyReturnCallersOwnSpecies() {
    Species species = seedSpecies("Ficus lyrata");
    createPlantOfSpecies(tokenA, species.getId());

    String tokenB = (String) registerAndLogin(uniqueEmail(), "password123").get("token");

    ResponseEntity<Map> responseA = getWithToken("/api/v1/species/mine", tokenA);
    ResponseEntity<Map> responseB = getWithToken("/api/v1/species/mine", tokenB);

    @SuppressWarnings("unchecked")
    Map<String, Object> dataA = (Map<String, Object>) responseA.getBody().get("data");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> contentA = (List<Map<String, Object>>) dataA.get("content");
    assertThat(contentA).anyMatch(s -> "Ficus lyrata".equals(s.get("scientificName")));

    @SuppressWarnings("unchecked")
    Map<String, Object> dataB = (Map<String, Object>) responseB.getBody().get("data");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> contentB = (List<Map<String, Object>>) dataB.get("content");
    assertThat(contentB).noneMatch(s -> "Ficus lyrata".equals(s.get("scientificName")));
  }

  @Test
  @DisplayName("GET /api/v1/species/mine without a token should return 401")
  void shouldReturn401WhenUnauthenticated() {
    ResponseEntity<Map> response =
        restTemplate.getForEntity(url("/api/v1/species/mine"), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Species seedSpecies(String scientificName) {
    return speciesRepository.save(
        Species.builder().scientificName(scientificName).status(SpeciesStatus.ACTIVE).build());
  }

  private void createPlantOfSpecies(String token, Long speciesId) {
    // Plant has no public "create with speciesId" endpoint -- the real flow attaches speciesId
    // via the identification resolve-species/resolve-plant endpoints (T6.9), which need a real
    // AI-backed identification. Plant entity is updated directly here since this test is only
    // verifying the species-listing query, not the attach flow itself (covered elsewhere).
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.set("Content-Type", "application/json");
    HttpEntity<Object> entity = new HttpEntity<>(Map.of("nickname", "My Ficus"), headers);
    ResponseEntity<Map> response =
        restTemplate.exchange(url("/api/v1/plants"), HttpMethod.POST, entity, Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    int plantId = (int) data.get("id");
    attachSpeciesToPlant(plantId, speciesId);
  }

  @Autowired private com.plantpal.plant.repository.PlantRepository plantRepository;

  private void attachSpeciesToPlant(int plantId, Long speciesId) {
    com.plantpal.plant.entity.Plant plant = plantRepository.findById((long) plantId).orElseThrow();
    plant.setSpeciesId(speciesId);
    plantRepository.save(plant);
  }

  private Map<String, Object> registerAndLogin(String email, String password) {
    var request =
        Map.of("email", email, "password", password, "firstName", "Test", "lastName", "User");
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/register"), request, Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    return data;
  }

  private ResponseEntity<Map> getWithToken(String path, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange(url(path), HttpMethod.GET, entity, Map.class);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private String uniqueEmail() {
    return "it-" + UUID.randomUUID() + "@example.com";
  }
}
