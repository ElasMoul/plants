package com.plantpal.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

@DisplayName("API docs exposure by profile")
class ApiDocsExposureTest {

  private static java.util.Properties load(String resource) {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource(resource));
    return yaml.getObject();
  }

  @Test
  @DisplayName("production profile disables Swagger UI and the OpenAPI document")
  void prodDisablesApiDocs() {
    var prod = load("application-prod.yml");

    assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
  }

  @Test
  @DisplayName("base and dev configuration leave them enabled")
  void baseAndDevKeepApiDocs() {
    assertThat(load("application.yml").getProperty("springdoc.swagger-ui.path"))
        .isEqualTo("/swagger-ui.html");
    assertThat(load("application-dev.yml").getProperty("springdoc.swagger-ui.enabled")).isNull();
    assertThat(load("application-dev.yml").getProperty("springdoc.api-docs.enabled")).isNull();
  }
}
