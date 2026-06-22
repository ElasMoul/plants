package com.plantpal.identification.client;

import com.plantpal.shared.exception.PlantPalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Primary
@Component
public class DeepSeekAnnotationClient implements VisionAnnotationClient {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekAnnotationClient.class);

  private final GitHubModelsClient gitHubModelsClient;

  public DeepSeekAnnotationClient(GitHubModelsClient gitHubModelsClient) {
    this.gitHubModelsClient = gitHubModelsClient;
  }

  @Override
  public String analyzeRegions(byte[] imageBytes, String mediaType) {
    // Two attempts: the first may fail with EOF if the Azure HTTP/2 connection
    // is closed by a GOAWAY after the parallel identifyPlant() call completes.
    // A retry establishes a fresh connection and succeeds. This is connection
    // resilience, not model substitution — a 429 is not retried, it propagates.
    Exception lastException = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        return gitHubModelsClient.analyzeRegions(imageBytes, mediaType);
      } catch (RestClientResponseException e) {
        if (e.getStatusCode().value() == 429) {
          throw new PlantPalException("Annotation rate limit reached — try again later", 429, e);
        }
        lastException = e;
        if (attempt < 2) {
          log.debug(
              "DeepSeek annotation attempt {} failed ({}), retrying", attempt, e.getMessage());
        }
      } catch (Exception e) {
        lastException = e;
        if (attempt < 2) {
          log.debug(
              "DeepSeek annotation attempt {} failed ({}), retrying", attempt, e.getMessage());
        }
      }
    }
    throw new PlantPalException(
        "Annotation unavailable after retries: "
            + (lastException != null ? lastException.getMessage() : "unknown"),
        500,
        lastException);
  }
}
