package com.example.prospera.DTO;

import com.example.prospera.Entities.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringOccurrenceRecord(Integer id, Integer recurrenceId, String recurrenceName,
                                        LocalDate occurrenceDate, BigDecimal amount,
                                        RecurringTargetType targetType, TransactionType transactionType,
                                        Integer accountId, Integer cardId, Integer categoryId,
                                        RecurringClassification classification,
                                        RecurringOccurrenceStatus status, Integer transactionId,
                                        Integer expenseId) {
    public RecurringOccurrenceRecord(RecurringTransaction recurrence, RecurringOccurrence occurrence,
                                     LocalDate occurrenceDate) {
        this(occurrence == null ? null : occurrence.getId(), recurrence.getId(), recurrence.getName(),
                occurrenceDate, amountFor(recurrence, occurrence), recurrence.getTargetType(), recurrence.getTransactionType(),
                recurrence.getAccountId(), recurrence.getCardId(), recurrence.getCategoryId(),
                recurrence.getClassification(),
                occurrence == null ? RecurringOccurrenceStatus.PENDING : occurrence.getStatus(),
                occurrence == null ? null : occurrence.getTransactionId(),
                occurrence == null ? null : occurrence.getExpenseId());
    }

    private static BigDecimal amountFor(RecurringTransaction recurrence, RecurringOccurrence occurrence) {
        return occurrence != null && occurrence.getAmount() != null ? occurrence.getAmount() : recurrence.getAmount();
    }
}
