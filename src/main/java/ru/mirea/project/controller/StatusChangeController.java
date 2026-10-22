package ru.mirea.project.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import ru.mirea.project.exception.AppException;
import ru.mirea.project.model.MedicineOrder;
import ru.mirea.project.model.OrderStatus;
import ru.mirea.project.service.MedicineOrderService;

/**
 * Контроллер диалогового окна смены статуса заказа по правилам жизненного цикла.
 */
public class StatusChangeController {

    @FXML private Label orderInfoLabel;
    @FXML private Label currentStatusLabel;
    @FXML private ComboBox<OrderStatus> nextStatusComboBox;
    @FXML private Label statusDescriptionLabel;
    @FXML private Label errorLabel;

    private Stage dialogStage;
    private MedicineOrderService orderService;
    private MedicineOrder order;
    private boolean changed = false;

    @FXML
    public void initialize() {
        nextStatusComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(OrderStatus status) {
                return status != null ? (status.getDisplayName() + " (" + status.name() + ")") : "";
            }

            @Override
            public OrderStatus fromString(String string) {
                return null;
            }
        });

        nextStatusComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                statusDescriptionLabel.setText(newV.getDescription());
            } else {
                statusDescriptionLabel.setText("");
            }
        });
    }

    public void initDialog(Stage dialogStage, MedicineOrderService orderService, MedicineOrder order) {
        this.dialogStage = dialogStage;
        this.orderService = orderService;
        this.order = order;

        orderInfoLabel.setText(String.format("Заказ № %s — Клиент: %s",
                order.getOrderNumber(), order.getClientDisplayName()));
        currentStatusLabel.setText(order.getStatus().getDisplayName() + " (" + order.getStatus().getDescription() + ")");

        List<OrderStatus> allowedNext = Arrays.stream(OrderStatus.values())
                .filter(st -> st != order.getStatus() && order.getStatus().canTransitionTo(st))
                .collect(Collectors.toList());

        nextStatusComboBox.setItems(FXCollections.observableArrayList(allowedNext));
        if (!allowedNext.isEmpty()) {
            nextStatusComboBox.getSelectionModel().selectFirst();
        }
    }

    @FXML
    public void handleApplyStatus() {
        OrderStatus target = nextStatusComboBox.getValue();
        if (target == null) {
            showError("Выберите новый статус из списка доступных переходов.");
            return;
        }

        try {
            orderService.changeOrderStatus(order.getId(), target);
            this.changed = true;
            dialogStage.close();
        } catch (AppException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            showError("Ошибка обновления статуса: " + e.getMessage());
        }
    }

    @FXML
    public void handleCancel() {
        dialogStage.close();
    }

    public boolean isChanged() {
        return changed;
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
