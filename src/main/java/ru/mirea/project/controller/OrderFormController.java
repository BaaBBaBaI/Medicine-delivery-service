package ru.mirea.project.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import ru.mirea.project.exception.AppException;
import ru.mirea.project.model.MedicineOrder;
import ru.mirea.project.model.PaymentMethod;
import ru.mirea.project.model.User;
import ru.mirea.project.service.MedicineOrderService;

import java.util.List;
import java.util.Locale;

/**
 * Контроллер диалогового окна создания и редактирования заказа доставки лекарств.
 * Обеспечивает выбор связанной сущности (User) через ComboBox без ручного ввода ID и валидацию полей.
 */
public class OrderFormController {

    @FXML private Label formTitleLabel;
    @FXML private TextField orderNumberField;
    @FXML private ComboBox<User> clientComboBox;
    @FXML private TextField deliveryAddressField;
    @FXML private TextField contactPhoneField;
    @FXML private TextField totalAmountField;
    @FXML private ComboBox<PaymentMethod> paymentMethodComboBox;
    @FXML private CheckBox prescriptionRequiredCheckBox;
    @FXML private TextField prescriptionNumberField;
    @FXML private TextArea deliveryNotesArea;
    @FXML private Label errorBannerLabel;

    private Stage dialogStage;
    private MedicineOrderService orderService;
    private MedicineOrder editingOrder;
    private MedicineOrder savedOrder;
    private boolean saved = false;

    @FXML
    public void initialize() {
        paymentMethodComboBox.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        paymentMethodComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(PaymentMethod method) {
                return method != null ? method.getDisplayName() : "";
            }

            @Override
            public PaymentMethod fromString(String string) {
                return null;
            }
        });
        paymentMethodComboBox.getSelectionModel().select(PaymentMethod.CARD_ONLINE);

        clientComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(User user) {
                return user != null ? String.format("%s — %s (%s)", user.getFullName(), user.getPhone(), user.getEmail()) : "";
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        });

        prescriptionNumberField.disableProperty().bind(prescriptionRequiredCheckBox.selectedProperty().not());
        prescriptionRequiredCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (!isSelected) {
                prescriptionNumberField.clear();
                prescriptionNumberField.getStyleClass().remove("input-error");
            }
        });
    }

    public void initDialog(Stage dialogStage, MedicineOrderService orderService, List<User> clients, MedicineOrder orderToEdit) {
        this.dialogStage = dialogStage;
        this.orderService = orderService;
        this.editingOrder = orderToEdit;

        clientComboBox.setItems(FXCollections.observableArrayList(clients));

        if (orderToEdit == null) {
            formTitleLabel.setText("Оформление нового заказа доставки лекарств");
            orderNumberField.setPromptText("Сгенерируется автоматически (например, MED-2026-1045)");
            if (!clients.isEmpty()) {
                clientComboBox.getSelectionModel().selectFirst();
                handleUseClientDefaults();
            }
        } else {
            formTitleLabel.setText("Редактирование заказа " + orderToEdit.getOrderNumber());
            orderNumberField.setText(orderToEdit.getOrderNumber());
            orderNumberField.setDisable(true);

            clients.stream()
                    .filter(u -> u.getId().equals(orderToEdit.getClientId()))
                    .findFirst()
                    .ifPresent(u -> clientComboBox.getSelectionModel().select(u));

            deliveryAddressField.setText(orderToEdit.getDeliveryAddress());
            contactPhoneField.setText(orderToEdit.getContactPhone());
            totalAmountField.setText(String.format(Locale.US, "%.2f", orderToEdit.getTotalAmount()));
            paymentMethodComboBox.getSelectionModel().select(orderToEdit.getPaymentMethod());
            prescriptionRequiredCheckBox.setSelected(orderToEdit.isPrescriptionRequired());
            prescriptionNumberField.setText(orderToEdit.getPrescriptionNumber() != null ? orderToEdit.getPrescriptionNumber() : "");
            deliveryNotesArea.setText(orderToEdit.getDeliveryNotes() != null ? orderToEdit.getDeliveryNotes() : "");
        }
    }

    @FXML
    public void handleUseClientDefaults() {
        User selectedClient = clientComboBox.getValue();
        if (selectedClient != null) {
            deliveryAddressField.setText(selectedClient.getDefaultAddress());
            contactPhoneField.setText(selectedClient.getPhone());
        }
    }

    @FXML
    public void handleSave() {
        clearErrorStyles();

        User selectedClient = clientComboBox.getValue();
        if (selectedClient == null) {
            markError(clientComboBox, "Выберите клиента-заказчика из выпадающего списка.");
            return;
        }

        String address = deliveryAddressField.getText() != null ? deliveryAddressField.getText().trim() : "";
        if (address.length() < 5 || address.length() > 255) {
            markError(deliveryAddressField, "Поле «Адрес доставки» должно содержать от 5 до 255 символов.");
            return;
        }

        String phone = contactPhoneField.getText() != null ? contactPhoneField.getText().trim() : "";
        if (phone.length() < 6 || phone.length() > 25) {
            markError(contactPhoneField, "Поле «Контактный телефон» должно содержать от 6 до 25 символов.");
            return;
        }

        String amountText = totalAmountField.getText() != null ? totalAmountField.getText().trim().replace(",", ".") : "";
        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException e) {
            markError(totalAmountField, "Поле «Сумма заказа» должно быть числом (например, 1850.50).");
            return;
        }
        if (amount <= 0.0 || amount > 100_000.0) {
            markError(totalAmountField, "Сумма заказа должна находиться в допустимом диапазоне от 0.01 до 100 000.00 ₽.");
            return;
        }

        PaymentMethod paymentMethod = paymentMethodComboBox.getValue();
        if (paymentMethod == null) {
            markError(paymentMethodComboBox, "Выберите способ оплаты заказа.");
            return;
        }

        boolean rxRequired = prescriptionRequiredCheckBox.isSelected();
        String rxNumber = prescriptionNumberField.getText() != null ? prescriptionNumberField.getText().trim() : "";
        if (rxRequired && rxNumber.isEmpty()) {
            markError(prescriptionNumberField, "Для рецептурного заказа обязательно укажите номер рецепта (например, RX-77-90214).");
            return;
        }
        if (rxRequired && rxNumber.length() > 60) {
            markError(prescriptionNumberField, "Номер рецепта не должен превышать 60 символов.");
            return;
        }

        try {
            if (editingOrder == null) {
                MedicineOrder newOrder = new MedicineOrder();
                String customOrderNum = orderNumberField.getText() != null ? orderNumberField.getText().trim() : "";
                if (!customOrderNum.isEmpty()) {
                    newOrder.setOrderNumber(customOrderNum);
                }
                newOrder.setClientId(selectedClient.getId());
                newOrder.setClient(selectedClient);
                newOrder.setDeliveryAddress(address);
                newOrder.setContactPhone(phone);
                newOrder.setTotalAmount(amount);
                newOrder.setPaymentMethod(paymentMethod);
                newOrder.setPrescriptionRequired(rxRequired);
                newOrder.setPrescriptionNumber(rxRequired ? rxNumber : null);
                newOrder.setDeliveryNotes(deliveryNotesArea.getText() != null ? deliveryNotesArea.getText().trim() : "");

                this.savedOrder = orderService.createOrder(newOrder);
            } else {
                MedicineOrder updated = new MedicineOrder();
                updated.setId(editingOrder.getId());
                updated.setOrderNumber(editingOrder.getOrderNumber());
                updated.setClientId(selectedClient.getId());
                updated.setClient(selectedClient);
                updated.setDeliveryAddress(address);
                updated.setContactPhone(phone);
                updated.setTotalAmount(amount);
                updated.setPaymentMethod(paymentMethod);
                updated.setPrescriptionRequired(rxRequired);
                updated.setPrescriptionNumber(rxRequired ? rxNumber : null);
                updated.setDeliveryNotes(deliveryNotesArea.getText() != null ? deliveryNotesArea.getText().trim() : "");

                this.savedOrder = orderService.updateOrder(updated);
            }

            this.saved = true;
            dialogStage.close();
        } catch (AppException e) {
            showErrorBanner(e.getMessage());
        } catch (Exception e) {
            showErrorBanner("Ошибка сохранения в базу данных: " + e.getMessage());
        }
    }

    @FXML
    public void handleCancel() {
        dialogStage.close();
    }

    public boolean isSaved() {
        return saved;
    }

    public MedicineOrder getSavedOrder() {
        return savedOrder;
    }

    private void markError(Control control, String message) {
        if (!control.getStyleClass().contains("input-error")) {
            control.getStyleClass().add("input-error");
        }
        control.requestFocus();
        showErrorBanner(message);
    }

    private void showErrorBanner(String message) {
        errorBannerLabel.setText(message);
        errorBannerLabel.setVisible(true);
        errorBannerLabel.setManaged(true);
    }

    private void clearErrorStyles() {
        orderNumberField.getStyleClass().remove("input-error");
        clientComboBox.getStyleClass().remove("input-error");
        deliveryAddressField.getStyleClass().remove("input-error");
        contactPhoneField.getStyleClass().remove("input-error");
        totalAmountField.getStyleClass().remove("input-error");
        paymentMethodComboBox.getStyleClass().remove("input-error");
        prescriptionNumberField.getStyleClass().remove("input-error");
        errorBannerLabel.setText("");
        errorBannerLabel.setVisible(false);
        errorBannerLabel.setManaged(false);
    }
}
