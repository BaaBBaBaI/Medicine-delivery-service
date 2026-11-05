package ru.mirea.medicinedelivery.domain;

/**
 * Статус жизненного цикла заказа доставки лекарств (из КР №1 / КР №2).
 */
public enum OrderStatus {
    /** Заказ создан и ожидает проверки фармацевтом. */
    CREATED,
    /** Заказ подтверждён диспетчером-фармацевтом. */
    CONFIRMED,
    /** Заказ комплектуется в аптеке. */
    ASSEMBLING,
    /** Заказ передан курьеру и находится в доставке. */
    IN_DELIVERY,
    /** Заказ успешно доставлен клиенту. */
    DELIVERED,
    /** Заказ отменён. */
    CANCELLED;

    /** Проверяет, завершён ли жизненный цикл заказа. */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
