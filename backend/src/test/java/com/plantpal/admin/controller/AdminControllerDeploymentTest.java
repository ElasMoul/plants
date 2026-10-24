package com.plantpal.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.shared.config.DeploymentIdentityInfoContributor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdminController.deployment() — Unit Tests")
class AdminControllerDeploymentTest {

  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

  @Test
  @DisplayName("should return the stamped 40-hex revision inside the ApiResponse wrapper")
  void returnsStampedRevision() {
    var controller =
        new AdminController(null, null, new DeploymentIdentityInfoContributor(SHA, "", "dev"));

    var response = controller.deployment();

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData())
        .containsEntry("appIdentity", "plantpal")
        .containsEntry("revision", SHA)
        .containsEntry("environment", "dev");
  }

  @Test
  @DisplayName("should report a null revision when none or a malformed one is stamped")
  void malformedRevisionIsNull() {
    for (String bad : new String[] {"", "abc123", SHA.toUpperCase(), "z".repeat(40)}) {
      var controller =
          new AdminController(null, null, new DeploymentIdentityInfoContributor(bad, "", ""));

      assertThat(controller.deployment().getData()).containsEntry("revision", null);
    }
  }
}
