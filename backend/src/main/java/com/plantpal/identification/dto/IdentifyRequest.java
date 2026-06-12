package com.plantpal.identification.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class IdentifyRequest {

  @NotEmpty(message = "At least one image is required")
  @Size(max = 5, message = "Maximum 5 images are allowed")
  private List<MultipartFile> images;

  private List<String> organs;

  private Long plantId;
}
