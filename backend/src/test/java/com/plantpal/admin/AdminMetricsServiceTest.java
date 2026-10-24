package com.plantpal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.plantpal.admin.dto.AdminDtos.Scans;
import com.plantpal.admin.dto.AdminDtos.UsageDay;
import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.admin.service.impl.AdminServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminMetricsServiceTest {
  @Test
  void costIsRateTimesRequestsWhenConfigured() {
    var repo = mock(AdminRepository.class);
    when(repo.scanCounts(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new Scans(0, 0, 0, 0));
    when(repo.usageDays(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new UsageDay("2034-05-10", 5, 2), new UsageDay("2034-05-11", 3, 1)));
    var service = new AdminServiceImpl(null, repo, null);
    ReflectionTestUtils.setField(service, "aiCostPerRequest", new BigDecimal("0.002"));

    var cost = service.metrics(LocalDate.of(2034, 5, 10), LocalDate.of(2034, 5, 11)).cost();

    assertThat(cost.estimated()).isEqualByComparingTo("0.016");
    assertThat(cost.perRequestRate()).isEqualByComparingTo("0.002");
    assertThat(cost.basis()).startsWith("estimate");
  }
}
