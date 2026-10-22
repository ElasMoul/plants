package com.plantpal.plant.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.plant.service.impl.PlantServiceImpl;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@DisplayName("PlantServiceImpl — plants cache keys")
class PlantCacheKeyTest {

  private static final Pageable BY_NEWEST =
      PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
  private static final Pageable BY_NAME = PageRequest.of(0, 20, Sort.by("nickname"));

  @Test
  @DisplayName("the same page in a different sort order gets its own cache entry")
  void sortIsPartOfTheKey() throws NoSuchMethodException {
    Method method = PlantServiceImpl.class.getMethod("getUserPlants", Long.class, Pageable.class);

    assertThat(key(method, 7L, null, BY_NEWEST)).isEqualTo(key(method, 7L, null, BY_NEWEST));
    assertThat(key(method, 7L, null, BY_NEWEST)).isNotEqualTo(key(method, 7L, null, BY_NAME));
    assertThat(key(method, 7L, null, BY_NEWEST)).isNotEqualTo(key(method, 8L, null, BY_NEWEST));
  }

  @Test
  @DisplayName("the species-filtered list keys on species and sort too")
  void speciesListKey() throws NoSuchMethodException {
    Method method =
        PlantServiceImpl.class.getMethod("getUserPlants", Long.class, Long.class, Pageable.class);

    assertThat(key(method, 7L, 3L, BY_NEWEST)).isNotEqualTo(key(method, 7L, 3L, BY_NAME));
    assertThat(key(method, 7L, 3L, BY_NEWEST)).isNotEqualTo(key(method, 7L, 4L, BY_NEWEST));
  }

  @Test
  @DisplayName("the group-filtered list never collides with the species or unfiltered keys")
  void groupListKey() throws NoSuchMethodException {
    Method group =
        PlantServiceImpl.class.getMethod(
            "getUserPlantsInGroup", Long.class, Long.class, Pageable.class);
    Method species =
        PlantServiceImpl.class.getMethod("getUserPlants", Long.class, Long.class, Pageable.class);
    Method all = PlantServiceImpl.class.getMethod("getUserPlants", Long.class, Pageable.class);

    assertThat(key(group, 7L, 3L, BY_NEWEST)).isNotEqualTo(key(species, 7L, 3L, BY_NEWEST));
    assertThat(key(group, 7L, 3L, BY_NEWEST)).isNotEqualTo(key(all, 7L, null, BY_NEWEST));
    assertThat(key(group, 7L, 3L, BY_NEWEST)).isNotEqualTo(key(group, 7L, 4L, BY_NEWEST));
    assertThat(key(group, 7L, 3L, BY_NEWEST)).isNotEqualTo(key(group, 8L, 3L, BY_NEWEST));
  }

  private static Object key(Method method, Long userId, Long speciesId, Pageable pageable) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    context.setVariable("userId", userId);
    context.setVariable("speciesId", speciesId);
    context.setVariable("groupId", speciesId);
    context.setVariable("pageable", pageable);
    String expression = method.getAnnotation(Cacheable.class).key();
    return new SpelExpressionParser().parseExpression(expression).getValue(context);
  }
}
