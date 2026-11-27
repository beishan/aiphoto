package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aiphoto.entity.User;
import com.aiphoto.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserImageDisplayTest {
    @Test
    void savesOnlyAuthenticatedUsersPreference() {
        UserRepository repository = mock(UserRepository.class);
        User first = new User();
        first.setId(1L);
        first.setUsername("first");
        User second = new User();
        second.setId(2L);
        when(repository.findByUsername("first")).thenReturn(Optional.of(first));
        when(repository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        UserService service = new UserService(repository, null, null, null, null);

        assertThat(first.getHideImages()).isTrue();
        var result = service.updateCurrentImageDisplay("first", false);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getHideImages()).isFalse();
        assertThat(second.getHideImages()).isTrue();
        verify(repository).save(first);
        verify(repository, never()).save(second);
    }

    @Test
    void rejectsMissingPreferenceBeforeSaving() {
        UserRepository repository = mock(UserRepository.class);
        UserService service = new UserService(repository, null, null, null, null);
        assertThatThrownBy(() -> service.updateCurrentImageDisplay("first", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
