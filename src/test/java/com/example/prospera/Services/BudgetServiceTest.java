package com.example.prospera.Services;

import com.example.prospera.DTO.BudgetProgressRecord;
import com.example.prospera.DTO.BudgetRecord;
import com.example.prospera.Entities.*;
import com.example.prospera.repositories.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private SpendingInsightService spendingInsightService;

    @Test
    void createBudgetRequiresExpenseCategory() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        BudgetRecord record = new BudgetRecord(null, 50, 6, 2026, BigDecimal.valueOf(800), true);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(categoryService.requireActiveCategory(1, 50, CategoryType.EXPENSE))
                .thenThrow(new IllegalArgumentException("Category type must be EXPENSE"));

        BudgetService service = new BudgetService(budgetRepository, authenticatedUserService, categoryService,
                spendingInsightService);

        assertThrows(IllegalArgumentException.class, () -> service.create(record));
        verify(budgetRepository, never()).save(any(Budget.class));
    }

    @Test
    void progressReturnsNearLimitStatus() {
        Budget budget = new Budget(10, 50, 6, 2026, BigDecimal.valueOf(100), true, 1);
        when(spendingInsightService.getSpendingByCategory(1, 6, 2026))
                .thenReturn(Map.of(50, BigDecimal.valueOf(80)));
        when(budgetRepository.findApplicableActiveBudgets(1, 6, 2026))
                .thenReturn(List.of(budget));
        when(categoryService.findUserCategories(1, List.of(50)))
                .thenReturn(List.of(new Category(50, "Groceries", CategoryType.EXPENSE, true, 1)));

        BudgetService service = new BudgetService(budgetRepository, authenticatedUserService, categoryService,
                spendingInsightService);

        List<BudgetProgressRecord> progress = service.getProgress(1, 6, 2026);

        assertEquals(BudgetStatus.NEAR_LIMIT, progress.get(0).status());
        assertEquals(BigDecimal.valueOf(80), progress.get(0).spentAmount());
        assertEquals(BigDecimal.valueOf(20), progress.get(0).remainingAmount());
    }

    @Test
    void duplicateActiveBudgetIsRejected() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        BudgetRecord record = new BudgetRecord(null, 50, 6, 2026, BigDecimal.valueOf(100), true);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(categoryService.requireActiveCategory(1, 50, CategoryType.EXPENSE))
                .thenReturn(new Category(50, "Groceries", CategoryType.EXPENSE, true, 1));
        when(budgetRepository.findActiveByScopeAndPeriod(1, 50, 6, 2026))
                .thenReturn(List.of(new Budget(9, 50, 6, 2026, BigDecimal.valueOf(90), true, 1)));

        BudgetService service = new BudgetService(budgetRepository, authenticatedUserService, categoryService,
                spendingInsightService);

        assertThrows(IllegalArgumentException.class, () -> service.create(record));
        verify(budgetRepository, never()).save(any(Budget.class));
    }

    @Test
    void createGlobalRecurringBudgetDoesNotRequireCategoryOrPeriod() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        BudgetRecord record = new BudgetRecord(null, null, null, null, BigDecimal.valueOf(2500), true);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(budgetRepository.findActiveByScopeAndPeriod(1, null, null, null)).thenReturn(List.of());
        when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetService service = new BudgetService(budgetRepository, authenticatedUserService, categoryService,
                spendingInsightService);

        Budget budget = service.create(record);

        assertNull(budget.getCategoryId());
        assertNull(budget.getMonth());
        assertNull(budget.getYear());
        assertEquals(BigDecimal.valueOf(2500), budget.getAmount());
        verify(categoryService, never()).requireActiveCategory(any(), any(), any());
    }

    @Test
    void progressForGlobalRecurringBudgetUsesTotalMonthlySpending() {
        Budget budget = new Budget(10, null, null, null, BigDecimal.valueOf(1000), true, 1);
        when(spendingInsightService.getSpendingByCategory(1, 6, 2026))
                .thenReturn(Map.of(50, BigDecimal.valueOf(600), 60, BigDecimal.valueOf(250)));
        when(budgetRepository.findApplicableActiveBudgets(1, 6, 2026)).thenReturn(List.of(budget));

        BudgetService service = new BudgetService(budgetRepository, authenticatedUserService, categoryService,
                spendingInsightService);

        List<BudgetProgressRecord> progress = service.getProgress(1, 6, 2026);

        assertNull(progress.get(0).categoryId());
        assertEquals("Monthly budget", progress.get(0).categoryName());
        assertEquals(6, progress.get(0).month());
        assertEquals(2026, progress.get(0).year());
        assertEquals(BigDecimal.valueOf(850), progress.get(0).spentAmount());
        assertEquals(BigDecimal.valueOf(150), progress.get(0).remainingAmount());
        assertEquals(BudgetStatus.NEAR_LIMIT, progress.get(0).status());
    }

    @Test
    void monthlyBudgetOverridesRecurringBudgetForSameCategory() {
        Budget recurring = new Budget(10, 50, null, null, BigDecimal.valueOf(100), true, 1);
        Budget monthly = new Budget(11, 50, 6, 2026, BigDecimal.valueOf(200), true, 1);
        when(spendingInsightService.getSpendingByCategory(1, 6, 2026))
                .thenReturn(Map.of(50, BigDecimal.valueOf(120)));
        when(budgetRepository.findApplicableActiveBudgets(1, 6, 2026))
                .thenReturn(List.of(recurring, monthly));
        when(categoryService.findUserCategories(1, List.of(50)))
                .thenReturn(List.of(new Category(50, "Groceries", CategoryType.EXPENSE, true, 1)));

        BudgetService service = new BudgetService(budgetRepository, authenticatedUserService, categoryService,
                spendingInsightService);

        List<BudgetProgressRecord> progress = service.getProgress(1, 6, 2026);

        assertEquals(1, progress.size());
        assertEquals(11, progress.get(0).budgetId());
        assertEquals(BigDecimal.valueOf(200), progress.get(0).budgetAmount());
        assertEquals(BudgetStatus.UNDER_BUDGET, progress.get(0).status());
    }

    @Test
    void duplicateGlobalRecurringBudgetIsRejected() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        BudgetRecord record = new BudgetRecord(null, null, null, null, BigDecimal.valueOf(2500), true);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(budgetRepository.findActiveByScopeAndPeriod(1, null, null, null))
                .thenReturn(List.of(new Budget(9, null, null, null, BigDecimal.valueOf(2000), true, 1)));

        BudgetService service = new BudgetService(budgetRepository, authenticatedUserService, categoryService,
                spendingInsightService);

        assertThrows(IllegalArgumentException.class, () -> service.create(record));
        verify(budgetRepository, never()).save(any(Budget.class));
    }
}
