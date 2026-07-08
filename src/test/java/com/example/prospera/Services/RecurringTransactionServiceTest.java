package com.example.prospera.Services;

import com.example.prospera.DTO.RecurringOccurrenceRequest;
import com.example.prospera.Entities.*;
import com.example.prospera.repositories.RecurringOccurrenceRepository;
import com.example.prospera.repositories.RecurringTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTest {
    @Mock
    private RecurringTransactionRepository recurrenceRepository;
    @Mock
    private RecurringOccurrenceRepository occurrenceRepository;
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private AccountService accountService;
    @Mock
    private CardService cardService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private ExpenseService expenseService;

    @Test
    void monthlyRecurrenceClampsShortMonths() {
        RecurringTransaction recurrence = accountRecurrence();
        recurrence.setDayOfMonth(31);

        RecurringTransactionService service = service();

        List<LocalDate> dates = service.generateOccurrenceDates(recurrence,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertEquals(List.of(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31)
        ), dates);
    }

    @Test
    void materializeAccountIncomeCreatesTransactionAndOccurrence() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = accountRecurrence();
        Transaction transaction = new Transaction(20, TransactionType.INCOME, BigDecimal.valueOf(500),
                null, "Salary", 10, null, 1, 7);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(accountService.findUserAccount(1, 10)).thenReturn(new Account(10, "Checking", AccountType.CHECKING,
                BigDecimal.ZERO, "BRL", true, 1));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.empty());
        when(transactionService.createRecurringTransaction(1, 10, TransactionType.INCOME, BigDecimal.valueOf(500),
                java.time.LocalDateTime.of(2026, 6, 5, 12, 0), "Salary", 7)).thenReturn(transaction);
        when(occurrenceRepository.save(any(RecurringOccurrence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionService service = service();
        service.materialize(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5)));

        ArgumentCaptor<RecurringOccurrence> captor = ArgumentCaptor.forClass(RecurringOccurrence.class);
        verify(occurrenceRepository).save(captor.capture());
        assertEquals(RecurringOccurrenceStatus.MATERIALIZED, captor.getValue().getStatus());
        assertEquals(20, captor.getValue().getTransactionId());
        assertEquals(BigDecimal.valueOf(500), captor.getValue().getAmount());
    }

    @Test
    void materializeVariableAccountRecurrenceUsesCustomAmount() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = accountRecurrence();
        recurrence.setClassification(RecurringClassification.VARIABLE);
        Transaction transaction = new Transaction(20, TransactionType.INCOME, BigDecimal.valueOf(650),
                null, "Salary", 10, null, 1, 7);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(accountService.findUserAccount(1, 10)).thenReturn(new Account(10, "Checking", AccountType.CHECKING,
                BigDecimal.ZERO, "BRL", true, 1));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.empty());
        when(transactionService.createRecurringTransaction(1, 10, TransactionType.INCOME, BigDecimal.valueOf(650),
                java.time.LocalDateTime.of(2026, 6, 5, 12, 0), "Salary", 7)).thenReturn(transaction);
        when(occurrenceRepository.save(any(RecurringOccurrence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionService service = service();
        service.materialize(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5), BigDecimal.valueOf(650)));

        ArgumentCaptor<RecurringOccurrence> captor = ArgumentCaptor.forClass(RecurringOccurrence.class);
        verify(occurrenceRepository).save(captor.capture());
        assertEquals(RecurringOccurrenceStatus.MATERIALIZED, captor.getValue().getStatus());
        assertEquals(20, captor.getValue().getTransactionId());
        assertEquals(BigDecimal.valueOf(650), captor.getValue().getAmount());
    }

    @Test
    void materializeVariableCardRecurrenceUsesCustomAmount() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = cardRecurrence();
        recurrence.setClassification(RecurringClassification.VARIABLE);
        Expense expense = new Expense(30, "Gym", BigDecimal.valueOf(145), 1,
                null, "Monthly gym", 1, 10, 7);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(cardService.findUserCard(1, 10)).thenReturn(new Card(10, "Bank", "Credit", "Visa",
                "1234", BigDecimal.valueOf(1000), 20, 5, true, 1));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.empty());
        when(expenseService.createRecurringExpense(1, "Gym", BigDecimal.valueOf(145), 1,
                java.time.LocalDateTime.of(2026, 6, 5, 12, 0), "Monthly gym", 10, 7)).thenReturn(expense);
        when(occurrenceRepository.save(any(RecurringOccurrence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionService service = service();
        service.materialize(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5), BigDecimal.valueOf(145)));

        ArgumentCaptor<RecurringOccurrence> captor = ArgumentCaptor.forClass(RecurringOccurrence.class);
        verify(occurrenceRepository).save(captor.capture());
        assertEquals(RecurringOccurrenceStatus.MATERIALIZED, captor.getValue().getStatus());
        assertEquals(30, captor.getValue().getExpenseId());
        assertEquals(BigDecimal.valueOf(145), captor.getValue().getAmount());
    }

    @Test
    void materializeFixedRecurrenceRejectsCustomAmount() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = accountRecurrence();
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(accountService.findUserAccount(1, 10)).thenReturn(new Account(10, "Checking", AccountType.CHECKING,
                BigDecimal.ZERO, "BRL", true, 1));

        RecurringTransactionService service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.materialize(99,
                        new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5), BigDecimal.valueOf(650))));
        verifyNoInteractions(transactionService, expenseService);
        verify(occurrenceRepository, never()).save(any(RecurringOccurrence.class));
    }

    @Test
    void skippedOccurrenceCannotBeMaterialized() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = accountRecurrence();
        RecurringOccurrence skipped = new RecurringOccurrence(1, 99, LocalDate.of(2026, 6, 5),
                RecurringOccurrenceStatus.SKIPPED, null, null, 1);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(accountService.findUserAccount(1, 10)).thenReturn(new Account(10, "Checking", AccountType.CHECKING,
                BigDecimal.ZERO, "BRL", true, 1));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.of(skipped));

        RecurringTransactionService service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.materialize(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5))));
        verifyNoInteractions(transactionService);
        verify(occurrenceRepository, never()).save(any(RecurringOccurrence.class));
    }

    @Test
    void revertAccountOccurrenceDeletesTransactionAndResetsOccurrence() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = accountRecurrence();
        RecurringOccurrence materialized = new RecurringOccurrence(1, 99, LocalDate.of(2026, 6, 5),
                RecurringOccurrenceStatus.MATERIALIZED, BigDecimal.valueOf(500), 20, null, 1);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.of(materialized));
        when(occurrenceRepository.save(any(RecurringOccurrence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionService service = service();
        service.revert(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5)));

        ArgumentCaptor<RecurringOccurrence> captor = ArgumentCaptor.forClass(RecurringOccurrence.class);
        verify(transactionService).deleteRecurringTransaction(1, 20);
        verifyNoInteractions(expenseService);
        verify(occurrenceRepository).save(captor.capture());
        assertEquals(RecurringOccurrenceStatus.PENDING, captor.getValue().getStatus());
        assertNull(captor.getValue().getTransactionId());
        assertNull(captor.getValue().getExpenseId());
        assertNull(captor.getValue().getAmount());
    }

    @Test
    void revertCardOccurrenceDeletesExpenseAndResetsOccurrence() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = cardRecurrence();
        RecurringOccurrence materialized = new RecurringOccurrence(1, 99, LocalDate.of(2026, 6, 5),
                RecurringOccurrenceStatus.MATERIALIZED, BigDecimal.valueOf(120), null, 30, 1);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.of(materialized));
        when(occurrenceRepository.save(any(RecurringOccurrence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionService service = service();
        service.revert(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5)));

        ArgumentCaptor<RecurringOccurrence> captor = ArgumentCaptor.forClass(RecurringOccurrence.class);
        verify(expenseService).deleteRecurringExpense(1, 30);
        verifyNoInteractions(transactionService);
        verify(occurrenceRepository).save(captor.capture());
        assertEquals(RecurringOccurrenceStatus.PENDING, captor.getValue().getStatus());
        assertNull(captor.getValue().getTransactionId());
        assertNull(captor.getValue().getExpenseId());
        assertNull(captor.getValue().getAmount());
    }

    @Test
    void skippedOccurrenceCannotBeReverted() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = accountRecurrence();
        RecurringOccurrence skipped = new RecurringOccurrence(1, 99, LocalDate.of(2026, 6, 5),
                RecurringOccurrenceStatus.SKIPPED, null, null, 1);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.of(skipped));

        RecurringTransactionService service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.revert(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5))));
        verifyNoInteractions(transactionService, expenseService);
        verify(occurrenceRepository, never()).save(any(RecurringOccurrence.class));
    }

    @Test
    void pendingOccurrenceCannotBeReverted() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = accountRecurrence();
        RecurringOccurrence pending = new RecurringOccurrence(1, 99, LocalDate.of(2026, 6, 5),
                RecurringOccurrenceStatus.PENDING, null, null, 1);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.of(pending));

        RecurringTransactionService service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.revert(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5))));
        verifyNoInteractions(transactionService, expenseService);
        verify(occurrenceRepository, never()).save(any(RecurringOccurrence.class));
    }

    @Test
    void missingOccurrenceCannotBeReverted() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        RecurringTransaction recurrence = accountRecurrence();
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(recurrenceRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.of(recurrence));
        when(occurrenceRepository.findByRecurrenceIdAndOccurrenceDate(99, LocalDate.of(2026, 6, 5)))
                .thenReturn(Optional.empty());

        RecurringTransactionService service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.revert(99, new RecurringOccurrenceRequest(LocalDate.of(2026, 6, 5))));
        verifyNoInteractions(transactionService, expenseService);
        verify(occurrenceRepository, never()).save(any(RecurringOccurrence.class));
    }

    private RecurringTransaction accountRecurrence() {
        return new RecurringTransaction(99, "Salary", "Salary", RecurringTargetType.ACCOUNT_TRANSACTION,
                TransactionType.INCOME, BigDecimal.valueOf(500), RecurringFrequency.MONTHLY,
                LocalDate.of(2026, 1, 5), null, 5, null, 10, null, 7,
                null, RecurringClassification.FIXED, true, 1);
    }

    private RecurringTransaction cardRecurrence() {
        return new RecurringTransaction(99, "Gym", "Monthly gym", RecurringTargetType.CARD_EXPENSE,
                null, BigDecimal.valueOf(120), RecurringFrequency.MONTHLY,
                LocalDate.of(2026, 1, 5), null, 5, null, null, 10, 7,
                1, RecurringClassification.FIXED, true, 1);
    }

    private RecurringTransactionService service() {
        return new RecurringTransactionService(recurrenceRepository, occurrenceRepository, authenticatedUserService,
                accountService, cardService, categoryService, transactionService, expenseService);
    }
}
