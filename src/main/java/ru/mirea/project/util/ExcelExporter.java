package ru.mirea.project.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.project.model.MedicineOrder;

import java.io.FileOutputStream;
import java.util.List;

public class ExcelExporter implements DataExporter {

    @Override
    public String getFormatName() {
        return "Excel (.xlsx)";
    }

    @Override
    public void exportOrders(List<MedicineOrder> orders, String filePath) throws Exception {
        if (!filePath.toLowerCase().endsWith(".xlsx")) {
            filePath += ".xlsx";
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Заказы доставки лекарств");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            String[] headers = {
                    "ID", "Номер заказа", "Клиент", "Телефон", "Адрес доставки",
                    "Сумма (руб.)", "Статус", "Оплата", "Рецепт", "№ Рецепта",
                    "Примечание", "Дата создания", "Дата доставки"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (MedicineOrder order : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(18);

                Cell c0 = row.createCell(0);
                c0.setCellValue(order.getId() != null ? order.getId() : 0);
                c0.setCellStyle(dataStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(order.getOrderNumber());
                c1.setCellStyle(dataStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(order.getClientDisplayName());
                c2.setCellStyle(dataStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(order.getContactPhone());
                c3.setCellStyle(dataStyle);

                Cell c4 = row.createCell(4);
                c4.setCellValue(order.getDeliveryAddress());
                c4.setCellStyle(dataStyle);

                Cell c5 = row.createCell(5);
                c5.setCellValue(order.getTotalAmount());
                c5.setCellStyle(currencyStyle);

                Cell c6 = row.createCell(6);
                c6.setCellValue(order.getStatus().getDisplayName());
                c6.setCellStyle(dataStyle);

                Cell c7 = row.createCell(7);
                c7.setCellValue(order.getPaymentMethod().getDisplayName());
                c7.setCellStyle(dataStyle);

                Cell c8 = row.createCell(8);
                c8.setCellValue(order.isPrescriptionRequired() ? "Да" : "Нет");
                c8.setCellStyle(dataStyle);

                Cell c9 = row.createCell(9);
                c9.setCellValue(order.getPrescriptionNumber() != null ? order.getPrescriptionNumber() : "—");
                c9.setCellStyle(dataStyle);

                Cell c10 = row.createCell(10);
                c10.setCellValue(order.getDeliveryNotes() != null ? order.getDeliveryNotes() : "");
                c10.setCellStyle(dataStyle);

                Cell c11 = row.createCell(11);
                c11.setCellValue(order.getCreatedAtFormatted());
                c11.setCellStyle(dateStyle);

                Cell c12 = row.createCell(12);
                c12.setCellValue(order.getDeliveredAtFormatted());
                c12.setCellStyle(dateStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(currentWidth + 1000, 15000));
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);

        style.setFillForegroundColor(IndexedColors.TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00 \"₽\""));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private CellStyle createDateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
