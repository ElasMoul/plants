package com.plantpal.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

@DisplayName("DeploymentIdentityInfoContributor — Unit Tests")
class DeploymentIdentityInfoContributorTest {

  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

  @Nested
  @DisplayName("identity()")
  class Identity {

    @Test
    @DisplayName("should report the stamped revision, deployment id and environment")
    void reportsStampedValues() {
      var contributor = new DeploymentIdentityInfoContributor(SHA, "dev-20260927-abc", "dev");

      assertThat(contributor.identity())
          .containsEntry("appIdentity", "plantpal")
          .containsEntry("revision", SHA)
          .containsEntry("deploymentId", "dev-20260927-abc")
          .containsEntry("environment", "dev");
    }

    @Test
    @DisplayName("should report null, never a default, when nothing is stamped")
    void unknownStaysNull() {
      var contributor = new DeploymentIdentityInfoContributor("", "", "");

      assertThat(contributor.identity())
          .containsEntry("appIdentity", "plantpal")
          .containsEntry("revision", null)
          .containsEntry("deploymentId", null)
          .containsEntry("environment", null);
    }

    @Test
    @DisplayName("should null a short or non-hex revision instead of echoing it")
    void rejectsMalformedRevision() {
      assertThat(new DeploymentIdentityInfoContributor("0123abc", "", "").identity())
          .containsEntry("revision", null);
      assertThat(new DeploymentIdentityInfoContributor(SHA.toUpperCase(), "", "").identity())
          .containsEntry("revision", null);
    }

    @Test
    @DisplayName("should null a deployment id carrying unsafe characters")
    void rejectsUnsafeDeploymentId() {
      assertThat(new DeploymentIdentityInfoContributor(SHA, "<script>", "dev").identity())
          .containsEntry("deploymentId", null);
    }
  }

  @Test
  @DisplayName("contribute() should publish the identity under the 'deployment' key")
  void contributesUnderDeploymentKey() {
    var builder = new Info.Builder();

    new DeploymentIdentityInfoContributor(SHA, "d1", "dev").contribute(builder);

    assertThat(builder.build().getDetails()).containsKey("deployment");
    @SuppressWarnings("unchecked")
    var deployment = (Map<String, Object>) builder.build().get("deployment");
    assertThat(deployment).containsEntry("revision", SHA);
  }
}
