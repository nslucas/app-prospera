package com.example.prospera.Resources;

import com.example.prospera.DTO.AuthenticationDTO;
import com.example.prospera.DTO.LoginResponseDTO;
import com.example.prospera.DTO.RegisterDTO;
import com.example.prospera.Entities.User;
import com.example.prospera.Entities.UserRole;
import com.example.prospera.Infra.Security.AuthRateLimitService;
import com.example.prospera.Infra.Security.TokenService;
import com.example.prospera.Services.UserService;
import com.example.prospera.repositories.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("auth")
@SecurityRequirements
public class AuthenticationResource {
    private final AuthenticationManager authenticationManager;
    private final UserRepository repository;
    private final TokenService tokenService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimitService rateLimitService;

    public AuthenticationResource(AuthenticationManager authenticationManager,
                                  UserRepository repository,
                                  TokenService tokenService,
                                  UserService userService,
                                  PasswordEncoder passwordEncoder,
                                  AuthRateLimitService rateLimitService) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
        this.tokenService = tokenService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Validated AuthenticationDTO data, HttpServletRequest request) {
        String email = AuthRateLimitService.normalizeEmail(data.email());
        AuthRateLimitService.LoginAttempt attempt = rateLimitService.acquireLogin(request.getRemoteAddr(), email);
        try {
            var usernamePassword = new UsernamePasswordAuthenticationToken(email, data.password());
            var auth = authenticationManager.authenticate(usernamePassword);

            User user = (User) auth.getPrincipal();
            var token = tokenService.generateToken(user);
            rateLimitService.loginSucceeded(attempt);
            return ResponseEntity.ok(new LoginResponseDTO(token, user.getId(), user.getEmail(), user.getName(),
                    user.getLastName()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        } catch (Exception exception) {
            rateLimitService.loginSystemFailed(attempt);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Validated RegisterDTO data, HttpServletRequest request) {
        String email = AuthRateLimitService.normalizeEmail(data.email());
        rateLimitService.acquireRegistration(request.getRemoteAddr(), email);

        String encryptedPassword = passwordEncoder.encode(data.password());
        if (repository.findByEmail(email) != null) {
            return ResponseEntity.ok().build();
        }

        User newUser = new User(data.name(), data.lastName(), data.monthLimit(), email, encryptedPassword,
                UserRole.USER);
        newUser.setConnectionCode(userService.generateUniqueConnectionCode());
        repository.save(newUser);
        return ResponseEntity.ok().build();
    }
}
