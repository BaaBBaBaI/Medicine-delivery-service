package ru.mirea.project.util;

import ru.mirea.project.model.MedicineOrder;

import java.util.List;

/**
 * Интерфейс экспортера данных (демонстрация полиморфизма).
 */
public interface DataExporter {
    void exportOrders(List<MedicineOrder> orders, String filePath) throws Exception;
    String getFormatName();
}
