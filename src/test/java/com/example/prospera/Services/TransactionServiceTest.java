package com.example.prospera.Services;

import com.example.prospera.DTO.TransactionRecord;
import com.example.prospera.Entities.*;
import com.example.prospera.Exceptions.ObjectNotFoundException;
import com.example.prospera.repositories.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private AccountService accountService;
    @Mock
    private CategoryService categoryService;

    @Test
    void createIncomeAppliesPositiveBalanceDelta() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        Account account = new Account(10, "Checking", AccountType.CHECKING, BigDecimal.ZERO, "BRL", true, 1);
        TransactionRecord record = new TransactionRecord(null, TransactionType.INCOME, BigDecimal.valueOf(100),
                null, "Salary", 10, null);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(accountService.findUserAccount(1, 10)).thenReturn(account);

        TransactionService service = new TransactionService(transactionRepository, authenticatedUserService, accountService,
                categoryService);
        service.create(record);

        verify(accountService).applyDelta(1, 10, BigDecimal.valueOf(100));
        verifyNoInteractions(categoryService);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void directCardPaymentTransactionIsRejected() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        TransactionRecord record = new TransactionRecord(null, TransactionType.CARD_PAYMENT, BigDecimal.valueOf(100),
                null, "Payment", 10, null);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);

        TransactionService service = new TransactionService(transactionRepository, authenticatedUserService, accountService,
                categoryService);

        assertThrows(IllegalArgumentException.class, () -> service.create(record));
        verifyNoInteractions(accountService);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void findByIdCannotReadAnotherUsersTransaction() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(transactionRepository.findByIdAndUserId(55, 1)).thenReturn(Optional.empty());
        TransactionService service = new TransactionService(transactionRepository, authenticatedUserService,
                accountService, categoryService);

        assertThrows(ObjectNotFoundException.class, () -> service.findAuthenticatedUserTransaction(55));

        verify(transactionRepository).findByIdAndUserId(55, 1);
        verify(transactionRepository, never()).findById(55);
    }

    @Test
    void directCardPaymentTransactionDeleteIsRejected() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        Transaction transaction = new Transaction(55, TransactionType.CARD_PAYMENT, BigDecimal.valueOf(100),
                LocalDateTime.of(2026, 6, 8, 12, 0), "Payment", 10, null, 1);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(transactionRepository.findByIdAndUserId(55, 1)).thenReturn(Optional.of(transaction));

        TransactionService service = new TransactionService(transactionRepository, authenticatedUserService, accountService,
                categoryService);

        assertThrows(IllegalArgumentException.class, () -> service.delete(55));
        verify(accountService, never()).applyDelta(any(), any(), any());
        verify(transactionRepository, never()).delete(any());
    }

    @Test
    void updateCardPaymentTransactionReversesOldDebitAndAppliesNewDebit() {
        Account account = new Account(10, "Checking", AccountType.CHECKING, BigDecimal.valueOf(1000),
                "BRL", true, 1);
        Transaction transaction = new Transaction(55, TransactionType.CARD_PAYMENT, BigDecimal.valueOf(100),
                LocalDateTime.of(2026, 6, 8, 12, 0), "Payment", 10, null, 1);
        LocalDateTime updatedDate = LocalDateTime.of(2026, 6, 9, 12, 0);
        when(accountService.findUserAccount(1, 10)).thenReturn(account);
        when(transactionRepository.findByIdAndUserId(55, 1)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionService service = new TransactionService(transactionRepository, authenticatedUserService, accountService,
                categoryService);
        Transaction updated = service.updateCardPaymentTransaction(1, 55, 10, BigDecimal.valueOf(300),
                updatedDate, "Updated payment");

        InOrder inOrder = inOrder(accountService);
        inOrder.verify(accountService).applyDelta(1, 10, BigDecimal.valueOf(100));
        inOrder.verify(accountService).applyDelta(1, 10, BigDecimal.valueOf(-300));
        assertEquals(10, updated.getAccountId());
        assertEquals(BigDecimal.valueOf(300), updated.getAmount());
        assertEquals(updatedDate, updated.getOccurredAt());
        assertEquals("Updated payment", updated.getDescription());
    }

    @Test
    void updateCardPaymentTransactionCanMoveDebitToAnotherAccount() {
        Account account = new Account(20, "Savings", AccountType.SAVINGS, BigDecimal.valueOf(1000),
                "BRL", true, 1);
        Transaction transaction = new Transaction(55, TransactionType.CARD_PAYMENT, BigDecimal.valueOf(100),
                LocalDateTime.of(2026, 6, 8, 12, 0), "Payment", 10, null, 1);
        when(accountService.findUserAccount(1, 20)).thenReturn(account);
        when(transactionRepository.findByIdAndUserId(55, 1)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionService service = new TransactionService(transactionRepository, authenticatedUserService, accountService,
                categoryService);
        Transaction updated = service.updateCardPaymentTransaction(1, 55, 20, BigDecimal.valueOf(250),
                LocalDateTime.of(2026, 6, 9, 12, 0), "Moved payment");

        InOrder inOrder = inOrder(accountService);
        inOrder.verify(accountService).applyDelta(1, 10, BigDecimal.valueOf(100));
        inOrder.verify(accountService).applyDelta(1, 20, BigDecimal.valueOf(-250));
        assertEquals(20, updated.getAccountId());
        assertEquals(BigDecimal.valueOf(250), updated.getAmount());
    }

    @Test
    void reverseCardPaymentTransactionRestoresBalance() {
        Transaction transaction = new Transaction(55, TransactionType.CARD_PAYMENT, BigDecimal.valueOf(100),
                LocalDateTime.of(2026, 6, 8, 12, 0), "Payment", 10, null, 1);
        when(transactionRepository.findByIdAndUserId(55, 1)).thenReturn(Optional.of(transaction));

        TransactionService service = new TransactionService(transactionRepository, authenticatedUserService, accountService,
                categoryService);
        Transaction reversed = service.reverseCardPaymentTransaction(1, 55);

        assertEquals(55, reversed.getId());
        verify(accountService).applyDelta(1, 10, BigDecimal.valueOf(100));
    }

    @Test
    void createExpenseWithIncomeCategoryIsRejected() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        TransactionRecord record = new TransactionRecord(null, TransactionType.EXPENSE, BigDecimal.valueOf(100),
                null, "Groceries", 10, null, 99);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(categoryService.requireActiveCategory(1, 99, CategoryType.EXPENSE))
                .thenThrow(new IllegalArgumentException("Category type must be EXPENSE"));

        TransactionService service = new TransactionService(transactionRepository, authenticatedUserService, accountService,
                categoryService);

        assertThrows(IllegalArgumentException.class, () -> service.create(record));
        verifyNoInteractions(accountService);
        verifyNoInteractions(transactionRepository);
    }
}
