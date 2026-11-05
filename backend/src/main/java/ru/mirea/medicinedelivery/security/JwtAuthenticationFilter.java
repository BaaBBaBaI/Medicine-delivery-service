package ru.mirea.medicinedelivery.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.mirea.medicinedelivery.repository.UserAccountRepository;

import java.io.IOException;
import java.util.UUID;

/**
 * Создаёт SecurityContext из access JWT.
 *
 * <p>Прикладной REST API использует заголовок Authorization Bearer. Для Swagger UI
 * дополнительно поддерживается HttpOnly cookie администратора.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String SWAGGER_COOKIE = "meddelivery_swagger";

    private final JwtService jwtService;
    private final UserAccountRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = bearerToken(request);
            if (token == null && isSwaggerRequest(request)) {
                token = cookieValue(request, SWAGGER_COOKIE);
            }
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring(7)
                : null;
    }

    private boolean isSwaggerRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/swagger-ui.html")
                || uri.startsWith("/swagger-ui/")
                || uri.equals("/v3/api-docs")
                || uri.startsWith("/v3/api-docs/");
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void authenticate(String token) {
        if (token == null || token.isBlank()) return;
        try {
            Claims claims = jwtService.parse(token);
            UUID userId = UUID.fromString(claims.getSubject());
            users.findById(userId)
                    .filter(UserAccount -> UserAccount.isEnabled())
                    .ifPresent(user -> {
                        UserPrincipal principal = UserPrincipal.from(user);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        principal.getAuthorities()
                                );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        } catch (RuntimeException ignored) {
            SecurityContextHolder.clearContext();
        }
    }
}
