package com.plantpal.shared.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.user.entity.User;
import com.plantpal.user.repository.UserRepository;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("DataExportReviewSeeder - Unit Tests")
class DataExportReviewSeederTest {

  @TempDir Path dir;

  private final UserRepository users = mock(UserRepository.class);
  private final PlantRepository plants = mock(PlantRepository.class);
  private final CareLogRepository careLogs = mock(CareLogRepository.class);
  private final IdentificationRepository identifications = mock(IdentificationRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);

  private DataExportReviewSeeder seeder() {
    return new DataExportReviewSeeder(
        users, plants, careLogs, identifications, encoder, dir.toString());
  }

  @Test
  @DisplayName("seeds two users with plants, care logs and scans")
  void seeds() {
    when(users.findByEmail(DataExportReviewSeeder.OWNER_EMAIL)).thenReturn(Optional.empty());
    when(users.save(any(User.class)))
        .thenAnswer(
            i -> {
              User u = i.getArgument(0);
              u.setId(7L);
              return u;
            });
    when(plants.save(any(Plant.class)))
        .thenAnswer(
            i -> {
              Plant p = i.getArgument(0);
              p.setId(9L);
              return p;
            });
    when(encoder.encode(any())).thenReturn("hash");

    seeder().run(null);

    verify(users, times(2)).save(any(User.class));
    verify(plants, times(4)).save(any(Plant.class));
    verify(careLogs, times(2)).save(any());
    verify(identifications, times(2)).save(any());
    assertThat(dir.toFile().list()).hasSize(2);
  }

  @Test
  @DisplayName("is idempotent when the owner already exists")
  void idempotent() {
    when(users.findByEmail(DataExportReviewSeeder.OWNER_EMAIL)).thenReturn(Optional.of(new User()));

    seeder().run(null);

    verify(users, never()).save(any());
  }
}
