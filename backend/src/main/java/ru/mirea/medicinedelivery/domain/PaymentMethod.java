package ru.mirea.medicinedelivery.domain;

/**
 * Способ оплаты заказа доставки лекарств (из КР №1 / КР №2).
 */
public enum PaymentMethod {
    /** Наличными курьеру при получении. */
    CASH,
    /** Банковской картой курьеру через терминал. */
    CARD_ON_DELIVERY,
    /** Банковской картой онлайн. */
    CARD_ONLINE,
    /** Система быстрых платежей (СБП). */
    SBP
}
