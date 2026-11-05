package ru.mirea.medicinedelivery.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Единый безопасный JSON-формат ошибки REST API.
 *
 * <p>Клиент получает только предусмотренные поля ошибки. Stack trace, SQL и имена
 * внутренних классов в ответ никогда не включаются.</p>
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> validationErrors) {
}
