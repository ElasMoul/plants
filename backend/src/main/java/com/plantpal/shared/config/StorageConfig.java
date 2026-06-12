package com.plantpal.shared.config;

import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StorageConfig implements WebMvcConfigurer {

  @Value("${app.storage.local-path:/tmp/plantpal/photos}")
  private String localPath;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String resourceLocation = Paths.get(localPath).toUri().toString();
    registry.addResourceHandler("/photos/**").addResourceLocations(resourceLocation);
  }
}
