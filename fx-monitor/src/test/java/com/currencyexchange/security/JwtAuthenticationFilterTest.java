package com.currencyexchange.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails userDetails(String username) {
        return new User(username, "password", Collections.emptyList());
    }

    @Test
    @DisplayName("a valid ACCESS token authenticates the request and populates the SecurityContext")
    void validAccessTokenSetsAuthentication() throws Exception {
        UserDetails userDetails = userDetails("alice@example.com");
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(tokenProvider.validateToken("good-token")).thenReturn(true);
        when(tokenProvider.getTokenType("good-token")).thenReturn("ACCESS");
        when(tokenProvider.getUsernameFromToken("good-token")).thenReturn("alice@example.com");
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isSameAs(userDetails);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("a REFRESH token is not accepted for request authentication")
    void refreshTokenDoesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer refresh-token");
        when(tokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(tokenProvider.getTokenType("refresh-token")).thenReturn("REFRESH");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("an invalid token leaves the context empty but still passes the request down the chain")
    void invalidTokenIsIgnored() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(tokenProvider.validateToken("bad-token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).getUsernameFromToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("a request without an Authorization header is passed through unauthenticated")
    void missingHeaderIsPassedThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenProvider, userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("a non-Bearer Authorization header is ignored")
    void nonBearerHeaderIsIgnored() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenProvider, userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("an exception while resolving the user is swallowed and the chain still proceeds")
    void exceptionDuringResolutionIsSwallowed() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(tokenProvider.validateToken("good-token")).thenReturn(true);
        when(tokenProvider.getTokenType("good-token")).thenReturn("ACCESS");
        when(tokenProvider.getUsernameFromToken("good-token")).thenReturn("alice@example.com");
        when(userDetailsService.loadUserByUsername("alice@example.com"))
                .thenThrow(new RuntimeException("user store down"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
