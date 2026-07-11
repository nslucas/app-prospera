package com.example.prospera.Services;

import com.example.prospera.DTO.ExpenseRecord;
import com.example.prospera.Entities.Card;
import com.example.prospera.Entities.CategoryType;
import com.example.prospera.Entities.Expense;
import com.example.prospera.Entities.MonthlyExpensesResponse;
import com.example.prospera.Entities.User;
import com.example.prospera.Exceptions.ObjectNotFoundException;
import com.example.prospera.repositories.ExpenseInstallmentRepository;
import com.example.prospera.repositories.ExpenseRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final UserService userService;
    private final String TIME_ZONE = "America/Sao_Paulo";
    private final ExpenseInstallmentService installmentService;
    private final ExpenseInstallmentRepository installmentRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final CardService cardService;
    private final CategoryService categoryService;
    private final ExpenseShareService expenseShareService;

    public ExpenseService(ExpenseRepository expenseRepository, UserService userService,
                          ExpenseInstallmentService installmentService,
                          ExpenseInstallmentRepository installmentRepository,
                          AuthenticatedUserService authenticatedUserService, CardService cardService,
                          CategoryService categoryService, ExpenseShareService expenseShareService) {
        this.expenseRepository = expenseRepository;
        this.userService = userService;
        this.installmentService = installmentService;
        this.installmentRepository = installmentRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.cardService = cardService;
        this.categoryService = categoryService;
        this.expenseShareService = expenseShareService;
    }

    public List<Expense> getAllExpenses() {
        return expenseRepository.findAll();
    }

    public List<Expense> findAuthenticatedUserExpenses(Integer month, Integer year, Integer cardId) {
        User user = authenticatedUserService.getAuthenticatedUser();
        validateMonthYear(month, year);
        if (cardId != null) {
            cardService.findUserCard(user.getId(), cardId);
        }
        YearMonth filterMonth = month == null ? null : YearMonth.of(year, month);
        LocalDateTime from = filterMonth == null ? null : filterMonth.atDay(1).atStartOfDay();
        LocalDateTime to = filterMonth == null ? null : filterMonth.plusMonths(1).atDay(1).atStartOfDay();
        return expenseRepository.findByFilters(user.getId(), from, to, cardId);
    }

    public Expense getExpenseById(Integer id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Expense not found with id: " + id));
    }

    public Expense getAuthenticatedUserExpense(Integer id) {
        User user = authenticatedUserService.getAuthenticatedUser();
        return findOwnedExpense(user.getId(), id);
    }

    public String getSumAmountByUserId(Integer userId) {
        BigDecimal sumAmountByUser = zeroIfNull(expenseRepository.sumAmountByUserId(userId));
        User user = userService.findById(userId);
        if (sumAmountByUser.compareTo(user.getMonthLimit()) >= 0) {
            return "Your month limit exceeded, here's your total for this month " + sumAmountByUser;
        }
        return sumAmountByUser.toString();
    }

    public String getSumAmountByUserIdInCurrentMonth(Integer userId) {
        BigDecimal sumAmountByUser = zeroIfNull(expenseRepository.sumAmountByUserIdInCurrentMonth(userId));
        User user = userService.findById(userId);
        if (sumAmountByUser.compareTo(user.getMonthLimit()) >= 0) {
            return "Your month limit exceeded, here's your total for this month " + sumAmountByUser;
        }
        return sumAmountByUser.toString();
    }

    public Expense createExpense(ExpenseRecord obj) {
        User user = authenticatedUserService.getAuthenticatedUser();
        Expense expense = fromDTO(obj);
        expense.setUserId(user.getId());
        if (expense.getPurchaseDate() == null) {
            ZoneId zid = ZoneId.of(TIME_ZONE);
            expense.setPurchaseDate(LocalDateTime.now(zid));
        }
        validateExpense(expense);
        Card card = resolveOwnedActiveCard(user.getId(), expense.getCardId());
        validateExpenseCategory(user.getId(), expense.getCategoryId());

        expenseRepository.save(expense);
        installmentService.generateInstallments(expense, card);
        expenseShareService.createForExpense(user, expense, obj.share());
        return expense;
    }

    public Expense createRecurringExpense(Integer userId, String name, BigDecimal amount, Integer installmentCount,
                                          LocalDateTime purchaseDate, String description, Integer cardId,
                                          Integer categoryId) {
        Expense expense = new Expense(null, name, amount, installmentCount, purchaseDate, description,
                userId, cardId, categoryId);
        validateExpense(expense);
        Card card = resolveOwnedActiveCard(userId, cardId);
        validateExpenseCategory(userId, categoryId);

        expenseRepository.save(expense);
        installmentService.generateInstallments(expense, card);
        return expense;
    }

    public Expense update(Integer id, ExpenseRecord objDTO) {
        User user = authenticatedUserService.getAuthenticatedUser();
        Expense obj = fromDTO(objDTO);
        obj.setId(id);
        obj.setUserId(user.getId());
        Expense newObj = findOwnedExpense(user.getId(), id);
        if (obj.getPurchaseDate() == null) {
            obj.setPurchaseDate(newObj.getPurchaseDate());
        }
        validateExpense(obj);
        Card card = resolveOwnedActiveCard(user.getId(), obj.getCardId());
        validateExpenseCategory(user.getId(), obj.getCategoryId());

        newObj.setName(obj.getName());
        newObj.setDescription(obj.getDescription());
        newObj.setAmount(obj.getAmount());
        newObj.setInstallmentCount(obj.getInstallmentCount());
        newObj.setPurchaseDate(obj.getPurchaseDate());
        newObj.setCardId(obj.getCardId());
        newObj.setCategoryId(obj.getCategoryId());
        Expense saved = expenseRepository.save(newObj);
        expenseShareService.updateForExpense(user, saved, objDTO.share());

        installmentService.deleteByExpenseId(saved.getId());
        installmentService.generateInstallments(saved, card);
        return saved;
    }

    public void delete(Integer id) {
        User user = authenticatedUserService.getAuthenticatedUser();
        deleteRecurringExpense(user.getId(), id);
    }

    public void deleteRecurringExpense(Integer userId, Integer expenseId) {
        Expense expense = findOwnedExpense(userId, expenseId);
        expenseShareService.deleteForExpense(expense.getId());
        installmentService.deleteByExpenseId(expense.getId());
        expenseRepository.deleteById(expense.getId());
    }

    public BigDecimal getAuthenticatedUserTotalExpenses(Integer requestedUserId) {
        User user = authenticatedUserService.requireAuthenticatedUser(requestedUserId);
        return zeroIfNull(expenseRepository.sumAmountByUserId(user.getId()));
    }

    public MonthlyExpensesResponse getAuthenticatedUserExpensesInCurrentMonth(Integer requestedUserId) {
        User user = authenticatedUserService.requireAuthenticatedUser(requestedUserId);
        YearMonth currentMonth = YearMonth.now();
        List<Expense> expenses = installmentRepository.findExpensesByUserIdAndDueMonth(
                user.getId(), currentMonth.getMonthValue(), currentMonth.getYear());
        BigDecimal total = zeroIfNull(installmentRepository.sumByUserIdAndDueMonth(
                user.getId(), currentMonth.getMonthValue(), currentMonth.getYear()));
        return new MonthlyExpensesResponse(expenses, total);
    }

    public MonthlyExpensesResponse getAuthenticatedUserExpensesInAnyMonth(Integer requestedUserId,
                                                                           Integer month,
                                                                           Integer year) {
        User user = authenticatedUserService.requireAuthenticatedUser(requestedUserId);
        List<Expense> expenses = installmentRepository.findExpensesByUserIdAndDueMonth(user.getId(), month, year);
        if (expenses.isEmpty()) {
            throw new ObjectNotFoundException("There are no expenses for this user");
        }
        BigDecimal total = zeroIfNull(installmentRepository.sumByUserIdAndDueMonth(user.getId(), month, year));
        return new MonthlyExpensesResponse(expenses, total);
    }

    public Expense fromDTO(ExpenseRecord objDTO) {
        return new Expense(objDTO.id(), objDTO.name(), objDTO.amount(), objDTO.installmentCount(),
                objDTO.purchaseDate(), objDTO.description(), objDTO.userId(), objDTO.cardId(), objDTO.categoryId());
    }

    private Expense findOwnedExpense(Integer userId, Integer expenseId) {
        Expense expense = getExpenseById(expenseId);
        if (!expense.getUserId().equals(userId)) {
            throw new ObjectNotFoundException("Expense not found with id: " + expenseId);
        }
        return expense;
    }

    private Card resolveOwnedActiveCard(Integer userId, Integer cardId) {
        if (cardId == null) {
            return null;
        }
        Card card = cardService.findUserCard(userId, cardId);
        if (!Boolean.TRUE.equals(card.getActive())) {
            throw new IllegalArgumentException("Card is inactive");
        }
        return card;
    }

    private void validateExpenseCategory(Integer userId, Integer categoryId) {
        if (categoryId != null) {
            categoryService.requireActiveCategory(userId, categoryId, CategoryType.EXPENSE);
        }
    }

    private void validateExpense(Expense expense) {
        if (expense.getName() == null || expense.getName().isBlank()) {
            throw new IllegalArgumentException("Expense name is required");
        }
        if (expense.getAmount() == null || expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero");
        }
        if (expense.getInstallmentCount() == null || expense.getInstallmentCount() < 1) {
            throw new IllegalArgumentException("Installment count must be greater than zero");
        }
    }

    private void validateMonthYear(Integer month, Integer year) {
        if ((month == null && year != null) || (month != null && year == null)) {
            throw new IllegalArgumentException("Month and year filters must be used together");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (year != null && year < 1900) {
            throw new IllegalArgumentException("Year must be valid");
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
