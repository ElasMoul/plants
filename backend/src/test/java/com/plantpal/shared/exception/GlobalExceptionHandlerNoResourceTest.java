package com.plantpal.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.shared.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("GlobalExceptionHandler — unmatched routes")
class GlobalExceptionHandlerNoResourceTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("an unmapped route answers 404, not 500")
  void unmappedRouteIs404() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleNoResource(
            new NoResourceFoundException(HttpMethod.GET, "api/v1/species/6/photos"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().isSuccess()).isFalse();
  }
}
