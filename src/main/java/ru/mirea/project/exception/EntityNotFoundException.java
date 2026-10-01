package ru.mirea.project.exception;

public class EntityNotFoundException extends AppException {
    public EntityNotFoundException(String entityName, Object id) {
        super(String.format("Сущность '%s' с идентификатором '%s' не найдена в системе.", entityName, id));
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
