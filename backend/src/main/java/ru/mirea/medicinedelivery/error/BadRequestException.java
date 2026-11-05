package ru.mirea.medicinedelivery.error;

/** Сигнал некорректного прикладного запроса (HTTP 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
