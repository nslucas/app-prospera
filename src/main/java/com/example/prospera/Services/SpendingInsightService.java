package com.example.prospera.Services;

import com.example.prospera.DTO.CategorySummaryRecord;
import com.example.prospera.DTO.MonthlyTrendRecord;
import com.example.prospera.Entities.Category;
import com.example.prospera.Entities.CategoryType;
import com.example.prospera.Entities.TransactionType;
import com.example.prospera.Entities.User;
import com.example.prospera.repositories.CategoryRepository;
import com.example.prospera.repositories.ExpenseInstallmentRepository;
import com.example.prospera.repositories.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SpendingInsightService {
    private final AuthenticatedUserService authenticatedUserService;
    private final TransactionRepository transactionRepository;
    private final ExpenseInstallmentRepository installmentRepository;
    private final CategoryRepository categoryRepository;

    public SpendingInsightService(AuthenticatedUserService authenticatedUserService,
                                  TransactionRepository transactionRepository,
                                  ExpenseInstallmentRepository installmentRepository,
                                  CategoryRepository categoryRepository) {
        this.authenticatedUserService = authenticatedUserService;
        this.transactionRepository = transactionRepository;
        this.installmentRepository = installmentRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<CategorySummaryRecord> getCategorySummary(Integer month, Integer year) {
        validateMonthYear(month, year);
        User user = authenticatedUserService.getAuthenticatedUser();
        return toCategorySummary(user.getId(), getSpendingByCategory(user.getId(), month, year));
    }

    public List<MonthlyTrendRecord> getTrends(Integer fromMonth, Integer fromYear, Integer toMonth, Integer toYear) {
        validateMonthYear(fromMonth, fromYear);
        validateMonthYear(toMonth, toYear);
        User user = authenticatedUserService.getAuthenticatedUser();
        YearMonth from = YearMonth.of(fromYear, fromMonth);
        YearMonth to = YearMonth.of(toYear, toMonth);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Start month must be before or equal to end month");
        }

        List<MonthlyTrendRecord> trends = new ArrayList<>();
        YearMonth cursor = from;
        while (!cursor.isAfter(to)) {
            Integer month = cursor.getMonthValue();
            Integer year = cursor.getYear();
            BigDecimal incomeTotal = sumByType(user.getId(), TransactionType.INCOME, month, year);
            BigDecimal accountExpenseTotal = sumByType(user.getId(), TransactionType.EXPENSE, month, year);
            BigDecimal cardStatementExpenseTotal = zeroIfNull(
                    installmentRepository.sumCardStatementByUserIdAndDueMonth(user.getId(), month, year));
            BigDecimal netTotal = incomeTotal.subtract(accountExpenseTotal).subtract(cardStatementExpenseTotal);
            trends.add(new MonthlyTrendRecord(month, year, incomeTotal, accountExpenseTotal,
                    cardStatementExpenseTotal, netTotal));
            cursor = cursor.plusMonths(1);
        }
        return trends;
    }

    public Map<Integer, BigDecimal> getSpendingByCategory(Integer userId, Integer month, Integer year) {
        Map<Integer, BigDecimal> totals = new HashMap<>();
        YearMonth reportMonth = YearMonth.of(year, month);
        YearMonth statementMonth = reportMonth.plusMonths(1);
        LocalDateTime fromDateTime = reportMonth.atDay(1).atStartOfDay();
        LocalDateTime toDateTime = reportMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDate fromDate = statementMonth.atDay(1);
        LocalDate toDate = statementMonth.plusMonths(1).atDay(1);
        mergeGroupedTotals(totals, transactionRepository.sumExpenseTransactionsByCategoryInDateRange(userId,
                fromDateTime, toDateTime, TransactionType.EXPENSE));
        mergeGroupedTotals(totals, installmentRepository.sumCardStatementInstallmentsByCategoryInDueDateRange(userId,
                fromDate, toDate));
        return totals;
    }

    private List<CategorySummaryRecord> toCategorySummary(Integer userId, Map<Integer, BigDecimal> totals) {
        Map<Integer, Category> categoriesById = findCategoriesById(userId, totals.keySet());
        return totals.entrySet().stream()
                .sorted(Comparator.comparing(entry -> categorySortName(categoriesById, entry.getKey())))
                .map(entry -> toCategorySummaryRecord(categoriesById, entry.getKey(), entry.getValue()))
                .toList();
    }

    private CategorySummaryRecord toCategorySummaryRecord(Map<Integer, Category> categoriesById, Integer categoryId,
                                                          BigDecimal amount) {
        if (categoryId == null) {
            return new CategorySummaryRecord(null, "Uncategorized", CategoryType.EXPENSE, amount);
        }
        Category category = categoriesById.get(categoryId);
        if (category == null) {
            return new CategorySummaryRecord(categoryId, "Unknown category", CategoryType.EXPENSE, amount);
        }
        return new CategorySummaryRecord(category.getId(), category.getName(), category.getType(), amount);
    }

    private String categorySortName(Map<Integer, Category> categoriesById, Integer categoryId) {
        if (categoryId == null) {
            return "zzzz_uncategorized";
        }
        Category category = categoriesById.get(categoryId);
        return category == null ? "zzzz_unknown" : category.getName();
    }

    private Map<Integer, Category> findCategoriesById(Integer userId, Set<Integer> categoryIds) {
        List<Integer> ids = categoryIds.stream()
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findByUserIdAndIdIn(userId, ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private void mergeGroupedTotals(Map<Integer, BigDecimal> totals, List<Object[]> groupedTotals) {
        if (groupedTotals == null) {
            return;
        }
        for (Object[] row : groupedTotals) {
            Integer categoryId = (Integer) row[0];
            BigDecimal amount = zeroIfNull((BigDecimal) row[1]);
            totals.merge(categoryId, amount, BigDecimal::add);
        }
    }

    private BigDecimal sumByType(Integer userId, TransactionType type, Integer month, Integer year) {
        YearMonth filterMonth = YearMonth.of(year, month);
        return zeroIfNull(transactionRepository.sumByUserIdTypeAndDateRange(userId, type,
                filterMonth.atDay(1).atStartOfDay(), filterMonth.plusMonths(1).atDay(1).atStartOfDay()));
    }

    private void validateMonthYear(Integer month, Integer year) {
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (year == null || year < 1900) {
            throw new IllegalArgumentException("Year must be valid");
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
