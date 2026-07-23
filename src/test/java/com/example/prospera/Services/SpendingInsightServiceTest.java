package com.example.prospera.Services;

import com.example.prospera.DTO.CategorySummaryRecord;
import com.example.prospera.Entities.Category;
import com.example.prospera.Entities.CategoryType;
import com.example.prospera.Entities.TransactionType;
import com.example.prospera.Entities.User;
import com.example.prospera.repositories.CategoryRepository;
import com.example.prospera.repositories.ExpenseInstallmentRepository;
import com.example.prospera.repositories.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingInsightServiceTest {
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ExpenseInstallmentRepository installmentRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @Test
    void categorySummaryCombinesReportMonthTransactionsAndNextMonthCardInstallments() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(transactionRepository.sumExpenseTransactionsByCategoryInDateRange(
                1,
                LocalDateTime.of(2026, 12, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0),
                TransactionType.EXPENSE))
                .thenReturn(List.<Object[]>of(new Object[]{50, BigDecimal.valueOf(70)}));
        when(installmentRepository.sumCardStatementInstallmentsByCategoryInDueDateRange(
                1,
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 2, 1)))
                .thenReturn(List.<Object[]>of(new Object[]{50, BigDecimal.valueOf(30)}, new Object[]{null, BigDecimal.valueOf(10)}));
        when(categoryRepository.findByUserIdAndIdIn(eq(1), any()))
                .thenReturn(List.of(new Category(50, "Groceries", CategoryType.EXPENSE, true, 1)));

        SpendingInsightService service = new SpendingInsightService(authenticatedUserService, transactionRepository,
                installmentRepository, categoryRepository);

        List<CategorySummaryRecord> summary = service.getCategorySummary(12, 2026);

        assertEquals(2, summary.size());
        assertEquals(50, summary.get(0).categoryId());
        assertEquals(BigDecimal.valueOf(100), summary.get(0).amount());
        assertEquals("Uncategorized", summary.get(1).categoryName());
        assertEquals(BigDecimal.valueOf(10), summary.get(1).amount());
        verify(transactionRepository).sumExpenseTransactionsByCategoryInDateRange(
                1,
                LocalDateTime.of(2026, 12, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0),
                TransactionType.EXPENSE);
        verify(installmentRepository).sumCardStatementInstallmentsByCategoryInDueDateRange(
                1,
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 2, 1));
    }
}
