package com.example.prospera.Services;

import com.example.prospera.Entities.User;
import com.example.prospera.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Test
    void findByIdRejectsAnotherUsersIdWithoutReadingThatUser() {
        when(authenticatedUserService.requireAuthenticatedUser(2))
                .thenThrow(new AccessDeniedException("You can only access your own data"));
        UserService service = new UserService(userRepository, authenticatedUserService);

        assertThrows(AccessDeniedException.class, () -> service.findAuthenticatedUserById(2));

        verifyNoInteractions(userRepository);
    }

    @Test
    void updateRejectsAnotherUsersIdWithoutWritingThatUser() {
        User update = new User();
        update.setId(2);
        when(authenticatedUserService.requireAuthenticatedUser(2))
                .thenThrow(new AccessDeniedException("You can only access your own data"));
        UserService service = new UserService(userRepository, authenticatedUserService);

        assertThrows(AccessDeniedException.class, () -> service.updateAuthenticatedUser(update));

        verifyNoInteractions(userRepository);
    }

    @Test
    void deleteRejectsAnotherUsersIdWithoutDeletingThatUser() {
        when(authenticatedUserService.requireAuthenticatedUser(2))
                .thenThrow(new AccessDeniedException("You can only access your own data"));
        UserService service = new UserService(userRepository, authenticatedUserService);

        assertThrows(AccessDeniedException.class, () -> service.deleteAuthenticatedUser(2));

        verifyNoInteractions(userRepository);
    }
}
