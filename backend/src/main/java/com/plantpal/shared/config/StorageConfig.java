package com.plantpal.shared.config;

import com.cloudinary.Cloudinary;
import com.plantpal.shared.storage.CloudinaryFileStorageService;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;
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

  /**
   * Assembles the Cloudinary-backed store here (rather than {@code @Service} on the class) so the
   * service keeps a single mock-friendly constructor — a second {@code @Value} constructor left
   * Spring unable to instantiate the bean at all ("No default constructor found"). Fail-fast on a
   * missing CLOUDINARY_URL beats 500ing on the first photo upload.
   */
  @Bean
  @ConditionalOnProperty(name = "app.storage.type", havingValue = "cloudinary")
  public CloudinaryFileStorageService cloudinaryFileStorageService(
      @Value("${app.storage.cloudinary-url:${CLOUDINARY_URL:}}") String cloudinaryUrl,
      RedisTemplate<String, byte[]> byteRedisTemplate,
      StringRedisTemplate stringRedisTemplate) {
    if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
      throw new IllegalStateException(
          "app.storage.type=cloudinary but CLOUDINARY_URL is not set "
              + "(expected cloudinary://api_key:api_secret@cloud_name)");
    }
    Cloudinary cloudinary = new Cloudinary(cloudinaryUrl);
    RestClient deliveryClient =
        RestClient.builder()
            .baseUrl("https://res.cloudinary.com/" + cloudinary.config.cloudName + "/image/upload/")
            .build();
    return new CloudinaryFileStorageService(
        cloudinary, deliveryClient, byteRedisTemplate, stringRedisTemplate);
  }
}
