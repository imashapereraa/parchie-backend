package com.parchie.web;

import com.parchie.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the `Authorization: Bearer <token>` header, looks the token up via
 * AuthService, and parks the User on the request as {@link CurrentUser}.
 * Endpoints decide whether they require it via {@code @AuthenticatedUser}.
 *
 * This filter never rejects requests — missing/invalid tokens just leave the
 * attribute unset. That keeps anonymous endpoints (existing /api/sessions
 * routes) working without changes; the resolver enforces auth where needed.
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AuthService authService;

    public AuthTokenFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            authService.resolve(token).ifPresent(u ->
                    request.setAttribute(CurrentUser.REQUEST_ATTR, u));
        }
        chain.doFilter(request, response);
    }
}
