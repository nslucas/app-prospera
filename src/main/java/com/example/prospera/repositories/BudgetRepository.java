package com.example.prospera.repositories;

import com.example.prospera.Entities.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Integer> {
    List<Budget> findByUserIdAndActiveTrueOrderByYearDescMonthDescCategoryIdAscIdAsc(Integer userId);

    Optional<Budget> findByIdAndUserId(Integer id, Integer userId);

    @Query("SELECT b FROM Budget b WHERE b.userId = :userId AND b.active = true " +
            "AND ((b.month = :month AND b.year = :year) OR (b.month IS NULL AND b.year IS NULL)) " +
            "ORDER BY b.categoryId ASC, b.id ASC")
    List<Budget> findApplicableActiveBudgets(@Param("userId") Integer userId, @Param("month") Integer month,
                                             @Param("year") Integer year);

    @Query("SELECT b FROM Budget b WHERE b.userId = :userId AND b.active = true " +
            "AND ((:categoryId IS NULL AND b.categoryId IS NULL) OR b.categoryId = :categoryId) " +
            "AND ((:month IS NULL AND b.month IS NULL) OR b.month = :month) " +
            "AND ((:year IS NULL AND b.year IS NULL) OR b.year = :year)")
    List<Budget> findActiveByScopeAndPeriod(@Param("userId") Integer userId,
                                            @Param("categoryId") Integer categoryId,
                                            @Param("month") Integer month,
                                            @Param("year") Integer year);
}
