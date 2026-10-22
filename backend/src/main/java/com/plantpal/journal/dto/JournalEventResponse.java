package com.plantpal.journal.dto;

import com.plantpal.journal.entity.JournalEventType;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEventResponse {
  private Long id;
  private Long plantId;
  private JournalEventType type;
  private LocalDate eventDate;
  private String note;
}
