package com.currencyexchange.controller;

import com.currencyexchange.dto.auth.AuthResponseDTO;
import com.currencyexchange.dto.auth.LoginRequestDTO;
import com.currencyexchange.dto.auth.RegisterRequestDTO;
import com.currencyexchange.entity.User;
import com.currencyexchange.service.AuthService;
import com.currencyexchange.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Standalone setup keeps this a focused MVC slice: no security filter chain,
        // just the controller wired to mocked services.
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    private Authentication authFor(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 201 with the created auth response")
    void registerReturnsCreated() throws Exception {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "alice@example.com", "s3cret!", "Alice Smith", "+15551234", "US");
        AuthResponseDTO response = AuthResponseDTO.builder()
                .userId(1L)
                .email("alice@example.com")
                .fullName("Alice Smith")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(3600)
                .build();
        when(authService.register(any(RegisterRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.accessToken").value("access-token"));

        ArgumentCaptor<RegisterRequestDTO> captor = ArgumentCaptor.forClass(RegisterRequestDTO.class);
        verify(authService).register(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getEmail())
                .isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 with tokens")
    void loginReturnsOk() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("bob@example.com", "password");
        AuthResponseDTO response = AuthResponseDTO.builder()
                .userId(2L)
                .email("bob@example.com")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(3600)
                .build();
        when(authService.login(any(LoginRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("GET /api/auth/profile maps the authenticated user's fields")
    void profileReturnsCurrentUser() throws Exception {
        User user = new User();
        user.setId(5L);
        user.setEmail("carol@example.com");
        user.setFullName("Carol Jones");
        user.setPhoneNumber("+15559999");
        user.setCountry("GB");
        user.setKycStatus("APPROVED");
        user.setIsEmailVerified(true);
        user.setTwoFactorEnabled(false);
        user.setIsActive(true);
        when(userService.getUserByEmail("carol@example.com")).thenReturn(user);

        mockMvc.perform(get("/api/auth/profile").principal(authFor("carol@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.email").value("carol@example.com"))
                .andExpect(jsonPath("$.fullName").value("Carol Jones"))
                .andExpect(jsonPath("$.kycStatus").value("APPROVED"))
                .andExpect(jsonPath("$.isEmailVerified").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/change-password delegates to the service for the current user")
    void changePasswordDelegates() throws Exception {
        User user = new User();
        user.setId(9L);
        user.setEmail("dave@example.com");
        when(userService.getUserByEmail("dave@example.com")).thenReturn(user);

        String body = "{\"currentPassword\":\"old\",\"newPassword\":\"newpass123\"}";

        mockMvc.perform(post("/api/auth/change-password")
                        .principal(authFor("dave@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("Password changed successfully"));

        verify(authService).changePassword(org.mockito.ArgumentMatchers.eq(9L), any());
    }

    @Test
    @DisplayName("GET /api/auth/health reports the API is running")
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/auth/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("API is running"));
    }
}
