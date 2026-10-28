package ru.mirea.project.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import ru.mirea.project.exception.AppException;
import ru.mirea.project.model.MedicineOrder;
import ru.mirea.project.model.User;
import ru.mirea.project.service.MedicineOrderService;
import ru.mirea.project.service.UserService;
import ru.mirea.project.util.DialogUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер окна управления связанной сущностью — Клиентами (User).
 * Позволяет просматривать клиентов и их заказы, искать, добавлять, редактировать и удалять клиентов.
 */
public class ClientsController {

    @FXML private TextField searchClientField;
    @FXML private TableView<User> clientsTable;
    @FXML private TableColumn<User, String> colId;
    @FXML private TableColumn<User, String> colFullName;
    @FXML private TableColumn<User, String> colPhone;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colAddress;
    @FXML private TableColumn<User, String> colRegisteredAt;

    @FXML private Label formModeLabel;
    @FXML private TextField fullNameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField addressField;
    @FXML private Label clientErrorLabel;
    @FXML private ListView<String> clientOrdersListView;

    private Stage dialogStage;
    private UserService userService;
    private MedicineOrderService orderService;
    private User editingUser;

    private final ObservableList<User> observableClients = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        clientsTable.setItems(observableClients);

        colId.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getId())));
        colFullName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFullName()));
        colPhone.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPhone()));
        colEmail.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getEmail()));
        colAddress.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDefaultAddress()));
        colRegisteredAt.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getRegisteredAt() != null
                        ? cell.getValue().getRegisteredAt().format(User.FORMATTER)
                        : "—"
        ));

        clientsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                populateClientForm(newSel);
                loadClientOrders(newSel);
            }
        });
    }

    public void initDialog(Stage dialogStage, UserService userService, MedicineOrderService orderService) {
        this.dialogStage = dialogStage;
        this.userService = userService;
        this.orderService = orderService;
        loadClients();
    }

    @FXML
    public void handleSearchClients() {
        try {
            String query = searchClientField.getText();
            List<User> found = userService.searchUsersByName(query);
            observableClients.setAll(found);
        } catch (AppException e) {
            showClientError(e.getMessage());
        }
    }

    @FXML
    public void handleResetClientSearch() {
        searchClientField.clear();
        loadClients();
    }

    @FXML
    public void handleNewClientMode() {
        editingUser = null;
        clientsTable.getSelectionModel().clearSelection();
        formModeLabel.setText("Регистрация нового клиента");
        fullNameField.clear();
        phoneField.clear();
        emailField.clear();
        addressField.clear();
        clientOrdersListView.getItems().clear();
        clearClientError();
        fullNameField.requestFocus();
    }

    @FXML
    public void handleSaveClient() {
        clearClientError();
        String fullName = fullNameField.getText() != null ? fullNameField.getText().trim() : "";
        String phone = phoneField.getText() != null ? phoneField.getText().trim() : "";
        String email = emailField.getText() != null ? emailField.getText().trim() : "";
        String address = addressField.getText() != null ? addressField.getText().trim() : "";

        try {
            if (editingUser == null) {
                User newUser = new User(fullName, phone, email, address);
                User created = userService.registerUser(newUser);
                loadClients();
                clientsTable.getSelectionModel().select(created);
            } else {
                User updated = new User(editingUser.getId(), fullName, phone, email, address, editingUser.getRegisteredAt());
                userService.updateUser(updated);
                loadClients();
                clientsTable.getSelectionModel().select(updated);
            }
        } catch (AppException e) {
            showClientError(e.getMessage());
        } catch (Exception e) {
            showClientError("Ошибка сохранения клиента: " + e.getMessage());
        }
    }

    @FXML
    public void handleDeleteClient() {
        User selected = clientsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtil.showWarning(dialogStage, "Клиент не выбран",
                    "Выберите клиента в таблице для удаления.");
            return;
        }

        boolean confirmed = DialogUtil.showConfirmation(
                dialogStage,
                "Подтверждение удаления клиента",
                "Удалить клиента «" + selected.getFullName() + "»?",
                "Если за клиентом числятся заказы в базе данных, удаление будет отклонено ограничением целостности внешнего ключа."
        );
        if (!confirmed) {
            return;
        }

        try {
            userService.deleteUser(selected.getId());
            handleNewClientMode();
            loadClients();
        } catch (AppException e) {
            DialogUtil.showError(dialogStage, "Удаление клиента отклонено",
                    "Не удалось удалить клиента", e.getMessage());
        }
    }

    @FXML
    public void handleClose() {
        dialogStage.close();
    }

    private void loadClients() {
        List<User> all = userService.getAllUsers();
        observableClients.setAll(all);
        if (!all.isEmpty() && clientsTable.getSelectionModel().getSelectedItem() == null) {
            clientsTable.getSelectionModel().selectFirst();
        }
    }

    private void populateClientForm(User user) {
        this.editingUser = user;
        formModeLabel.setText("Редактирование клиента ID=" + user.getId());
        fullNameField.setText(user.getFullName());
        phoneField.setText(user.getPhone());
        emailField.setText(user.getEmail());
        addressField.setText(user.getDefaultAddress());
        clearClientError();
    }

    private void loadClientOrders(User user) {
        if (orderService == null || user == null) return;
        List<String> items = orderService.getAllOrders().stream()
                .filter(o -> user.getId().equals(o.getClientId()))
                .map(o -> String.format("%s | %.2f ₽ | %s | %s",
                        o.getOrderNumber(), o.getTotalAmount(),
                        o.getStatus().getDisplayName(), o.getCreatedAtFormatted()))
                .collect(Collectors.toList());
        if (items.isEmpty()) {
            items.add("У выбранного клиента пока нет заказов.");
        }
        clientOrdersListView.setItems(FXCollections.observableArrayList(items));
    }

    private void showClientError(String message) {
        clientErrorLabel.setText(message);
        clientErrorLabel.setVisible(true);
        clientErrorLabel.setManaged(true);
    }

    private void clearClientError() {
        clientErrorLabel.setText("");
        clientErrorLabel.setVisible(false);
        clientErrorLabel.setManaged(false);
    }
}
