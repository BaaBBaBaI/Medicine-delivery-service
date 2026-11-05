package ru.mirea.medicinedelivery.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.medicinedelivery.config.AppProperties;
import ru.mirea.medicinedelivery.domain.RefreshSession;
import ru.mirea.medicinedelivery.domain.UserAccount;
import ru.mirea.medicinedelivery.dto.AuthDtos.AuthResponse;
import ru.mirea.medicinedelivery.dto.AuthDtos.ChangePasswordRequest;
import ru.mirea.medicinedelivery.dto.AuthDtos.CurrentUser;
import ru.mirea.medicinedelivery.dto.AuthDtos.InitialPasswordChangeRequest;
import ru.mirea.medicinedelivery.dto.AuthDtos.LoginRequest;
import ru.mirea.medicinedelivery.error.AuthenticationFailedException;
import ru.mirea.medicinedelivery.error.BadRequestException;
import ru.mirea.medicinedelivery.repository.RefreshSessionRepository;
import ru.mirea.medicinedelivery.repository.UserAccountRepository;
import ru.mirea.medicinedelivery.security.CurrentUserService;
import ru.mirea.medicinedelivery.security.JwtService;
import ru.mirea.medicinedelivery.security.PasswordPolicy;
import ru.mirea.medicinedelivery.web.RequestMetadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Сервис аутентификации, ротации refresh-сессий, выхода и смены пароля. */
@Service
public class AuthService {
    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptService attempts;
    private final JwtService jwtService;
    private final CurrentUserService currentUser;
    private final AppProperties properties;
    private final RequestMetadata metadata;
    private final AuditService audit;
    private final String dummyHash;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserAccountRepository users, RefreshSessionRepository sessions,
                       PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                       LoginAttemptService attempts, JwtService jwtService,
                       CurrentUserService currentUser, AppProperties properties,
                       RequestMetadata metadata, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.attempts = attempts;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.properties = properties;
        this.metadata = metadata;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode("constant-dummy-password-never-used");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserAccount user = users.findByEmailIgnoreCase(email).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !user.isEnabled() || attempts.isLocked(user) || !passwordMatches) {
            if (user != null && user.isEnabled()) attempts.recordFailure(user.getId());
            throw new AuthenticationFailedException();
        }
        attempts.recordSuccess(user.getId());
        AuthResponse response = issueSession(user);
        audit.record("LOGIN_SUCCESS", "USER", user.getId(), "Успешный вход в систему",
                Map.of("clientType", request.effectiveClientType().name()));
        return response;
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) {
            throw new AuthenticationFailedException();
        }
        RefreshSession old = sessions.findByTokenHash(hash(rawToken)).orElseThrow(AuthenticationFailedException::new);
        Instant now = Instant.now();
        if (old.getRevokedAt() != null) {
            if (old.getReplacedBy() != null) {
                sessions.revokeAllForUser(old.getUser().getId(), now);
                audit.record("REFRESH_REUSE_DETECTED", "USER", old.getUser().getId(),
                        "Обнаружено повторное использование refresh-токена", Map.of());
            }
            throw new AuthenticationFailedException();
        }
        if (!old.getExpiresAt().isAfter(now) || !old.getUser().isEnabled()) {
            old.setRevokedAt(now);
            throw new AuthenticationFailedException();
        }

        String nextRaw = randomToken();
        Instant nextExpiry = now.plus(properties.jwt().refreshDays(), ChronoUnit.DAYS);
        RefreshSession next = new RefreshSession(UUID.randomUUID(), old.getUser(), hash(nextRaw), nextExpiry,
                metadata.userAgent(), metadata.ipAddress());
        sessions.save(next);
        old.setRevokedAt(now);
        old.setLastUsedAt(now);
        old.setReplacedBy(next.getId());

        JwtService.IssuedAccessToken access = jwtService.issue(old.getUser());
        return new AuthResponse(access.token(), access.expiresAt(), nextRaw, nextExpiry, toCurrentUser(old.getUser()));
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) return;
        sessions.findByTokenHash(hash(rawToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) session.setRevokedAt(Instant.now());
        });
    }

    @Transactional(readOnly = true)
    public CurrentUser me() {
        return toCurrentUser(currentUser.requireEntity());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserAccount user = currentUser.requireEntity();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }
        applyNewPassword(user, request.newPassword());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.saveAndFlush(user);
        sessions.revokeAllForUser(user.getId(), Instant.now());
        audit.record("PASSWORD_CHANGED", "USER", user.getId(), "Пользователь изменил пароль", Map.of());
    }

    @Transactional
    public void changeInitialPassword(InitialPasswordChangeRequest request) {
        UserAccount user = currentUser.requireEntity();
        if (!user.isPasswordChangeRequired()) {
            throw new BadRequestException("Первичная смена временного пароля уже выполнена");
        }
        applyNewPassword(user, request.newPassword());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.saveAndFlush(user);
        audit.record("INITIAL_PASSWORD_CHANGED", "USER", user.getId(),
                "Пользователь заменил временный пароль", Map.of());
    }

    private void applyNewPassword(UserAccount user, String newPassword) {
        passwordPolicy.validate(newPassword, user.getEmail());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Новый пароль должен отличаться от текущего");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
    }

    private AuthResponse issueSession(UserAccount user) {
        JwtService.IssuedAccessToken access = jwtService.issue(user);
        String rawRefresh = randomToken();
        Instant refreshExpiry = Instant.now().plus(properties.jwt().refreshDays(), ChronoUnit.DAYS);
        sessions.save(new RefreshSession(UUID.randomUUID(), user, hash(rawRefresh), refreshExpiry,
                metadata.userAgent(), metadata.ipAddress()));
        return new AuthResponse(access.token(), access.expiresAt(), rawRefresh, refreshExpiry, toCurrentUser(user));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static CurrentUser toCurrentUser(UserAccount user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(),
                Set.copyOf(user.getRoles()), user.isPasswordChangeRequired());
    }
}
