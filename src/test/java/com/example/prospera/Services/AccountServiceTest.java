package com.example.prospera.Services;

import com.example.prospera.DTO.AccountRecord;
import com.example.prospera.Entities.*;
import com.example.prospera.Exceptions.ObjectNotFoundException;
import com.example.prospera.repositories.AccountRepository;
import com.example.prospera.repositories.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserPreferenceService userPreferenceService;

    @Test
    void createAccountWithOpeningBalanceCreatesAdjustmentTransaction() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        AccountRecord record = new AccountRecord(null, "Checking", AccountType.CHECKING,
                BigDecimal.valueOf(500), "brl", true);
        Account savedAccount = new Account(10, "Checking", AccountType.CHECKING,
                BigDecimal.valueOf(500), "BRL", true, 1);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountService service = new AccountService(accountRepository, authenticatedUserService, transactionRepository,
                userPreferenceService);
        service.create(record);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertEquals(TransactionType.ADJUSTMENT, transactionCaptor.getValue().getType());
        assertEquals(BigDecimal.valueOf(500), transactionCaptor.getValue().getAmount());
        assertEquals(10, transactionCaptor.getValue().getAccountId());
    }

    @Test
    void findByIdCannotReadAnotherUsersAccount() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(accountRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.empty());
        AccountService service = new AccountService(accountRepository, authenticatedUserService, transactionRepository,
                userPreferenceService);

        assertThrows(ObjectNotFoundException.class, () -> service.findAuthenticatedUserAccount(10));

        verify(accountRepository).findByIdAndUserId(10, 1);
        verify(accountRepository, never()).findById(10);
    }
}
