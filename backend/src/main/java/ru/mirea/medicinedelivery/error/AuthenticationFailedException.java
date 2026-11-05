package ru.mirea.medicinedelivery.error;

/**
 * Общая ошибка аутентификации (HTTP 401) без раскрытия существования учётной записи.
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() { super("Неверные учётные данные или доступ временно заблокирован"); }
}
