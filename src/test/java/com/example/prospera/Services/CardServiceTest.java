package com.example.prospera.Services;

import com.example.prospera.Entities.User;
import com.example.prospera.Exceptions.ObjectNotFoundException;
import com.example.prospera.repositories.CardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {
    @Mock
    private CardRepository cardRepository;
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private UserPreferenceService userPreferenceService;

    @Test
    void findByIdCannotReadAnotherUsersCard() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(cardRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.empty());
        CardService service = new CardService(cardRepository, authenticatedUserService, userPreferenceService);

        assertThrows(ObjectNotFoundException.class, () -> service.findAuthenticatedUserCard(10));

        verify(cardRepository).findByIdAndUserId(10, 1);
        verify(cardRepository, never()).findById(10);
    }
}
