package com.example.prospera.Services;

import com.example.prospera.Entities.User;
import com.example.prospera.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserServiceTest {
    @Mock
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsAuthenticatedUserToAccessOwnData() {
        User user = authenticatedUser(1);
        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

        User result = service.requireAuthenticatedUser(1);

        assertSame(user, result);
    }

    @Test
    void rejectsAuthenticatedUserAccessingAnotherUsersData() {
        authenticatedUser(1);
        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

        assertThrows(AccessDeniedException.class, () -> service.requireAuthenticatedUser(2));
    }

    private User authenticatedUser(Integer id) {
        User user = new User(id, "User", "Test", BigDecimal.valueOf(1000), "user" + id + "@test.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return user;
    }
}
