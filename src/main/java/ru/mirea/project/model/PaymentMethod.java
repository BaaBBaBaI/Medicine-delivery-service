package ru.mirea.project.model;

public enum PaymentMethod {
    CASH("Наличными курьеру"),
    CARD_ON_DELIVERY("Банковской картой курьеру"),
    CARD_ONLINE("Банковской картой онлайн"),
    SBP("Система быстрых платежей (СБП)");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
