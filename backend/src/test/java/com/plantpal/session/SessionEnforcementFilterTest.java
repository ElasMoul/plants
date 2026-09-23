package com.plantpal.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.session.config.SessionProperties;
import com.plantpal.session.entity.RevokedReason;
import com.plantpal.session.entity.SessionCheckResult;
import com.plantpal.session.service.SessionRegistryService;
import com.plantpal.session.web.NonRenewingRequestMatcher;
import com.plantpal.shared.config.SecurityConfig;
import com.plantpal.shared.filter.AuthRateLimitFilter;
import com.plantpal.shared.filter.JwtAuthFilter;
import com.plantpal.shared.util.JwtUtil;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Wave-3 acceptance evidence: with {@code app.session.enforcement-enabled=true} a session the
 * registry rejects (idle timeout, absolute cap, logout, password change, admin revocation) must not
 * reach a protected API, and the client must be told which reason applied. The mirror cases prove
 * the shipped default is genuinely inert — the same rejected session is admitted while the flag is
 * off (ADR-8), so this test also pins the wave-5 cutover boundary.
 *
 * <p>Deliberately a {@code *Test} (surefire), not an {@code *IT} (failsafe): it wires a standalone
 * MockMvc over the real {@link SecurityConfig} and filter with a mocked registry, so it needs no
 * Spring Boot context and no Testcontainers. The enforcement boundary is therefore verifiable on
 * any runner, including one where Docker is unavailable and the integration suite cannot start.
 */
@DisplayName("SessionEnforcementFilterTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionEnforcementFilterTest {

  // HMAC-SHA test key — base64 of a 45-byte string, matching app.jwt.secret's shape.
  private static final String TEST_SECRET =
      "dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWZvci1qd3Qtc2lnbmluZy0xMjM0NTY3OA==";
  private static final String USER_EMAIL = "grower@plantpal.test";
  private static final Long USER_ID = 42L;
  private static final int EXPECTED_DENIAL_STATUS = HttpStatus.UNAUTHORIZED.value();
  private static final int EXPECTED_ADMITTED_STATUS = HttpStatus.NO_CONTENT.value();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final JwtUtil JWT_UTIL = new JwtUtil();
  private static final SessionProperties SESSION_PROPERTIES = new SessionProperties();
  private static final SessionRegistryService SESSION_REGISTRY =
      Mockito.mock(SessionRegistryService.class);

  private static AnnotationConfigWebApplicationContext context;
  private static MockMvc mockMvc;
  private static String validToken;

  @BeforeAll
  void startContext() {
    ReflectionTestUtils.setField(JWT_UTIL, "secret", TEST_SECRET);
    ReflectionTestUtils.setField(JWT_UTIL, "expirationMs", 86_400_000L);
    ReflectionTestUtils.setField(JWT_UTIL, "sessionAbsoluteCapHours", 12);

    context = new AnnotationConfigWebApplicationContext();
    context.register(TestWebConfig.class);
    context.setServletContext(new MockServletContext());
    context.refresh();

    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    validToken = JWT_UTIL.generateToken(activeUser(), USER_ID);
  }

  @AfterAll
  void stopContext() {
    if (context != null) {
      context.close();
    }
  }

  @BeforeEach
  void resetRegistryAndEnforce() {
    Mockito.reset(SESSION_REGISTRY);
    SESSION_PROPERTIES.setEnforcementEnabled(true);
  }

  @ParameterizedTest(name = "a session revoked as {0} cannot call a protected API")
  @EnumSource(RevokedReason.class)
  void shouldDenyProtectedApiWhenSessionIsRejectedAndEnforcementIsOn(RevokedReason reason)
      throws Exception {
    Mockito.when(SESSION_REGISTRY.validateAndSlide(Mockito.anyString(), Mockito.any(Instant.class)))
        .thenReturn(SessionCheckResult.invalid(reason));

    MvcResult result = mockMvc.perform(protectedRequest()).andReturn();

    assertThat(result.getResponse().getStatus())
        .as("revoked session (%s) must not reach a protected API", reason)
        .isEqualTo(EXPECTED_DENIAL_STATUS);
    assertThat(result.getResponse().getHeader(JwtAuthFilter.SESSION_REVOKED_REASON_HEADER))
        .as("the client needs the reason to show reason-specific eviction copy")
        .isEqualTo(reason.name());
  }

  @Test
  @DisplayName("an active session reaches the protected API when enforcement is on")
  void shouldAdmitProtectedApiWhenSessionIsActiveAndEnforcementIsOn() throws Exception {
    Mockito.when(SESSION_REGISTRY.validateAndSlide(Mockito.anyString(), Mockito.any(Instant.class)))
        .thenReturn(SessionCheckResult.active(900L, 43_200L));

    MvcResult result = mockMvc.perform(protectedRequest()).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(EXPECTED_ADMITTED_STATUS);
  }

  @ParameterizedTest(
      name = "the same rejected session still passes while enforcement is inert ({0})")
  @EnumSource(RevokedReason.class)
  void shouldAdmitRejectedSessionWhileEnforcementIsInert(RevokedReason reason) throws Exception {
    SESSION_PROPERTIES.setEnforcementEnabled(false);
    Mockito.when(SESSION_REGISTRY.validateAndSlide(Mockito.anyString(), Mockito.any(Instant.class)))
        .thenReturn(SessionCheckResult.invalid(reason));

    MvcResult result = mockMvc.perform(protectedRequest()).andReturn();

    assertThat(result.getResponse().getStatus())
        .as("ADR-8: the shipped default must not sign anybody out")
        .isEqualTo(EXPECTED_ADMITTED_STATUS);
    assertThat(result.getResponse().getHeader(JwtAuthFilter.SESSION_REVOKED_REASON_HEADER))
        .isNull();
  }

  @Test
  @DisplayName("an unavailable registry fails closed once enforcement is on")
  void shouldFailClosedWhenRegistryUnavailableAndEnforcementIsOn() throws Exception {
    Mockito.when(SESSION_REGISTRY.validateAndSlide(Mockito.anyString(), Mockito.any(Instant.class)))
        .thenThrow(new RuntimeException("redis unavailable"));

    MvcResult result = mockMvc.perform(protectedRequest()).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(EXPECTED_DENIAL_STATUS);
  }

  @Test
  @DisplayName("an unavailable registry fails open while enforcement is inert")
  void shouldFailOpenWhenRegistryUnavailableWhileEnforcementIsInert() throws Exception {
    SESSION_PROPERTIES.setEnforcementEnabled(false);
    Mockito.when(SESSION_REGISTRY.validateAndSlide(Mockito.anyString(), Mockito.any(Instant.class)))
        .thenThrow(new RuntimeException("redis unavailable"));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/plants").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(EXPECTED_ADMITTED_STATUS);
  }

  @Test
  @DisplayName("the non-renewing status endpoint peeks instead of sliding the session")
  void shouldNotSlideSessionOnNonRenewingStatusEndpoint() throws Exception {
    Mockito.when(SESSION_REGISTRY.status(Mockito.anyString(), Mockito.any(Instant.class)))
        .thenReturn(SessionCheckResult.active(120L, 43_200L));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/auth/session")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(EXPECTED_ADMITTED_STATUS);
    Mockito.verify(SESSION_REGISTRY).status(Mockito.anyString(), Mockito.any(Instant.class));
    Mockito.verify(SESSION_REGISTRY, Mockito.never())
        .validateAndSlide(Mockito.anyString(), Mockito.any(Instant.class));
  }

  private static MockHttpServletRequestBuilder protectedRequest() {
    return get("/api/v1/plants").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken);
  }

  private static UserDetails activeUser() {
    return new User(USER_EMAIL, "unused", List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Configuration
  @EnableWebMvc
  @Import(SecurityConfig.class)
  static class TestWebConfig {
    @Bean
    ObjectMapper securityObjectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    JwtAuthFilter jwtAuthFilter() {
      UserDetailsService userDetailsService = Mockito.mock(UserDetailsService.class);
      Mockito.when(userDetailsService.loadUserByUsername(Mockito.anyString()))
          .thenReturn(activeUser());
      return new JwtAuthFilter(
          JWT_UTIL,
          userDetailsService,
          SESSION_REGISTRY,
          new NonRenewingRequestMatcher(),
          SESSION_PROPERTIES);
    }

    @Bean
    AuthRateLimitFilter authRateLimitFilter() {
      return new AuthRateLimitFilter(OBJECT_MAPPER, 10_000, 10_000);
    }

    @Bean
    EndpointSinkController endpointSinkController() {
      return new EndpointSinkController();
    }

    @Bean
    SessionStatusSinkController sessionStatusSinkController() {
      return new SessionStatusSinkController();
    }
  }

  @RestController
  static class EndpointSinkController {

    @RequestMapping("/api/v1/**")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void handle() {}
  }

  @RestController
  static class SessionStatusSinkController {

    @RequestMapping("/api/v1/auth/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void handle() {}
  }
}
