package com.plantpal;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  // Singleton pattern: containers start once per JVM and stay running.
  // Spring context caching requires stable ports across test classes — @Container + @Testcontainers
  // restarts containers between classes, which invalidates the cached ApplicationContext.
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("plantpal_test")
          .withUsername("test")
          .withPassword("test");

  static final RedisContainer redis =
      new RedisContainer(DockerImageName.parse("redis:7-alpine"));

  static {
    postgres.start();
    redis.start();
  }

  @DynamicPropertySource
  static void overrideConnectionProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }
}
