package com.plantpal.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.plantpal.admin.dto.AdminDtos.ModelUpdate;
import com.plantpal.admin.entity.AiModelSetting;
import com.plantpal.admin.repository.*;
import com.plantpal.admin.service.impl.ModelCatalogServiceImpl;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.identification.client.DeepSeekDirectClient;
import com.plantpal.shared.exception.ValidationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelCatalogServiceTest {
  @Mock private DeepSeekDirectClient deepSeekDirect;
  @Mock private AiModelSettingRepository settings;
  @Mock private AdminRepository audit;
  @Mock private AnthropicClient anthropic;
  @InjectMocks private ModelCatalogServiceImpl service;

  @Test
  void rejectsHiddenUnknownAndUnconfiguredSelections() {
    var model = new AiModelSetting();
    model.setId("VISION:ANTHROPIC_CLAUDE");
    when(settings.findById(model.getId())).thenReturn(Optional.of(model));
    assertThatThrownBy(() -> service.validateSelection("VISION", "ANTHROPIC_CLAUDE"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no longer offered");
    model.setVisible(true);
    assertThatThrownBy(() -> service.validateSelection("VISION", "ANTHROPIC_CLAUDE"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("not configured");
    assertThatThrownBy(() -> service.validateSelection("VISION", "UNKNOWN"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void staleModelUpdateDoesNotWriteOrAudit() {
    var model = new AiModelSetting();
    model.setId("VISION:PLANTNET");
    model.setVersion(4);
    when(settings.findById(model.getId())).thenReturn(Optional.of(model));
    assertThatThrownBy(() -> service.update(model.getId(), new ModelUpdate(true, 3L), 1L))
        .isInstanceOf(ValidationException.class);
    verify(settings, never()).saveAndFlush(any());
    verifyNoInteractions(audit);
  }
}
