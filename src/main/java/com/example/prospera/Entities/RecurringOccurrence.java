package com.example.prospera.Entities;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_occurrence")
public class RecurringOccurrence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer recurrenceId;
    private LocalDate occurrenceDate;
    @Enumerated(EnumType.STRING)
    private RecurringOccurrenceStatus status;
    private BigDecimal amount;
    private Integer transactionId;
    private Integer expenseId;
    private Integer userId;

    public RecurringOccurrence() {
    }

    public RecurringOccurrence(Integer id, Integer recurrenceId, LocalDate occurrenceDate,
                               RecurringOccurrenceStatus status, Integer transactionId, Integer expenseId,
                               Integer userId) {
        this(id, recurrenceId, occurrenceDate, status, null, transactionId, expenseId, userId);
    }

    public RecurringOccurrence(Integer id, Integer recurrenceId, LocalDate occurrenceDate,
                               RecurringOccurrenceStatus status, BigDecimal amount, Integer transactionId,
                               Integer expenseId, Integer userId) {
        this.id = id;
        this.recurrenceId = recurrenceId;
        this.occurrenceDate = occurrenceDate;
        this.status = status;
        this.amount = amount;
        this.transactionId = transactionId;
        this.expenseId = expenseId;
        this.userId = userId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRecurrenceId() {
        return recurrenceId;
    }

    public void setRecurrenceId(Integer recurrenceId) {
        this.recurrenceId = recurrenceId;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public void setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
    }

    public RecurringOccurrenceStatus getStatus() {
        return status;
    }

    public void setStatus(RecurringOccurrenceStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Integer transactionId) {
        this.transactionId = transactionId;
    }

    public Integer getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(Integer expenseId) {
        this.expenseId = expenseId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
}
