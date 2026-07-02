package com.example.prospera.Services;

import com.example.prospera.DTO.BudgetProgressRecord;
import com.example.prospera.DTO.BudgetRecord;
import com.example.prospera.Entities.*;
import com.example.prospera.Exceptions.ObjectNotFoundException;
import com.example.prospera.repositories.BudgetRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class BudgetService {
    private static final BigDecimal NEAR_LIMIT_PERCENT = BigDecimal.valueOf(80);

    private final BudgetRepository budgetRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final CategoryService categoryService;
    private final SpendingInsightService spendingInsightService;

    public BudgetService(BudgetRepository budgetRepository, AuthenticatedUserService authenticatedUserService,
                         CategoryService categoryService, SpendingInsightService spendingInsightService) {
        this.budgetRepository = budgetRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.categoryService = categoryService;
        this.spendingInsightService = spendingInsightService;
    }

    public List<Budget> findAuthenticatedUserBudgets(Integer month, Integer year) {
        User user = authenticatedUserService.getAuthenticatedUser();
        validateOptionalMonthYear(month, year);
        if (month == null) {
            return budgetRepository.findByUserIdAndActiveTrueOrderByYearDescMonthDescCategoryIdAscIdAsc(user.getId());
        }
        return findApplicableBudgets(user.getId(), month, year);
    }

    public Budget findAuthenticatedUserBudget(Integer id) {
        User user = authenticatedUserService.getAuthenticatedUser();
        return findUserBudget(user.getId(), id);
    }

    public Budget create(BudgetRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Budget body is required");
        }
        User user = authenticatedUserService.getAuthenticatedUser();
        validate(record);
        if (record.categoryId() != null) {
            categoryService.requireActiveCategory(user.getId(), record.categoryId(), CategoryType.EXPENSE);
        }
        validateUniqueBudget(user.getId(), null, record.categoryId(), record.month(), record.year());

        Budget budget = new Budget(null, record.categoryId(), record.month(), record.year(),
                record.amount(), true, user.getId());
        return budgetRepository.save(budget);
    }

    public Budget update(Integer id, BudgetRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Budget body is required");
        }
        User user = authenticatedUserService.getAuthenticatedUser();
        validate(record);
        if (record.categoryId() != null) {
            categoryService.requireActiveCategory(user.getId(), record.categoryId(), CategoryType.EXPENSE);
        }
        validateUniqueBudget(user.getId(), id, record.categoryId(), record.month(), record.year());

        Budget budget = findUserBudget(user.getId(), id);
        budget.setCategoryId(record.categoryId());
        budget.setMonth(record.month());
        budget.setYear(record.year());
        budget.setAmount(record.amount());
        return budgetRepository.save(budget);
    }

    public void deactivate(Integer id) {
        Budget budget = findAuthenticatedUserBudget(id);
        budget.setActive(false);
        budgetRepository.save(budget);
    }

    public List<BudgetProgressRecord> getProgress(Integer month, Integer year) {
        validateMonthYear(month, year);
        User user = authenticatedUserService.getAuthenticatedUser();
        return getProgress(user.getId(), month, year);
    }

    public List<BudgetProgressRecord> getProgress(Integer userId, Integer month, Integer year) {
        Map<Integer, BigDecimal> spending = spendingInsightService.getSpendingByCategory(userId, month, year);
        BigDecimal totalSpending = spending.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Budget> budgets = findApplicableBudgets(userId, month, year);
        List<Integer> categoryIds = budgets.stream()
                .map(Budget::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, Category> categoriesById = categoryIds.isEmpty()
                ? Map.of()
                : categoryService.findUserCategories(userId, categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity()));
        return budgets.stream()
                .map(budget -> toProgress(budget,
                        budget.getCategoryId() == null ? null : categoriesById.get(budget.getCategoryId()),
                        budget.getCategoryId() == null
                                ? totalSpending
                                : spending.getOrDefault(budget.getCategoryId(), BigDecimal.ZERO),
                        month, year))
                .toList();
    }

    private BudgetProgressRecord toProgress(Budget budget, Category category, BigDecimal spentAmount,
                                            Integer month, Integer year) {
        if (budget.getCategoryId() != null && category == null) {
            throw new ObjectNotFoundException("Category not found with id: " + budget.getCategoryId());
        }
        BigDecimal remainingAmount = budget.getAmount().subtract(spentAmount);
        BigDecimal percentUsed = spentAmount.multiply(BigDecimal.valueOf(100))
                .divide(budget.getAmount(), 2, RoundingMode.HALF_UP);
        String categoryName = budget.getCategoryId() == null ? "Monthly budget" : category.getName();
        return new BudgetProgressRecord(budget.getId(), budget.getCategoryId(), categoryName,
                month, year, budget.getAmount(), spentAmount, remainingAmount,
                percentUsed, resolveStatus(percentUsed));
    }

    public static BudgetStatus resolveStatus(BigDecimal percentUsed) {
        if (percentUsed.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BudgetStatus.OVER_BUDGET;
        }
        if (percentUsed.compareTo(NEAR_LIMIT_PERCENT) >= 0) {
            return BudgetStatus.NEAR_LIMIT;
        }
        return BudgetStatus.UNDER_BUDGET;
    }

    private Budget findUserBudget(Integer userId, Integer id) {
        return budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ObjectNotFoundException("Budget not found with id: " + id));
    }

    private void validate(BudgetRecord record) {
        validateOptionalMonthYear(record.month(), record.year());
        if (record.amount() == null || record.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget amount must be greater than zero");
        }
    }

    private void validateUniqueBudget(Integer userId, Integer currentId, Integer categoryId, Integer month, Integer year) {
        budgetRepository.findActiveByScopeAndPeriod(userId, categoryId, month, year)
                .stream()
                .filter(existing -> !existing.getId().equals(currentId))
                .findAny()
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Active budget already exists for this scope and period");
                });
    }

    private List<Budget> findApplicableBudgets(Integer userId, Integer month, Integer year) {
        List<Budget> budgets = budgetRepository.findApplicableActiveBudgets(userId, month, year);
        Set<Integer> specificScopes = budgets.stream()
                .filter(this::isSpecificBudget)
                .map(Budget::getCategoryId)
                .collect(Collectors.toSet());
        return budgets.stream()
                .filter(budget -> isSpecificBudget(budget) || !specificScopes.contains(budget.getCategoryId()))
                .sorted(Comparator
                        .comparing((Budget budget) -> budget.getCategoryId() == null ? 0 : 1)
                        .thenComparing(budget -> budget.getCategoryId() == null ? 0 : budget.getCategoryId())
                        .thenComparing(budget -> isSpecificBudget(budget) ? 0 : 1)
                        .thenComparing(budget -> budget.getId() == null ? 0 : budget.getId()))
                .toList();
    }

    private boolean isSpecificBudget(Budget budget) {
        return budget.getMonth() != null && budget.getYear() != null;
    }

    private void validateOptionalMonthYear(Integer month, Integer year) {
        if (month == null && year == null) {
            return;
        }
        if (month == null || year == null) {
            throw new IllegalArgumentException("Budget month and year must be provided together");
        }
        validateMonthYear(month, year);
    }

    private void validateMonthYear(Integer month, Integer year) {
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (year == null || year < 1900) {
            throw new IllegalArgumentException("Year must be valid");
        }
    }
}
