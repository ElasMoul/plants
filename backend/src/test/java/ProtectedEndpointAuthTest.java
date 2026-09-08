import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.config.SecurityConfig;
import com.plantpal.shared.filter.AuthRateLimitFilter;
import com.plantpal.shared.filter.JwtAuthFilter;
import com.plantpal.shared.util.JwtUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@DisplayName("ProtectedEndpointAuthTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProtectedEndpointAuthTest {

  private static final String MALFORMED_BEARER = "Bearer not-a-valid-jwt";
  private static final int EXPECTED_DENIAL_STATUS = HttpStatus.UNAUTHORIZED.value();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final List<Endpoint> PROTECTED_ENDPOINTS =
      List.of(
          endpoint("BE-003", "GET", "/api/v1/users/me/preferences"),
          endpoint("BE-004", "PUT", "/api/v1/users/me/preferences"),
          endpoint("BE-005", "POST", "/api/v1/chat"),
          endpoint("BE-006", "POST", "/api/v1/chat/stream"),
          endpoint("BE-007", "GET", "/api/v1/dashboard"),
          endpoint("BE-008", "GET", "/api/v1/plants"),
          endpoint("BE-009", "POST", "/api/v1/plants"),
          endpoint("BE-010", "GET", "/api/v1/plants/{id}"),
          endpoint("BE-011", "PUT", "/api/v1/plants/{id}"),
          endpoint("BE-012", "DELETE", "/api/v1/plants/{id}"),
          endpoint("BE-013", "POST", "/api/v1/plants/from-identification"),
          endpoint("BE-014", "GET", "/api/v1/plantnet/projects"),
          endpoint("BE-015", "GET", "/api/v1/plantnet/languages"),
          endpoint("BE-016", "GET", "/api/v1/plantnet/quota"),
          endpoint("BE-017", "POST", "/api/v1/identifications/analyze"),
          endpoint("BE-018", "GET", "/api/v1/identifications"),
          endpoint("BE-019", "GET", "/api/v1/identifications/{id}"),
          endpoint("BE-020", "GET", "/api/v1/identifications/plant/{plantId}"),
          endpoint("BE-021", "POST", "/api/v1/identifications/{id}/cure-advice"),
          endpoint("BE-022", "POST", "/api/v1/identifications/{id}/care-plan/cards"),
          endpoint("BE-023", "GET", "/api/v1/identifications/{id}/species-match"),
          endpoint("BE-024", "POST", "/api/v1/identifications/{id}/resolve-species"),
          endpoint("BE-025", "GET", "/api/v1/identifications/{id}/plant-match"),
          endpoint("BE-026", "POST", "/api/v1/identifications/{id}/retry"),
          endpoint("BE-027", "POST", "/api/v1/identifications/{id}/resolve-plant"),
          endpoint("BE-028", "POST", "/api/v1/treatments"),
          endpoint("BE-029", "POST", "/api/v1/treatments/{id}/craft-plan"),
          endpoint("BE-030", "GET", "/api/v1/treatments/{id}"),
          endpoint("BE-031", "GET", "/api/v1/plants/{id}/active-treatment"),
          endpoint("BE-032", "GET", "/api/v1/plants/{id}/active-treatments"),
          endpoint("BE-033", "PATCH", "/api/v1/treatments/{id}/complete"),
          endpoint("BE-034", "POST", "/api/v1/treatments/{id}/regenerate-description"),
          endpoint("BE-035", "POST", "/api/v1/treatment-plans"),
          endpoint("BE-036", "GET", "/api/v1/treatment-plans/{id}"),
          endpoint("BE-037", "POST", "/api/v1/reminders"),
          endpoint("BE-038", "GET", "/api/v1/reminders"),
          endpoint("BE-039", "POST", "/api/v1/reminders/{id}/complete"),
          endpoint("BE-040", "DELETE", "/api/v1/reminders/{id}"),
          endpoint("BE-041", "POST", "/api/v1/care/done"),
          endpoint("BE-042", "GET", "/api/v1/care/plant/{plantId}"),
          endpoint("BE-043", "POST", "/api/v1/notifications/subscribe"),
          endpoint("BE-044", "GET", "/api/v1/species/{id}"),
          endpoint("BE-045", "GET", "/api/v1/species/mine"),
          endpoint("BE-046", "POST", "/api/v1/species/{id}/regenerate-description"));

  private final List<ExecutionCase> executionCases = new ArrayList<>();
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mockMvc;

  @BeforeAll
  void setUpSecurityStack() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestWebConfig.class);
    context.refresh();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterAll
  void writeCoverageEvidence() throws Exception {
    Path output =
        Path.of(
            System.getProperty(
                "authHardeningOutput", "target/auth-hardening/protected-endpoint-coverage.json"));
    Files.createDirectories(output.getParent());
    OBJECT_MAPPER
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            output.toFile(),
            Map.of(
                "schema_version",
                1,
                "suite",
                "ProtectedEndpointAuthTest",
                "cases",
                executionCases));
    context.close();
  }

  @ParameterizedTest(name = "{0} without credentials is denied")
  @MethodSource("protectedEndpoints")
  void shouldDenyRequestsWithoutCredentials(Endpoint endpoint) throws Exception {
    assertDenied(endpoint, "missing_credentials", null);
  }

  @ParameterizedTest(name = "{0} with a malformed bearer token is denied")
  @MethodSource("protectedEndpoints")
  void shouldDenyRequestsWithMalformedBearerToken(Endpoint endpoint) throws Exception {
    assertDenied(endpoint, "malformed_bearer", MALFORMED_BEARER);
  }

  Stream<Arguments> protectedEndpoints() {
    return PROTECTED_ENDPOINTS.stream().map(Arguments::of);
  }

  private void assertDenied(Endpoint endpoint, String credentialCase, String authorization)
      throws Exception {
    var requestBuilder = request(endpoint.method(), endpoint.requestPath());
    if (authorization != null) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
    }

    MvcResult result = mockMvc.perform(requestBuilder).andReturn();
    int actualStatus = result.getResponse().getStatus();
    executionCases.add(
        new ExecutionCase(
            endpoint.id(),
            endpoint.method().name(),
            endpoint.inventoryPath(),
            credentialCase,
            EXPECTED_DENIAL_STATUS,
            actualStatus,
            actualStatus == EXPECTED_DENIAL_STATUS));

    assertThat(actualStatus)
        .as("%s %s must deny %s", endpoint.method(), endpoint.inventoryPath(), credentialCase)
        .isEqualTo(EXPECTED_DENIAL_STATUS);
  }

  private static Endpoint endpoint(String id, String method, String path) {
    return new Endpoint(id, HttpMethod.valueOf(method), path);
  }

  private record Endpoint(String id, HttpMethod method, String inventoryPath) {

    private String requestPath() {
      return inventoryPath.replaceAll("\\{[^/]+}", "1");
    }

    @Override
    public String toString() {
      return id + " " + method + " " + inventoryPath;
    }
  }

  private record ExecutionCase(
      @JsonProperty("endpoint_id") String endpointId,
      String method,
      String path,
      @JsonProperty("credential_case") String credentialCase,
      @JsonProperty("expected_status") int expectedStatus,
      @JsonProperty("actual_status") int actualStatus,
      boolean passed) {}

  @Configuration
  @EnableWebMvc
  @Import(SecurityConfig.class)
  static class TestWebConfig {

    @Bean
    JwtAuthFilter jwtAuthFilter() {
      return new JwtAuthFilter(Mockito.mock(JwtUtil.class), Mockito.mock(UserDetailsService.class));
    }

    @Bean
    AuthRateLimitFilter authRateLimitFilter() {
      return new AuthRateLimitFilter(OBJECT_MAPPER, 10_000, 10_000);
    }

    @Bean
    EndpointSinkController endpointSinkController() {
      return new EndpointSinkController();
    }
  }

  @RestController
  static class EndpointSinkController {

    @RequestMapping("/api/v1/**")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept() {}
  }
}
