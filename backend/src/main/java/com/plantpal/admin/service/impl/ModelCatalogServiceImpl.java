package com.plantpal.admin.service.impl;

import com.plantpal.admin.dto.AdminDtos.ModelUpdate;
import com.plantpal.admin.dto.AdminDtos.ModelView;
import com.plantpal.admin.entity.AiModelSetting;
import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.admin.repository.AiModelSettingRepository;
import com.plantpal.admin.service.ModelCatalogService;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ModelCatalogServiceImpl implements ModelCatalogService {
  private final AiModelSettingRepository settings;
  private final AdminRepository audit;
  private final AnthropicClient anthropic;

  public ModelCatalogServiceImpl(
      AiModelSettingRepository settings, AdminRepository audit, AnthropicClient anthropic) {
    this.settings = settings;
    this.audit = audit;
    this.anthropic = anthropic;
  }

  @Override
  public Page<ModelView> list(Pageable pageable) {
    return settings.findAll(pageable).map(this::view);
  }

  @Override
  public Map<String, Boolean> visibility(String capability) {
    Map<String, Boolean> result = new LinkedHashMap<>();
    settings
        .findAll(PageRequest.of(0, 50, Sort.by("id")))
        .forEach(
            setting -> {
              if (setting.getId().startsWith(capability + ":")) {
                result.put(setting.getId().split(":")[1], setting.isVisible());
              }
            });
    return result;
  }

  @Override
  @Transactional
  public ModelView update(String id, ModelUpdate request, Long actorId) {
    AiModelSetting setting =
        settings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Model not found"));
    if (setting.getVersion() != request.version()) {
      throw new ValidationException(
          "This model was changed by another administrator. Refresh and try again.");
    }
    setting.setVisible(request.visible());
    settings.saveAndFlush(setting);
    audit.record(
        actorId, request.visible() ? "Model shown in Settings" : "Model hidden from Settings", id);
    return view(setting);
  }

  @Override
  public void validateSelection(String capability, String model) {
    var setting = settings.findById(capability + ":" + model);
    if (setting.isEmpty() || !setting.get().isVisible()) {
      throw new ValidationException(
          "This model is no longer offered in Settings. Choose another model.");
    }
    if (model.equals("ANTHROPIC_CLAUDE") && !anthropic.isAvailable()) {
      throw new ValidationException("Claude is not configured on this server.");
    }
  }

  private ModelView view(AiModelSetting setting) {
    String[] parts = setting.getId().split(":");
    boolean configured = !parts[1].equals("ANTHROPIC_CLAUDE") || anthropic.isAvailable();
    return new ModelView(
        setting.getId(), parts[0], parts[1], setting.isVisible(), configured, setting.getVersion());
  }
}
