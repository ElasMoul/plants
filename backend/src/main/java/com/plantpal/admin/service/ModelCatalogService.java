package com.plantpal.admin.service;

import com.plantpal.admin.dto.AdminDtos.ModelUpdate;
import com.plantpal.admin.dto.AdminDtos.ModelView;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ModelCatalogService {
  Page<ModelView> list(Pageable pageable);

  Map<String, Boolean> visibility(String capability);

  ModelView update(String id, ModelUpdate request, Long actorId);

  void validateSelection(String capability, String model);
}
