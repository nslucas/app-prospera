package com.example.prospera.Services;

import com.example.prospera.Entities.User;
import com.example.prospera.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuthenticatedUserService {
    private final UserRepository userRepository;

    public AuthenticatedUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        if (principal instanceof UserDetails userDetails) {
            User user = userRepository.findByEmail(userDetails.getUsername());
            if (user != null) {
                return user;
            }
        }

        throw new AccessDeniedException("Authenticated user could not be resolved");
    }

    public User requireAuthenticatedUser(Integer requestedUserId) {
        User authenticatedUser = getAuthenticatedUser();
        if (!Objects.equals(authenticatedUser.getId(), requestedUserId)) {
            throw new AccessDeniedException("You can only access your own data");
        }
        return authenticatedUser;
    }
}
