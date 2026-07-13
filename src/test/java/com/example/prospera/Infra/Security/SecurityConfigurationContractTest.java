package com.example.prospera.Infra.Security;

import com.example.prospera.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigurationContractTest.ProbeResource.class,
        properties = "app.cors.allowed-origin-patterns=http://localhost:*")
@Import({SecurityConfiguration.class, SecurityFilter.class,
        SecurityConfigurationContractTest.ProbeConfiguration.class})
class SecurityConfigurationContractTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void exactAuthPostEndpointsArePublic() throws Exception {
        mockMvc.perform(post("/auth/login")).andExpect(status().isOk());
        mockMvc.perform(post("/auth/register")).andExpect(status().isOk());
    }

    @Test
    void otherMethodsAndFinancialEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/auth/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/expenses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void authenticatedUserCanReachProtectedFinancialEndpoint() throws Exception {
        mockMvc.perform(get("/accounts")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void ordinaryUserCannotListAllUsers() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListAllUsers() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isOk());
    }

    @RestController
    public static class ProbeResource {
        @PostMapping("/auth/login")
        ResponseEntity<Void> login() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/auth/register")
        ResponseEntity<Void> register() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/auth/login")
        ResponseEntity<Void> loginWithWrongMethod() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/accounts")
        ResponseEntity<Void> accounts() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/expenses")
        ResponseEntity<Void> expenses() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/users")
        ResponseEntity<Void> users() {
            return ResponseEntity.ok().build();
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        ProbeResource probeResource() {
            return new ProbeResource();
        }
    }
}
