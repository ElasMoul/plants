package com.plantpal.admin.entity;

import com.plantpal.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_model_settings")
@Getter
@Setter
public class AiModelSetting extends AuditableEntity {
  @Id private String id;

  @Column(nullable = false)
  private boolean visible;

  @Version private long version;
}
