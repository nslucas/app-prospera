package com.example.prospera.Services;

import com.example.prospera.DTO.CategoryRecord;
import com.example.prospera.Entities.Category;
import com.example.prospera.Entities.CategoryType;
import com.example.prospera.Entities.User;
import com.example.prospera.Exceptions.ObjectNotFoundException;
import com.example.prospera.repositories.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private UserPreferenceService userPreferenceService;

    @Test
    void createCategoryUsesAuthenticatedUser() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        CategoryRecord record = new CategoryRecord(null, " Groceries ", CategoryType.EXPENSE, true);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndActiveTrue(1, "Groceries")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryService service = new CategoryService(categoryRepository, authenticatedUserService,
                userPreferenceService);
        service.create(record);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertEquals("Groceries", captor.getValue().getName());
        assertEquals(1, captor.getValue().getUserId());
        assertEquals(true, captor.getValue().getActive());
    }

    @Test
    void duplicateActiveCategoryNameIsRejected() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        CategoryRecord record = new CategoryRecord(null, "Groceries", CategoryType.EXPENSE, true);
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(categoryRepository.findByUserIdAndNameIgnoreCaseAndActiveTrue(1, "Groceries"))
                .thenReturn(Optional.of(new Category(10, "Groceries", CategoryType.EXPENSE, true, 1)));

        CategoryService service = new CategoryService(categoryRepository, authenticatedUserService,
                userPreferenceService);

        assertThrows(IllegalArgumentException.class, () -> service.create(record));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void findByIdCannotReadAnotherUsersCategory() {
        User user = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(1000), "lucas@test.com");
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.empty());
        CategoryService service = new CategoryService(categoryRepository, authenticatedUserService,
                userPreferenceService);

        assertThrows(ObjectNotFoundException.class, () -> service.findAuthenticatedUserCategory(10));

        verify(categoryRepository).findByIdAndUserId(10, 1);
        verify(categoryRepository, never()).findById(10);
    }
}
