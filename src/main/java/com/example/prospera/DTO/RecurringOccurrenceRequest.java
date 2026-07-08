package com.example.prospera.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringOccurrenceRequest(LocalDate occurrenceDate, BigDecimal amount) {
    public RecurringOccurrenceRequest(LocalDate occurrenceDate) {
        this(occurrenceDate, null);
    }
}
