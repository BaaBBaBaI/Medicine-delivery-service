package ru.mirea.medicinedelivery.security;

import org.junit.jupiter.api.Test;
import ru.mirea.medicinedelivery.error.BadRequestException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 4. Тест серверной политики безопасности паролей.
 */
class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validate("Pharma-Safe-2026!", "pharmacist@example.org"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordContainingAccountName() {
        assertThatThrownBy(() -> policy.validate("Pharmacist-2026!", "pharmacist@example.org"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsShortOrSimplePassword() {
        assertThatThrownBy(() -> policy.validate("Short1!", "user@example.org"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> policy.validate("AllLettersAndDigits12345", "user@example.org"))
                .isInstanceOf(BadRequestException.class);
    }
}
