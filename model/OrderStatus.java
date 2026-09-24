package ru.mirea.project.model;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    CREATED("Создан", "Заказ оформлен клиентом и ожидает подтверждения", true),
    CONFIRMED("Подтвержден", "Заказ подтвержден фармацевтом, рецепты проверены", true),
    ASSEMBLING("Собирается в аптеке", "Фармацевт комплектует медикаменты", true),
    IN_DELIVERY("Передан курьеру", "Курьер доставляет медикаменты по адресу", true),
    DELIVERED("Доставлен", "Заказ успешно вручен клиенту", false),
    CANCELLED("Отменен", "Заказ аннулирован", false);

    private final String displayName;
    private final String description;
    private final boolean active;

    OrderStatus(String displayName, String description, boolean active) {
        this.displayName = displayName;
        this.description = description;
        this.active = active;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isFinal() {
        return !active;
    }


    public boolean canTransitionTo(OrderStatus next) {
        if (this == next) return true;
        if (isFinal()) {
            return false;
        }

        switch (this) {
            case CREATED:
                return next == CONFIRMED || next == CANCELLED;
            case CONFIRMED:
                return next == ASSEMBLING || next == CANCELLED;
            case ASSEMBLING:
                return next == IN_DELIVERY || next == CANCELLED;
            case IN_DELIVERY:
                return next == DELIVERED || next == CANCELLED;
            default:
                return false;
        }
    }

    public static Set<OrderStatus> getActiveStatuses() {
        return EnumSet.of(CREATED, CONFIRMED, ASSEMBLING, IN_DELIVERY);
    }
}
