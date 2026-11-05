package ru.mirea.medicinedelivery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.mirea.medicinedelivery.domain.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** DTO-контракты аутентификации и управления сессией. */
public final class AuthDtos {
    private AuthDtos() {
    }

    /** Тип клиента определяет способ доставки refresh-токена (HttpOnly cookie или JSON). */
    public enum ClientType { WEB, NATIVE }

    /** Запрос входа по логину (email) и паролю. */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password,
            ClientType clientType) {
        public ClientType effectiveClientType() {
            return clientType == null ? ClientType.NATIVE : clientType;
        }
    }

    /** Запрос ротации native refresh-токена. */
    public record RefreshRequest(@Size(max = 512) String refreshToken) {
    }

    /** Запрос смены постоянного пароля. */
    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {
    }

    /** Запрос обязательной первичной смены временного пароля. */
    public record InitialPasswordChangeRequest(
            @NotBlank @Size(max = 128) String newPassword) {
    }

    /** Публичная информация о текущем авторизованном пользователе. */
    public record CurrentUser(
            UUID id,
            String email,
            String displayName,
            Set<Role> roles,
            boolean passwordChangeRequired) {
    }

    /** Результат успешного входа или обновления токена. */
    public record AuthResponse(
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            CurrentUser user) {
    }
}
