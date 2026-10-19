package ru.mirea.project.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import ru.mirea.project.model.OrderStatistics;
import ru.mirea.project.model.OrderStatus;
import ru.mirea.project.model.PaymentMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Контроллер окна аналитики и статистики (10 статистических показателей + таблицы и графики).
 */
public class StatisticsController {

    @FXML private Label totalOrdersLabel;
    @FXML private Label totalClientsLabel;
    @FXML private Label activeOrdersLabel;
    @FXML private Label deliveredOrdersLabel;
    @FXML private Label cancelledOrdersLabel;
    @FXML private Label deliveredRevenueLabel;
    @FXML private Label averageCheckLabel;
    @FXML private Label minMaxOrderLabel;
    @FXML private Label prescriptionShareLabel;

    @FXML private TableView<Map.Entry<String, String>> statusBreakdownTable;
    @FXML private TableColumn<Map.Entry<String, String>, String> colStatusName;
    @FXML private TableColumn<Map.Entry<String, String>, String> colStatusCount;

    @FXML private TableView<Map.Entry<String, String>> paymentBreakdownTable;
    @FXML private TableColumn<Map.Entry<String, String>, String> colPaymentName;
    @FXML private TableColumn<Map.Entry<String, String>, String> colPaymentCount;

    @FXML private PieChart statusPieChart;
    @FXML private BarChart<String, Number> paymentBarChart;

    private Stage dialogStage;

    @FXML
    public void initialize() {
        colStatusName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));
        colStatusCount.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValue()));

        colPaymentName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));
        colPaymentCount.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValue()));
    }

    public void initDialog(Stage dialogStage, OrderStatistics stats) {
        this.dialogStage = dialogStage;

        totalOrdersLabel.setText(stats.getTotalOrders() + " шт.");
        totalClientsLabel.setText(stats.getTotalClients() + " чел.");
        activeOrdersLabel.setText(stats.getActiveOrdersCount() + " шт.");
        deliveredOrdersLabel.setText(stats.getDeliveredOrdersCount() + " шт.");
        cancelledOrdersLabel.setText(stats.getCancelledOrdersCount() + " шт.");
        deliveredRevenueLabel.setText(String.format(Locale.US, "%.2f ₽", stats.getTotalDeliveredRevenue()));
        averageCheckLabel.setText(String.format(Locale.US, "%.2f ₽", stats.getAverageOrderAmount()));
        minMaxOrderLabel.setText(String.format(Locale.US, "%.2f ₽ / %.2f ₽",
                stats.getMinOrderAmount(), stats.getMaxOrderAmount()));
        prescriptionShareLabel.setText(String.format(Locale.US, "%d шт. (%.1f%%)",
                stats.getPrescriptionOrdersCount(), stats.getPrescriptionPercentage()));

        // Таблица и PieChart по статусам
        List<Map.Entry<String, String>> statusRows = new ArrayList<>();
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (OrderStatus st : OrderStatus.values()) {
            long count = stats.getOrdersByStatus().getOrDefault(st, 0L);
            double pct = stats.getTotalOrders() > 0 ? (count * 100.0 / stats.getTotalOrders()) : 0.0;
            statusRows.add(Map.entry(st.getDisplayName(), String.format(Locale.US, "%d шт. (%.1f%%)", count, pct)));
            if (count > 0) {
                pieData.add(new PieChart.Data(st.getDisplayName() + " (" + count + ")", count));
            }
        }
        statusBreakdownTable.setItems(FXCollections.observableArrayList(statusRows));
        statusPieChart.setData(pieData);

        // Таблица и BarChart по способам оплаты
        List<Map.Entry<String, String>> paymentRows = new ArrayList<>();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Количество заказов");
        for (PaymentMethod pm : PaymentMethod.values()) {
            long count = stats.getOrdersByPaymentMethod().getOrDefault(pm, 0L);
            double pct = stats.getTotalOrders() > 0 ? (count * 100.0 / stats.getTotalOrders()) : 0.0;
            paymentRows.add(Map.entry(pm.getDisplayName(), String.format(Locale.US, "%d шт. (%.1f%%)", count, pct)));
            series.getData().add(new XYChart.Data<>(pm.getDisplayName(), count));
        }
        paymentBreakdownTable.setItems(FXCollections.observableArrayList(paymentRows));
        paymentBarChart.getData().clear();
        paymentBarChart.getData().add(series);
    }

    @FXML
    public void handleClose() {
        dialogStage.close();
    }
}
