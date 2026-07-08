package com.example.prospera.Services;

import com.example.prospera.DTO.RecurringOccurrenceRecord;
import com.example.prospera.DTO.RecurringOccurrenceRequest;
import com.example.prospera.DTO.RecurringTransactionRecord;
import com.example.prospera.Entities.*;
import com.example.prospera.Exceptions.ObjectNotFoundException;
import com.example.prospera.repositories.RecurringOccurrenceRepository;
import com.example.prospera.repositories.RecurringTransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class RecurringTransactionService {
    private final RecurringTransactionRepository recurrenceRepository;
    private final RecurringOccurrenceRepository occurrenceRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccountService accountService;
    private final CardService cardService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final ExpenseService expenseService;

    public RecurringTransactionService(RecurringTransactionRepository recurrenceRepository,
                                       RecurringOccurrenceRepository occurrenceRepository,
                                       AuthenticatedUserService authenticatedUserService,
                                       AccountService accountService,
                                       CardService cardService,
                                       CategoryService categoryService,
                                       TransactionService transactionService,
                                       ExpenseService expenseService) {
        this.recurrenceRepository = recurrenceRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.accountService = accountService;
        this.cardService = cardService;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.expenseService = expenseService;
    }

    public List<RecurringTransaction> findAllActiveForAuthenticatedUser() {
        User user = authenticatedUserService.getAuthenticatedUser();
        return recurrenceRepository.findByUserIdAndActiveTrueOrderByNameAsc(user.getId());
    }

    public RecurringTransaction findAuthenticatedUserRecurrence(Integer id) {
        User user = authenticatedUserService.getAuthenticatedUser();
        return findUserRecurrence(user.getId(), id);
    }

    public RecurringTransaction create(RecurringTransactionRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Recurring transaction body is required");
        }
        User user = authenticatedUserService.getAuthenticatedUser();
        RecurringTransaction recurrence = fromRecord(record);
        recurrence.setId(null);
        recurrence.setUserId(user.getId());
        recurrence.setActive(true);
        normalizeAndValidate(user.getId(), recurrence);
        return recurrenceRepository.save(recurrence);
    }

    public RecurringTransaction update(Integer id, RecurringTransactionRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Recurring transaction body is required");
        }
        User user = authenticatedUserService.getAuthenticatedUser();
        RecurringTransaction recurrence = findUserRecurrence(user.getId(), id);
        RecurringTransaction updated = fromRecord(record);
        updated.setId(id);
        updated.setUserId(user.getId());
        updated.setActive(recurrence.getActive());
        normalizeAndValidate(user.getId(), updated);

        recurrence.setName(updated.getName());
        recurrence.setDescription(updated.getDescription());
        recurrence.setTargetType(updated.getTargetType());
        recurrence.setTransactionType(updated.getTransactionType());
        recurrence.setAmount(updated.getAmount());
        recurrence.setFrequency(updated.getFrequency());
        recurrence.setStartDate(updated.getStartDate());
        recurrence.setEndDate(updated.getEndDate());
        recurrence.setDayOfMonth(updated.getDayOfMonth());
        recurrence.setMonthOfYear(updated.getMonthOfYear());
        recurrence.setAccountId(updated.getAccountId());
        recurrence.setCardId(updated.getCardId());
        recurrence.setCategoryId(updated.getCategoryId());
        recurrence.setInstallmentCount(updated.getInstallmentCount());
        recurrence.setClassification(updated.getClassification());
        return recurrenceRepository.save(recurrence);
    }

    public void deactivate(Integer id) {
        RecurringTransaction recurrence = findAuthenticatedUserRecurrence(id);
        recurrence.setActive(false);
        recurrenceRepository.save(recurrence);
    }

    public List<RecurringOccurrenceRecord> previewOccurrences(LocalDate from, LocalDate to) {
        User user = authenticatedUserService.getAuthenticatedUser();
        validateRange(from, to);
        return previewOccurrences(user.getId(), from, to);
    }

    public List<RecurringOccurrenceRecord> previewOccurrences(Integer userId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return findActiveUserRecurrencesInRange(userId, from, to).stream()
                .flatMap(recurrence -> generateOccurrenceDates(recurrence, from, to).stream()
                        .map(date -> toOccurrenceRecord(recurrence, date)))
                .sorted(Comparator.comparing(RecurringOccurrenceRecord::occurrenceDate)
                        .thenComparing(RecurringOccurrenceRecord::recurrenceName))
                .toList();
    }

    public List<RecurringTransaction> findActiveUserRecurrencesInRange(Integer userId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return recurrenceRepository.findActiveInRange(userId, from, to);
    }

    public RecurringOccurrence materialize(Integer recurrenceId, RecurringOccurrenceRequest request) {
        LocalDate occurrenceDate = requireOccurrenceDate(request);
        User user = authenticatedUserService.getAuthenticatedUser();
        RecurringTransaction recurrence = findUserRecurrence(user.getId(), recurrenceId);
        validateMaterializable(recurrence, occurrenceDate);
        normalizeAndValidate(user.getId(), recurrence);
        BigDecimal occurrenceAmount = resolveOccurrenceAmount(recurrence, request);

        RecurringOccurrence occurrence = occurrenceRepository
                .findByRecurrenceIdAndOccurrenceDate(recurrence.getId(), occurrenceDate)
                .orElse(null);
        if (occurrence != null && occurrence.getStatus() == RecurringOccurrenceStatus.MATERIALIZED) {
            throw new IllegalArgumentException("Occurrence already materialized");
        }
        if (occurrence != null && occurrence.getStatus() == RecurringOccurrenceStatus.SKIPPED) {
            throw new IllegalArgumentException("Skipped occurrence cannot be materialized");
        }
        if (occurrence == null) {
            occurrence = new RecurringOccurrence(null, recurrence.getId(), occurrenceDate,
                    RecurringOccurrenceStatus.PENDING, null, null, user.getId());
        }

        if (recurrence.getTargetType() == RecurringTargetType.ACCOUNT_TRANSACTION) {
            Transaction transaction = transactionService.createRecurringTransaction(user.getId(),
                    recurrence.getAccountId(), recurrence.getTransactionType(), occurrenceAmount,
                    LocalDateTime.of(occurrenceDate, LocalTime.NOON), recurrence.getDescription(),
                    recurrence.getCategoryId());
            occurrence.setTransactionId(transaction.getId());
        } else {
            Expense expense = expenseService.createRecurringExpense(user.getId(), recurrence.getName(),
                    occurrenceAmount, recurrence.getInstallmentCount(),
                    LocalDateTime.of(occurrenceDate, LocalTime.NOON), recurrence.getDescription(),
                    recurrence.getCardId(), recurrence.getCategoryId());
            occurrence.setExpenseId(expense.getId());
        }
        occurrence.setAmount(occurrenceAmount);
        occurrence.setStatus(RecurringOccurrenceStatus.MATERIALIZED);
        return occurrenceRepository.save(occurrence);
    }

    public RecurringOccurrence skip(Integer recurrenceId, RecurringOccurrenceRequest request) {
        LocalDate occurrenceDate = requireOccurrenceDate(request);
        User user = authenticatedUserService.getAuthenticatedUser();
        RecurringTransaction recurrence = findUserRecurrence(user.getId(), recurrenceId);
        validateMaterializable(recurrence, occurrenceDate);

        RecurringOccurrence occurrence = occurrenceRepository
                .findByRecurrenceIdAndOccurrenceDate(recurrence.getId(), occurrenceDate)
                .orElse(new RecurringOccurrence(null, recurrence.getId(), occurrenceDate,
                        RecurringOccurrenceStatus.PENDING, null, null, user.getId()));
        if (occurrence.getStatus() == RecurringOccurrenceStatus.MATERIALIZED) {
            throw new IllegalArgumentException("Materialized occurrence cannot be skipped");
        }
        occurrence.setStatus(RecurringOccurrenceStatus.SKIPPED);
        return occurrenceRepository.save(occurrence);
    }

    public RecurringOccurrence revert(Integer recurrenceId, RecurringOccurrenceRequest request) {
        LocalDate occurrenceDate = requireOccurrenceDate(request);
        User user = authenticatedUserService.getAuthenticatedUser();
        RecurringTransaction recurrence = findUserRecurrence(user.getId(), recurrenceId);
        validateOccurrenceDateInSchedule(recurrence, occurrenceDate);

        RecurringOccurrence occurrence = occurrenceRepository
                .findByRecurrenceIdAndOccurrenceDate(recurrence.getId(), occurrenceDate)
                .orElseThrow(() -> new IllegalArgumentException("Only materialized occurrences can be reverted"));
        if (occurrence.getStatus() != RecurringOccurrenceStatus.MATERIALIZED) {
            throw new IllegalArgumentException("Only materialized occurrences can be reverted");
        }

        if (occurrence.getTransactionId() != null) {
            transactionService.deleteRecurringTransaction(user.getId(), occurrence.getTransactionId());
        }
        if (occurrence.getExpenseId() != null) {
            expenseService.deleteRecurringExpense(user.getId(), occurrence.getExpenseId());
        }

        occurrence.setTransactionId(null);
        occurrence.setExpenseId(null);
        occurrence.setAmount(null);
        occurrence.setStatus(RecurringOccurrenceStatus.PENDING);
        return occurrenceRepository.save(occurrence);
    }

    public List<LocalDate> generateOccurrenceDates(RecurringTransaction recurrence, LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<LocalDate> dates = new ArrayList<>();
        LocalDate effectiveFrom = from.isAfter(recurrence.getStartDate()) ? from : recurrence.getStartDate();
        LocalDate effectiveTo = recurrence.getEndDate() != null && recurrence.getEndDate().isBefore(to)
                ? recurrence.getEndDate()
                : to;
        if (effectiveFrom.isAfter(effectiveTo)) {
            return dates;
        }

        if (recurrence.getFrequency() == RecurringFrequency.MONTHLY) {
            YearMonth cursor = YearMonth.from(effectiveFrom);
            YearMonth end = YearMonth.from(effectiveTo);
            while (!cursor.isAfter(end)) {
                LocalDate date = atConfiguredDay(cursor, recurrence.getDayOfMonth());
                if (!date.isBefore(effectiveFrom) && !date.isAfter(effectiveTo)) {
                    dates.add(date);
                }
                cursor = cursor.plusMonths(1);
            }
            return dates;
        }

        int year = effectiveFrom.getYear();
        while (year <= effectiveTo.getYear()) {
            LocalDate date = atConfiguredDay(YearMonth.of(year, recurrence.getMonthOfYear()),
                    recurrence.getDayOfMonth());
            if (!date.isBefore(effectiveFrom) && !date.isAfter(effectiveTo)) {
                dates.add(date);
            }
            year++;
        }
        return dates;
    }

    public RecurringTransaction findUserRecurrence(Integer userId, Integer id) {
        return recurrenceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ObjectNotFoundException("Recurring transaction not found with id: " + id));
    }

    private RecurringOccurrenceRecord toOccurrenceRecord(RecurringTransaction recurrence, LocalDate date) {
        RecurringOccurrence occurrence = occurrenceRepository
                .findByRecurrenceIdAndOccurrenceDate(recurrence.getId(), date)
                .orElse(null);
        return new RecurringOccurrenceRecord(recurrence, occurrence, date);
    }

    private void validateMaterializable(RecurringTransaction recurrence, LocalDate occurrenceDate) {
        if (!Boolean.TRUE.equals(recurrence.getActive())) {
            throw new IllegalArgumentException("Recurring transaction is inactive");
        }
        validateOccurrenceDateInSchedule(recurrence, occurrenceDate);
    }

    private void validateOccurrenceDateInSchedule(RecurringTransaction recurrence, LocalDate occurrenceDate) {
        if (!generateOccurrenceDates(recurrence, occurrenceDate, occurrenceDate).contains(occurrenceDate)) {
            throw new IllegalArgumentException("Occurrence date is outside the recurrence schedule");
        }
    }

    private RecurringTransaction fromRecord(RecurringTransactionRecord record) {
        return new RecurringTransaction(record.id(), normalizeName(record.name()), record.description(),
                record.targetType(), record.transactionType(), record.amount(), record.frequency(),
                record.startDate(), record.endDate(), record.dayOfMonth(), record.monthOfYear(),
                record.accountId(), record.cardId(), record.categoryId(), record.installmentCount(),
                record.classification(), record.active(), null);
    }

    private void normalizeAndValidate(Integer userId, RecurringTransaction recurrence) {
        if (recurrence.getName() == null || recurrence.getName().isBlank()) {
            throw new IllegalArgumentException("Recurring transaction name is required");
        }
        if (recurrence.getTargetType() == null) {
            throw new IllegalArgumentException("Recurring target type is required");
        }
        if (recurrence.getAmount() == null || recurrence.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Recurring amount must be greater than zero");
        }
        if (recurrence.getFrequency() == null) {
            throw new IllegalArgumentException("Recurring frequency is required");
        }
        if (recurrence.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (recurrence.getEndDate() != null && recurrence.getEndDate().isBefore(recurrence.getStartDate())) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
        if (recurrence.getDayOfMonth() == null) {
            recurrence.setDayOfMonth(recurrence.getStartDate().getDayOfMonth());
        }
        if (recurrence.getDayOfMonth() < 1 || recurrence.getDayOfMonth() > 31) {
            throw new IllegalArgumentException("Day of month must be between 1 and 31");
        }
        if (recurrence.getFrequency() == RecurringFrequency.ANNUAL && recurrence.getMonthOfYear() == null) {
            recurrence.setMonthOfYear(recurrence.getStartDate().getMonthValue());
        }
        if (recurrence.getFrequency() == RecurringFrequency.MONTHLY) {
            recurrence.setMonthOfYear(null);
        } else if (recurrence.getMonthOfYear() < 1 || recurrence.getMonthOfYear() > 12) {
            throw new IllegalArgumentException("Month of year must be between 1 and 12");
        }
        if (recurrence.getClassification() == null) {
            recurrence.setClassification(RecurringClassification.FIXED);
        }

        if (recurrence.getTargetType() == RecurringTargetType.ACCOUNT_TRANSACTION) {
            validateAccountRecurrence(userId, recurrence);
        } else {
            validateCardRecurrence(userId, recurrence);
        }
    }

    private void validateAccountRecurrence(Integer userId, RecurringTransaction recurrence) {
        if (recurrence.getTransactionType() != TransactionType.INCOME &&
                recurrence.getTransactionType() != TransactionType.EXPENSE) {
            throw new IllegalArgumentException("Account recurrence transaction type must be income or expense");
        }
        if (recurrence.getAccountId() == null) {
            throw new IllegalArgumentException("Account id is required");
        }
        if (recurrence.getCardId() != null) {
            throw new IllegalArgumentException("Card id must be empty for account recurrences");
        }
        Account account = accountService.findUserAccount(userId, recurrence.getAccountId());
        accountService.ensureActive(account);
        if (recurrence.getCategoryId() != null) {
            CategoryType categoryType = recurrence.getTransactionType() == TransactionType.INCOME
                    ? CategoryType.INCOME
                    : CategoryType.EXPENSE;
            categoryService.requireActiveCategory(userId, recurrence.getCategoryId(), categoryType);
        }
        recurrence.setInstallmentCount(null);
    }

    private void validateCardRecurrence(Integer userId, RecurringTransaction recurrence) {
        if (recurrence.getCardId() == null) {
            throw new IllegalArgumentException("Card id is required");
        }
        if (recurrence.getAccountId() != null || recurrence.getTransactionType() != null) {
            throw new IllegalArgumentException("Account id and transaction type must be empty for card recurrences");
        }
        Card card = cardService.findUserCard(userId, recurrence.getCardId());
        if (!Boolean.TRUE.equals(card.getActive())) {
            throw new IllegalArgumentException("Card is inactive");
        }
        if (recurrence.getCategoryId() != null) {
            categoryService.requireActiveCategory(userId, recurrence.getCategoryId(), CategoryType.EXPENSE);
        }
        if (recurrence.getInstallmentCount() == null) {
            recurrence.setInstallmentCount(1);
        }
        if (recurrence.getInstallmentCount() < 1) {
            throw new IllegalArgumentException("Installment count must be greater than zero");
        }
    }

    private LocalDate requireOccurrenceDate(RecurringOccurrenceRequest request) {
        if (request == null || request.occurrenceDate() == null) {
            throw new IllegalArgumentException("Occurrence date is required");
        }
        return request.occurrenceDate();
    }

    private BigDecimal resolveOccurrenceAmount(RecurringTransaction recurrence, RecurringOccurrenceRequest request) {
        BigDecimal requestedAmount = request.amount();
        if (requestedAmount == null) {
            return recurrence.getAmount();
        }
        if (recurrence.getClassification() != RecurringClassification.VARIABLE) {
            throw new IllegalArgumentException("Occurrence amount can only be changed for variable recurrences");
        }
        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Occurrence amount must be greater than zero");
        }
        return requestedAmount;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("From and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("From date must be before or equal to to date");
        }
    }

    private LocalDate atConfiguredDay(YearMonth month, Integer day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }
}
