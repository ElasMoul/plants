package com.plantpal.shared.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Jackson-deserializable wrapper for {@link PageImpl}.
 *
 * <p>{@code PageImpl} has no no-arg constructor, so Jackson cannot reconstruct it from Redis JSON.
 * This class provides a {@code @JsonCreator} that maps the serialized fields back to the correct
 * {@link PageImpl} constructor. It is a drop-in replacement for {@code Page<T>} in cached methods.
 */
@JsonIgnoreProperties(
    value = {"pageable", "sort", "last", "first", "empty", "numberOfElements"},
    ignoreUnknown = true)
public class RestPage<T> extends PageImpl<T> {

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public RestPage(
      @JsonProperty("content") List<T> content,
      @JsonProperty("number") int number,
      @JsonProperty("size") int size,
      @JsonProperty("totalElements") Long totalElements) {
    super(content, PageRequest.of(number, size), totalElements == null ? 0 : totalElements);
  }

  public RestPage(Page<T> page) {
    super(page.getContent(), page.getPageable(), page.getTotalElements());
  }
}
