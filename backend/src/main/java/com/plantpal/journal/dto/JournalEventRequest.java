package com.plantpal.journal.dto;

import com.plantpal.journal.entity.JournalEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Used for both create and update (full replace). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalEventRequest {

  @NotNull(message = "Event type is required")
  private JournalEventType type;

  @NotNull(message = "Event date is required")
  private LocalDate eventDate;

  @Size(max = 1000, message = "Note must be at most 1000 characters")
  private String note;
}
