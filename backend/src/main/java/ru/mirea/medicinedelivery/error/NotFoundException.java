package ru.mirea.medicinedelivery.error;

/** Сигнал отсутствия запрошенной записи (HTTP 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
