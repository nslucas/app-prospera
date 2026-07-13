package com.example.prospera.Resources;

import com.example.prospera.DTO.AuthenticationDTO;
import com.example.prospera.DTO.RegisterDTO;
import com.example.prospera.Entities.User;
import com.example.prospera.Infra.Security.AuthRateLimitService;
import com.example.prospera.Infra.Security.TokenService;
import com.example.prospera.Services.UserService;
import com.example.prospera.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationResourceTest {
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository repository;
    @Mock
    private TokenService tokenService;
    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthRateLimitService rateLimitService;
    @Mock
    private HttpServletRequest request;

    @Test
    void publicRegistrationAlwaysCreatesUserRoleAndNormalizesEmail() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(passwordEncoder.encode("secret")).thenReturn("encoded-password");
        when(repository.findByEmail("user@example.com")).thenReturn(null);
        when(userService.generateUniqueConnectionCode()).thenReturn("ABCDEFGH");

        ResponseEntity<Void> response = resource().register(
                new RegisterDTO("Lucas", "Nunes", BigDecimal.valueOf(3000), " User@Example.COM ", "secret"),
                request);

        assertEquals(200, response.getStatusCode().value());
        verify(rateLimitService).acquireRegistration("203.0.113.10", "user@example.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("user@example.com", saved.getEmail());
        assertEquals("encoded-password", saved.getPassword());
        assertEquals("ABCDEFGH", saved.getConnectionCode());
        assertEquals(1, saved.getAuthorities().size());
        assertEquals("ROLE_USER", saved.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void duplicateRegistrationHashesPasswordAndReturnsSameSuccessWithoutWriting() {
        User existing = new User(1, "Existing", "User", BigDecimal.TEN, "user@example.com");
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(passwordEncoder.encode("secret")).thenReturn("discarded-hash");
        when(repository.findByEmail("user@example.com")).thenReturn(existing);

        ResponseEntity<Void> response = resource().register(
                new RegisterDTO("Other", "Name", BigDecimal.ONE, "USER@example.com", "secret"), request);

        assertEquals(200, response.getStatusCode().value());
        InOrder order = inOrder(passwordEncoder, repository);
        order.verify(passwordEncoder).encode("secret");
        order.verify(repository).findByEmail("user@example.com");
        verify(repository, never()).save(any());
        verifyNoInteractions(userService);
    }

    @Test
    void legacyRoleFieldIsIgnoredByRegistrationDto() throws Exception {
        RegisterDTO dto = new ObjectMapper().readValue("""
                {"name":"Lucas","lastName":"Nunes","monthLimit":3000,
                 "email":"user@example.com","password":"secret","role":"ADMIN"}
                """, RegisterDTO.class);

        assertEquals("user@example.com", dto.email());
        assertFalse(dto.toString().contains("ADMIN"));
    }

    @Test
    void successfulLoginClearsEmailFailureBucket() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.TEN, "user@example.com");
        AuthRateLimitService.LoginAttempt attempt = new AuthRateLimitService.LoginAttempt("email-key", true);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(rateLimitService.acquireLogin("203.0.113.10", "user@example.com")).thenReturn(attempt);
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(tokenService.generateToken(user)).thenReturn("token");

        ResponseEntity<?> response = resource().login(
                new AuthenticationDTO(" User@Example.com ", "secret"), request);

        assertEquals(200, response.getStatusCode().value());
        verify(rateLimitService).loginSucceeded(attempt);
        verify(rateLimitService, never()).loginSystemFailed(attempt);
    }

    @Test
    void authenticationFailureRetainsConsumedEmailPermit() {
        AuthRateLimitService.LoginAttempt attempt = new AuthRateLimitService.LoginAttempt("email-key", true);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(rateLimitService.acquireLogin("203.0.113.10", "user@example.com")).thenReturn(attempt);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        ResponseEntity<?> response = resource().login(new AuthenticationDTO("user@example.com", "wrong"), request);

        assertEquals(401, response.getStatusCode().value());
        verify(rateLimitService, never()).loginSucceeded(attempt);
        verify(rateLimitService, never()).loginSystemFailed(attempt);
    }

    @Test
    void unrelatedLoginFailureRefundsEmailPermit() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.TEN, "user@example.com");
        AuthRateLimitService.LoginAttempt attempt = new AuthRateLimitService.LoginAttempt("email-key", true);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(rateLimitService.acquireLogin("203.0.113.10", "user@example.com")).thenReturn(attempt);
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(tokenService.generateToken(user)).thenThrow(new IllegalStateException("token service unavailable"));

        ResponseEntity<?> response = resource().login(new AuthenticationDTO("user@example.com", "secret"), request);

        assertEquals(500, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst("Retry-After"));
        verify(rateLimitService).loginSystemFailed(attempt);
        verify(rateLimitService, never()).loginSucceeded(attempt);
    }

    private AuthenticationResource resource() {
        return new AuthenticationResource(authenticationManager, repository, tokenService, userService,
                passwordEncoder, rateLimitService);
    }
}
