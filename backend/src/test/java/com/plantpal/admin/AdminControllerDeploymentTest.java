package com.plantpal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.plantpal.admin.controller.AdminController;
import com.plantpal.admin.service.AdminService;
import com.plantpal.admin.service.ModelCatalogService;
import com.plantpal.shared.config.DeploymentIdentityInfoContributor;
import org.junit.jupiter.api.Test;

class AdminControllerDeploymentTest {
  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

  private AdminController controller(String revision) {
    return new AdminController(
        mock(AdminService.class),
        mock(ModelCatalogService.class),
        new DeploymentIdentityInfoContributor(revision, "", ""));
  }

  @Test
  void returnsStampedRevision() {
    var data = controller(SHA).deployment().getData();
    assertThat(data).containsEntry("revision", SHA).containsEntry("appIdentity", "plantpal");
  }

  @Test
  void unstampedOrMalformedRevisionStaysNull() {
    assertThat(controller("").deployment().getData()).containsEntry("revision", null);
    assertThat(controller("ABC123").deployment().getData()).containsEntry("revision", null);
  }
}
