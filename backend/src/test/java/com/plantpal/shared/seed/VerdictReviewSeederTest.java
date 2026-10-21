package com.plantpal.shared.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationVerdict;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerdictReviewSeeder - Unit Tests")
class VerdictReviewSeederTest {

  @Mock private UserRepository users;
  @Mock private IdentificationRepository identifications;
  @Mock private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("seeds admin, owner and four scans on an empty database")
  void seedsEverything() {
    when(users.findByEmail(any())).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("hash");
    when(users.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              u.setId(7L);
              return u;
            });

    new VerdictReviewSeeder(users, identifications, passwordEncoder).run(null);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(users, org.mockito.Mockito.times(2)).save(userCaptor.capture());
    assertThat(userCaptor.getAllValues())
        .extracting(User::getRole)
        .containsExactly(UserRole.ADMIN, UserRole.USER);
    ArgumentCaptor<Identification> scans = ArgumentCaptor.forClass(Identification.class);
    verify(identifications, org.mockito.Mockito.times(4)).save(scans.capture());
    List<Identification> saved = scans.getAllValues();
    assertThat(saved)
        .extracting(Identification::getVerdict)
        .containsExactly(
            null,
            IdentificationVerdict.RIGHT,
            IdentificationVerdict.WRONG,
            IdentificationVerdict.UNSURE);
    assertThat(saved.get(0).getVerdictAt()).isNull();
    assertThat(saved.get(2).getVerdictSpecies()).isEqualTo("Epipremnum aureum");
  }

  @Test
  @DisplayName("is idempotent when the owner already exists")
  void skipsWhenSeeded() {
    when(users.findByEmail(any())).thenReturn(Optional.of(new User()));

    new VerdictReviewSeeder(users, identifications, passwordEncoder).run(null);

    verify(users, never()).save(any());
    verify(identifications, never()).save(any());
  }
}
