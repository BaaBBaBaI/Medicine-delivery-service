package ru.mirea.project.exception;

/**
 * Исключение, выбрасываемое при нарушении бизнес-правил предметной области.
 */
public class BusinessException extends AppException {
    public BusinessException(String message) {
        super(message);
    }
}
