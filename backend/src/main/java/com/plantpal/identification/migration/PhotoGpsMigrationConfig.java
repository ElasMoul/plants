package com.plantpal.identification.migration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Orders Liquibase after {@link PhotoGpsCleaner} so the 042 changeset can reach storage. */
@Configuration
public class PhotoGpsMigrationConfig {

  @Bean
  static BeanFactoryPostProcessor liquibaseAfterPhotoGpsCleaner() {
    return beanFactory -> {
      if (!beanFactory.containsBeanDefinition("liquibase")) {
        return;
      }
      BeanDefinition liquibase = beanFactory.getBeanDefinition("liquibase");
      List<String> dependsOn = new ArrayList<>();
      if (liquibase.getDependsOn() != null) {
        dependsOn.addAll(List.of(liquibase.getDependsOn()));
      }
      dependsOn.add("photoGpsCleaner");
      liquibase.setDependsOn(dependsOn.toArray(String[]::new));
    };
  }
}
