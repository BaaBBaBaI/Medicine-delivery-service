package ru.mirea.medicinedelivery.filter;

import org.springframework.stereotype.Component;
import ru.mirea.medicinedelivery.domain.DeliveryPriority;
import ru.mirea.medicinedelivery.domain.OrderStatus;
import ru.mirea.medicinedelivery.error.BadRequestException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбирает пользовательскую поисковую строку в типобезопасный фильтр {@link OrderFilter}.
 *
 * <p>Никакая часть пользовательской строки не конкатенируется в SQL/JPQL: все условия
 * передаются в JPA Criteria API исключительно через типизированные параметры.</p>
 */
@Component
public class SmartFilterParser {
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");
    private static final Pattern AMOUNT = Pattern.compile(
            "^(?:сумма|чек|стоимость|amount|total|price)(<=|>=|=|<|>)([0-9]+(?:[.,][0-9]{1,2})?)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Разбирает строку поиска; по умолчанию возвращает только активные (неархивные) заказы. */
    public OrderFilter parse(String source) {
        List<String> free = new ArrayList<>();
        String customer = null;
        OrderStatus status = null;
        Boolean prescription = null;
        DeliveryPriority priority = null;
        String tag = null;
        Boolean archived = false;
        List<OrderFilter.AmountConstraint> amounts = new ArrayList<>();

        if (source == null || source.isBlank()) {
            return new OrderFilter("", null, null, null, null, null, false, List.of());
        }
        if (source.length() > 500) {
            throw new BadRequestException("Строка фильтра слишком длинная (максимум 500 символов)");
        }

        Matcher matcher = TOKEN.matcher(source.trim());
        while (matcher.find()) {
            String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String token = raw.trim();
            if (token.isEmpty()) continue;
            String lower = token.toLowerCase(Locale.ROOT);

            Matcher amountMatcher = AMOUNT.matcher(lower);
            if (amountMatcher.matches()) {
                BigDecimal value = new BigDecimal(amountMatcher.group(2).replace(',', '.'));
                if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(new BigDecimal("1000000.00")) > 0) {
                    throw new BadRequestException("Сумма заказа в фильтре должна быть в диапазоне от 0.01 до 1000000.00");
                }
                amounts.add(new OrderFilter.AmountConstraint(operator(amountMatcher.group(1)), value));
                continue;
            }

            int colon = token.indexOf(':');
            if (colon > 0) {
                String key = lower.substring(0, colon);
                String value = stripQuotes(token.substring(colon + 1).trim());
                String normalized = value.toLowerCase(Locale.ROOT);
                switch (key) {
                    case "клиент", "заказчик", "customer", "client" -> customer = value;
                    case "статус", "status" -> status = status(normalized);
                    case "рецепт", "prescription", "rx" -> prescription = booleanValue(normalized, "рецепт");
                    case "приоритет", "риск", "priority" -> priority = priority(normalized);
                    case "тег", "tag" -> tag = value;
                    case "архив", "archived" -> archived = archivedValue(normalized);
                    default -> free.add(token);
                }
            } else {
                free.add(token);
            }
        }
        return new OrderFilter(String.join(" ", free), blankToNull(customer), status, prescription,
                priority, blankToNull(tag), archived, List.copyOf(amounts));
    }

    private static OrderFilter.Operator operator(String value) {
        return switch (value) {
            case "<" -> OrderFilter.Operator.LT;
            case "<=" -> OrderFilter.Operator.LTE;
            case "=" -> OrderFilter.Operator.EQ;
            case ">=" -> OrderFilter.Operator.GTE;
            case ">" -> OrderFilter.Operator.GT;
            default -> throw new BadRequestException("Неизвестный оператор сравнения суммы");
        };
    }

    private static OrderStatus status(String value) {
        return switch (value.replace('-', '_')) {
            case "создан", "новый", "created" -> OrderStatus.CREATED;
            case "подтвержден", "подтверждён", "confirmed" -> OrderStatus.CONFIRMED;
            case "собирается", "сборка", "комплектуется", "assembling" -> OrderStatus.ASSEMBLING;
            case "доставка", "в_доставке", "курьер", "in_delivery" -> OrderStatus.IN_DELIVERY;
            case "доставлен", "выполнен", "delivered" -> OrderStatus.DELIVERED;
            case "отменен", "отменён", "cancelled", "canceled" -> OrderStatus.CANCELLED;
            default -> throw new BadRequestException("Неизвестный статус заказа: " + value);
        };
    }

    private static DeliveryPriority priority(String value) {
        return switch (value.replace('-', '_')) {
            case "не_применяется", "неприменимо", "not_applicable", "n/a" -> DeliveryPriority.NOT_APPLICABLE;
            case "низкий", "low" -> DeliveryPriority.LOW;
            case "средний", "обычный", "medium" -> DeliveryPriority.MEDIUM;
            case "высокий", "high" -> DeliveryPriority.HIGH;
            case "критический", "срочный", "critical" -> DeliveryPriority.CRITICAL;
            default -> throw new BadRequestException("Неизвестный приоритет доставки: " + value);
        };
    }

    private static Boolean booleanValue(String value, String field) {
        return switch (value) {
            case "да", "yes", "true", "1" -> true;
            case "нет", "no", "false", "0" -> false;
            default -> throw new BadRequestException("Поле «" + field + "» принимает да/нет или true/false");
        };
    }

    private static Boolean archivedValue(String value) {
        if (value.equals("все") || value.equals("all")) return null;
        return booleanValue(value, "архив");
    }

    private static String stripQuotes(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
