package ru.mirea.project.model;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    CREATED("Создан", true),
    CONFIRMED("Подтвержден", true),
    ASSEMBLING("Собирается в аптеке", true),
    IN_DELIVERY("Передан курьеру", true),
    DELIVERED("Доставлен", false),
    CANCELLED("Отменен", false);

    private final String displayName;
    private final boolean active;

    OrderStatus(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }

    public String getDisplayName() {
        return displayName;
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

    @Override
    public String toString() {
        return displayName;
    }
}
