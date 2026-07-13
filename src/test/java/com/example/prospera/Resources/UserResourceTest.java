package com.example.prospera.Resources;

import com.example.prospera.Entities.User;
import com.example.prospera.Services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserResourceTest {

    @Test
    void updateReturnsSafeUserRecordWithoutPasswordHash() throws Exception {
        UserService service = mock(UserService.class);
        User input = new User("Lucas", "Nunes", BigDecimal.valueOf(3000), "user@example.com");
        User updated = new User(1, "Lucas", "Nunes", BigDecimal.valueOf(3000), "user@example.com",
                "$2a$10$sensitive-password-hash");
        when(service.fromDTO(any())).thenReturn(input);
        when(service.updateAuthenticatedUser(input)).thenReturn(updated);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserResource(service)).build();

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lucas","lastName":"Nunes","monthLimit":3000,
                                 "email":"user@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lucas"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.authorities").doesNotExist());
    }
}
