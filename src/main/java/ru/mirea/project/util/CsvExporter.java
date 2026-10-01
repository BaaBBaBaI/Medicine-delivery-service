package ru.mirea.project.util;

import ru.mirea.project.model.MedicineOrder;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CsvExporter implements DataExporter {

    @Override
    public String getFormatName() {
        return "CSV (.csv)";
    }

    @Override
    public void exportOrders(List<MedicineOrder> orders, String filePath) throws Exception {
        if (!filePath.toLowerCase().endsWith(".csv")) {
            filePath += ".csv";
        }

        try (FileOutputStream fos = new FileOutputStream(filePath);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {

            fos.write(0xEF);
            fos.write(0xBB);
            fos.write(0xBF);

            writer.write("ID;Номер заказа;ID клиента;ФИО клиента;Телефон;Адрес доставки;Сумма (руб.);Статус;Способ оплаты;Рецептурный;Номер рецепта;Примечание;Дата создания;Дата доставки");
            writer.newLine();

            for (MedicineOrder order : orders) {
                String clientName = order.getClient() != null ? order.getClient().getFullName() : "";
                String regAtStr = order.getCreatedAt() != null ? order.getCreatedAt().format(MedicineOrder.DATE_FORMATTER) : "";
                String delAtStr = order.getDeliveredAt() != null ? order.getDeliveredAt().format(MedicineOrder.DATE_FORMATTER) : "";

                StringBuilder sb = new StringBuilder();
                sb.append(order.getId()).append(";");
                sb.append(escapeCsv(order.getOrderNumber())).append(";");
                sb.append(order.getClientId()).append(";");
                sb.append(escapeCsv(clientName)).append(";");
                sb.append(escapeCsv(order.getContactPhone())).append(";");
                sb.append(escapeCsv(order.getDeliveryAddress())).append(";");
                sb.append(String.format(java.util.Locale.US, "%.2f", order.getTotalAmount())).append(";");
                sb.append(escapeCsv(order.getStatus().getDisplayName())).append(";");
                sb.append(escapeCsv(order.getPaymentMethod().getDisplayName())).append(";");
                sb.append(order.isPrescriptionRequired() ? "Да" : "Нет").append(";");
                sb.append(escapeCsv(order.getPrescriptionNumber() != null ? order.getPrescriptionNumber() : "")).append(";");
                sb.append(escapeCsv(order.getDeliveryNotes() != null ? order.getDeliveryNotes() : "")).append(";");
                sb.append(regAtStr).append(";");
                sb.append(delAtStr);

                writer.write(sb.toString());
                writer.newLine();
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
