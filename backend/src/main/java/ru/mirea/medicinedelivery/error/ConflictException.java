package ru.mirea.medicinedelivery.error;

/** Сигнал бизнес-конфликта или конфликта версий optimistic locking (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
