package ru.mirea.project.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import ru.mirea.project.exception.AppException;
import ru.mirea.project.model.*;
import ru.mirea.project.service.MedicineOrderService;
import ru.mirea.project.service.UserService;
import ru.mirea.project.util.*;

import java.io.File;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Контроллер главного окна приложения (Main View).
 * Координирует отображение таблицы заказов, поиск, фильтрацию, сортировку, CRUD-диалоги,
 * управление клиентами, просмотр статистики и экспорт в .xlsx / .csv.
 */
public class MainController {

    // Поиск, фильтрация и сортировка
    @FXML private TextField searchField;
    @FXML private ComboBox<OrderStatus> statusFilterCombo;
    @FXML private ComboBox<PaymentMethod> paymentFilterCombo;
    @FXML private ComboBox<String> prescriptionFilterCombo;
    @FXML private ComboBox<User> clientFilterCombo;
    @FXML private TextField minAmountField;
    @FXML private TextField maxAmountField;
    @FXML private ComboBox<String> sortCombo;
    @FXML private Label filterErrorLabel;

    // Таблица заказов (основная сущность)
    @FXML private TableView<MedicineOrder> ordersTable;
    @FXML private TableColumn<MedicineOrder, String> colOrderNumber;
    @FXML private TableColumn<MedicineOrder, String> colClientName;
    @FXML private TableColumn<MedicineOrder, String> colPhone;
    @FXML private TableColumn<MedicineOrder, String> colAddress;
    @FXML private TableColumn<MedicineOrder, String> colAmount;
    @FXML private TableColumn<MedicineOrder, String> colStatus;
    @FXML private TableColumn<MedicineOrder, String> colPayment;
    @FXML private TableColumn<MedicineOrder, String> colPrescription;
    @FXML private TableColumn<MedicineOrder, String> colCreatedAt;
    @FXML private TableColumn<MedicineOrder, String> colDeliveredAt;

    // Карточка просмотра выбранного заказа и связанного клиента
    @FXML private Label detailOrderNumberLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailAmountLabel;
    @FXML private Label detailPaymentLabel;
    @FXML private Label detailPrescriptionLabel;
    @FXML private Label detailAddressLabel;
    @FXML private Label detailPhoneLabel;
    @FXML private Label detailCreatedLabel;
    @FXML private Label detailDeliveredLabel;
    @FXML private TextArea detailNotesArea;

    // Сведения о связанной сущности (Клиент)
    @FXML private Label detailClientNameLabel;
    @FXML private Label detailClientPhoneLabel;
    @FXML private Label detailClientEmailLabel;
    @FXML private Label detailClientAddressLabel;

    // Нижняя строка состояния и сводных индикаторов
    @FXML private Label statusBarLabel;
    @FXML private Label dbConnectionBadge;
    @FXML private Label totalShownLabel;

    private MedicineOrderService orderService;
    private UserService userService;
    private DatabaseManager databaseManager;

    private final ObservableList<MedicineOrder> observableOrders = FXCollections.observableArrayList();

    private static final String SORT_DATE_DESC = "По дате создания: сначала новые";
    private static final String SORT_DATE_ASC = "По дате создания: сначала старые";
    private static final String SORT_AMOUNT_ASC = "По сумме заказа: по возрастанию";
    private static final String SORT_AMOUNT_DESC = "По сумме заказа: по убыванию";
    private static final String SORT_STATUS = "По этапу жизненного цикла (статусу)";
    private static final String SORT_CLIENT = "По ФИО клиента (А -> Я)";

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilterAndSortControls();
        setupSelectionListener();
        clearDetailsPane();
    }

    public void initServices(MedicineOrderService orderService, UserService userService, DatabaseManager databaseManager) {
        this.orderService = orderService;
        this.userService = userService;
        this.databaseManager = databaseManager;

        checkDatabaseAndLoadData();
    }

    private void setupTableColumns() {
        ordersTable.setItems(observableOrders);

        colOrderNumber.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getOrderNumber()));

        colClientName.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getClientDisplayName()));

        colPhone.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getContactPhone()));

        colAddress.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDeliveryAddress()));

        colAmount.setCellValueFactory(cell ->
                new SimpleStringProperty(String.format(Locale.US, "%.2f ₽", cell.getValue().getTotalAmount())));

        colStatus.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatus().getDisplayName()));

        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(
                        "status-badge-created", "status-badge-confirmed",
                        "status-badge-assembling", "status-badge-delivery",
                        "status-badge-delivered", "status-badge-cancelled"
                );
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(item);
                    OrderStatus st = getTableRow().getItem().getStatus();
                    if (st != null) {
                        switch (st) {
                            case CREATED -> getStyleClass().add("status-badge-created");
                            case CONFIRMED -> getStyleClass().add("status-badge-confirmed");
                            case ASSEMBLING -> getStyleClass().add("status-badge-assembling");
                            case IN_DELIVERY -> getStyleClass().add("status-badge-delivery");
                            case DELIVERED -> getStyleClass().add("status-badge-delivered");
                            case CANCELLED -> getStyleClass().add("status-badge-cancelled");
                        }
                    }
                }
            }
        });

        colPayment.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getPaymentMethod().getDisplayName()));

        colPrescription.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getPrescriptionDisplay()));

        colCreatedAt.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getCreatedAtFormatted()));

        colDeliveredAt.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDeliveredAtFormatted()));
    }

    private void setupFilterAndSortControls() {
        // Фильтр 1: Статус заказа
        ObservableList<OrderStatus> statuses = FXCollections.observableArrayList();
        statuses.add(null);
        statuses.addAll(OrderStatus.values());
        statusFilterCombo.setItems(statuses);
        statusFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(OrderStatus object) {
                return object == null ? "Все статусы" : object.getDisplayName();
            }

            @Override
            public OrderStatus fromString(String string) {
                return null;
            }
        });

        // Фильтр 2: Способ оплаты
        ObservableList<PaymentMethod> payments = FXCollections.observableArrayList();
        payments.add(null);
        payments.addAll(PaymentMethod.values());
        paymentFilterCombo.setItems(payments);
        paymentFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(PaymentMethod object) {
                return object == null ? "Все способы оплаты" : object.getDisplayName();
            }

            @Override
            public PaymentMethod fromString(String string) {
                return null;
            }
        });

        // Фильтр 3: Рецептурность
        prescriptionFilterCombo.setItems(FXCollections.observableArrayList(
                "Все заказы",
                "Только рецептурные (требуется рецепт)",
                "Без рецепта"
        ));
        prescriptionFilterCombo.getSelectionModel().selectFirst();

        // Фильтр 4: Связанный клиент
        clientFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(User user) {
                return user == null ? "Все клиенты" : (user.getFullName() + " (" + user.getPhone() + ")");
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        });

        // Сортировка (минимум по 2 параметрам, реализовано 6 вариантов)
        sortCombo.setItems(FXCollections.observableArrayList(
                SORT_DATE_DESC,
                SORT_DATE_ASC,
                SORT_AMOUNT_ASC,
                SORT_AMOUNT_DESC,
                SORT_STATUS,
                SORT_CLIENT
        ));
        sortCombo.getSelectionModel().selectFirst();
    }

    private void setupSelectionListener() {
        ordersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                populateDetailsPane(newSel);
            } else {
                clearDetailsPane();
            }
        });

        ordersTable.setRowFactory(tv -> {
            TableRow<MedicineOrder> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    handleEditOrder();
                }
            });
            return row;
        });
    }

    private void checkDatabaseAndLoadData() {
        boolean connected = databaseManager.testConnection();
        if (connected) {
            dbConnectionBadge.setText("БД PostgreSQL: подключено");
            dbConnectionBadge.getStyleClass().removeAll("badge-Offline");
            dbConnectionBadge.getStyleClass().add("badge-Online");
            reloadClientsFilter();
            handleApplySearchAndFilters();
        } else {
            dbConnectionBadge.setText("БД PostgreSQL: нет соединения");
            dbConnectionBadge.getStyleClass().removeAll("badge-Online");
            dbConnectionBadge.getStyleClass().add("badge-Offline");
            statusBarLabel.setText("Не удалось подключиться к PostgreSQL. Проверьте database.properties и запущенный контейнер/сервис БД.");
        }
    }

    private void reloadClientsFilter() {
        try {
            User previous = clientFilterCombo.getValue();
            List<User> users = userService.getAllUsers();
            ObservableList<User> items = FXCollections.observableArrayList();
            items.add(null);
            items.addAll(users);
            clientFilterCombo.setItems(items);
            if (previous != null && users.contains(previous)) {
                clientFilterCombo.setValue(previous);
            } else {
                clientFilterCombo.getSelectionModel().selectFirst();
            }
        } catch (Exception ignored) {
        }
    }

    @FXML
    public void handleApplySearchAndFilters() {
        clearValidationStyles();
        if (orderService == null) return;

        Double minAmount = null;
        Double maxAmount = null;

        String minText = minAmountField.getText() != null ? minAmountField.getText().trim().replace(",", ".") : "";
        String maxText = maxAmountField.getText() != null ? maxAmountField.getText().trim().replace(",", ".") : "";

        if (!minText.isEmpty()) {
            try {
                minAmount = Double.parseDouble(minText);
            } catch (NumberFormatException e) {
                markFieldError(minAmountField, "Минимальная сумма фильтра должна быть числом.");
                return;
            }
        }

        if (!maxText.isEmpty()) {
            try {
                maxAmount = Double.parseDouble(maxText);
            } catch (NumberFormatException e) {
                markFieldError(maxAmountField, "Максимальная сумма фильтра должна быть числом.");
                return;
            }
        }

        Boolean prescriptionRequired = null;
        String rxChoice = prescriptionFilterCombo.getValue();
        if ("Только рецептурные (требуется рецепт)".equals(rxChoice)) {
            prescriptionRequired = Boolean.TRUE;
        } else if ("Без рецепта".equals(rxChoice)) {
            prescriptionRequired = Boolean.FALSE;
        }

        User selectedClient = clientFilterCombo.getValue();
        Long clientId = selectedClient != null ? selectedClient.getId() : null;

        Comparator<MedicineOrder> comparator = resolveSelectedComparator();

        try {
            List<MedicineOrder> filtered = orderService.findOrdersWithFilters(
                    searchField.getText(),
                    statusFilterCombo.getValue(),
                    paymentFilterCombo.getValue(),
                    prescriptionRequired,
                    clientId,
                    minAmount,
                    maxAmount,
                    comparator
            );

            MedicineOrder previouslySelected = ordersTable.getSelectionModel().getSelectedItem();
            observableOrders.setAll(filtered);

            if (previouslySelected != null && observableOrders.contains(previouslySelected)) {
                ordersTable.getSelectionModel().select(previouslySelected);
            } else if (!observableOrders.isEmpty()) {
                ordersTable.getSelectionModel().selectFirst();
            } else {
                clearDetailsPane();
            }

            double sum = filtered.stream().mapToDouble(MedicineOrder::getTotalAmount).sum();
            totalShownLabel.setText(String.format(Locale.US, "Показано заказов: %d | Сумма выборки: %.2f ₽", filtered.size(), sum));
            statusBarLabel.setText("Данные успешно загружены из PostgreSQL.");
        } catch (AppException e) {
            showInlineFilterError(e.getMessage());
        } catch (Exception e) {
            DialogUtil.showError(getWindow(), "Ошибка работы с базой данных",
                    "Не удалось загрузить список заказов", e.getMessage());
        }
    }

    @FXML
    public void handleResetFilters() {
        searchField.clear();
        statusFilterCombo.getSelectionModel().selectFirst();
        paymentFilterCombo.getSelectionModel().selectFirst();
        prescriptionFilterCombo.getSelectionModel().selectFirst();
        clientFilterCombo.getSelectionModel().selectFirst();
        minAmountField.clear();
        maxAmountField.clear();
        sortCombo.getSelectionModel().selectFirst();
        clearValidationStyles();
        handleApplySearchAndFilters();
    }

    @FXML
    public void handleRefreshData() {
        reloadClientsFilter();
        handleApplySearchAndFilters();
        statusBarLabel.setText("Список заказов и клиентов синхронизирован с базой данных.");
    }

    @FXML
    public void handleAddOrder() {
        openOrderFormDialog(null);
    }

    @FXML
    public void handleEditOrder() {
        MedicineOrder selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtil.showWarning(getWindow(), "Заказ не выбран",
                    "Выберите заказ в таблице для редактирования.");
            return;
        }
        if (selected.getStatus().isFinal()) {
            DialogUtil.showError(getWindow(), "Ограничение бизнес-правила",
                    "Редактирование завершенного заказа запрещено",
                    "Заказ " + selected.getOrderNumber() + " находится в финальном статусе «"
                            + selected.getStatus().getDisplayName() + "» и не подлежит изменению.");
            return;
        }
        openOrderFormDialog(selected);
    }

    private void openOrderFormDialog(MedicineOrder orderToEdit) {
        try {
            List<User> clients = userService.getAllUsers();
            if (clients.isEmpty()) {
                DialogUtil.showWarning(getWindow(), "Нет зарегистрированных клиентов",
                        "Перед созданием заказа добавьте хотя бы одного клиента в разделе «Клиенты».");
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ru/mirea/project/view/order-form-dialog.fxml"));
            Parent page = loader.load();

            Stage dialogStage = createModalStage(
                    orderToEdit == null ? "Оформление нового заказа доставки лекарств" : "Редактирование заказа " + orderToEdit.getOrderNumber(),
                    page, 620, 640
            );

            OrderFormController controller = loader.getController();
            controller.initDialog(dialogStage, orderService, clients, orderToEdit);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                handleRefreshData();
                MedicineOrder result = controller.getSavedOrder();
                if (result != null) {
                    ordersTable.getSelectionModel().select(result);
                    ordersTable.scrollTo(result);
                }
                statusBarLabel.setText(orderToEdit == null
                        ? "Создан новый заказ: " + (result != null ? result.getOrderNumber() : "")
                        : "Обновлен заказ: " + (result != null ? result.getOrderNumber() : ""));
            }
        } catch (Exception e) {
            DialogUtil.showError(getWindow(), "Ошибка открытия формы заказа",
                    "Не удалось открыть окно формы заказа", e.getMessage());
        }
    }

    @FXML
    public void handleChangeStatus() {
        MedicineOrder selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtil.showWarning(getWindow(), "Заказ не выбран",
                    "Выберите заказ в таблице для изменения его статуса.");
            return;
        }

        if (selected.getStatus().isFinal()) {
            DialogUtil.showWarning(getWindow(), "Статус заказа финальный",
                    "Заказ " + selected.getOrderNumber() + " уже находится в финальном статусе «"
                            + selected.getStatus().getDisplayName() + "». Переходы из него недоступны.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ru/mirea/project/view/status-dialog.fxml"));
            Parent page = loader.load();

            Stage dialogStage = createModalStage("Смена статуса заказа " + selected.getOrderNumber(), page, 500, 420);

            StatusChangeController controller = loader.getController();
            controller.initDialog(dialogStage, orderService, selected);

            dialogStage.showAndWait();

            if (controller.isChanged()) {
                handleRefreshData();
                statusBarLabel.setText("Статус заказа " + selected.getOrderNumber() + " успешно обновлен.");
            }
        } catch (Exception e) {
            DialogUtil.showError(getWindow(), "Ошибка смены статуса",
                    "Не удалось открыть диалог смены статуса", e.getMessage());
        }
    }

    @FXML
    public void handleDeleteOrder() {
        MedicineOrder selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtil.showWarning(getWindow(), "Заказ не выбран",
                    "Выберите заказ в таблице, который необходимо удалить.");
            return;
        }

        boolean confirmed = DialogUtil.showConfirmation(
                getWindow(),
                "Подтверждение удаления заказа",
                "Удалить заказ № " + selected.getOrderNumber() + "?",
                String.format("Клиент: %s%nСумма: %.2f ₽%nСтатус: %s%n%nВы действительно хотите безвозвратно удалить выбранную запись из базы данных?",
                        selected.getClientDisplayName(),
                        selected.getTotalAmount(),
                        selected.getStatus().getDisplayName())
        );

        if (!confirmed) {
            return;
        }

        try {
            orderService.deleteOrder(selected.getId());
            handleRefreshData();
            statusBarLabel.setText("Заказ " + selected.getOrderNumber() + " успешно удален.");
        } catch (AppException e) {
            DialogUtil.showError(getWindow(), "Удаление отклонено",
                    "Невозможно удалить выбранный заказ", e.getMessage());
        } catch (Exception e) {
            DialogUtil.showError(getWindow(), "Ошибка удаления",
                    "Произошла ошибка при обращении к базе данных", e.getMessage());
        }
    }

    @FXML
    public void handleOpenClientsDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ru/mirea/project/view/clients-dialog.fxml"));
            Parent page = loader.load();

            Stage dialogStage = createModalStage("Справочник клиентов аптечной сети (связанная сущность)", page, 980, 620);

            ClientsController controller = loader.getController();
            controller.initDialog(dialogStage, userService, orderService);

            dialogStage.showAndWait();
            handleRefreshData();
        } catch (Exception e) {
            DialogUtil.showError(getWindow(), "Ошибка открытия справочника клиентов",
                    "Не удалось открыть окно управления клиентами", e.getMessage());
        }
    }

    @FXML
    public void handleOpenStatisticsDialog() {
        try {
            OrderStatistics statistics = orderService.calculateStatistics();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ru/mirea/project/view/statistics-dialog.fxml"));
            Parent page = loader.load();

            Stage dialogStage = createModalStage("Аналитика и статистика заказов доставки лекарств", page, 960, 680);

            StatisticsController controller = loader.getController();
            controller.initDialog(dialogStage, statistics);

            dialogStage.showAndWait();
        } catch (Exception e) {
            DialogUtil.showError(getWindow(), "Ошибка расчета статистики",
                    "Не удалось сформировать аналитический отчет", e.getMessage());
        }
    }

    @FXML
    public void handleExportExcel() {
        exportCurrentOrders(new ExcelExporter(), "Документ Microsoft Excel (*.xlsx)", "*.xlsx", "medicine_orders_export.xlsx");
    }

    @FXML
    public void handleExportCsv() {
        exportCurrentOrders(new CsvExporter(), "Файл CSV (*.csv)", "*.csv", "medicine_orders_export.csv");
    }

    private void exportCurrentOrders(DataExporter exporter, String extensionDescription, String extensionPattern, String initialName) {
        if (observableOrders.isEmpty()) {
            DialogUtil.showWarning(getWindow(), "Нет данных для выгрузки",
                    "Текущий список заказов пуст. Измените параметры фильтрации перед экспортом.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранение выгрузки заказов — " + exporter.getFormatName());
        fileChooser.setInitialFileName(initialName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extensionDescription, extensionPattern));

        File targetFile = fileChooser.showSaveDialog(getWindow());
        if (targetFile == null) {
            return;
        }

        try {
            exporter.exportOrders(List.copyOf(observableOrders), targetFile.getAbsolutePath());
            statusBarLabel.setText("Экспорт выполнен: " + targetFile.getName());
            DialogUtil.showInfo(
                    getWindow(),
                    "Экспорт успешно завершен",
                    "Данные выгружены в формат " + exporter.getFormatName(),
                    String.format("Файл: %s%nКоличество выгруженных записей: %d шт.",
                            targetFile.getAbsolutePath(), observableOrders.size())
            );
        } catch (Exception e) {
            DialogUtil.showError(getWindow(), "Ошибка экспорта данных",
                    "Не удалось сохранить файл выгрузки", e.getMessage());
        }
    }

    @FXML
    public void handleResetDatabaseDemoData() {
        boolean confirmed = DialogUtil.showConfirmation(
                getWindow(),
                "Инициализация / сброс демонстрационных данных БД",
                "Пересоздать таблицы и загрузить контрольные данные из КР №1?",
                "Будут выполнены скрипты schema.sql и data.sql (6 клиентов и 12 заказов доставки лекарств). Текущие несохраненные изменения в таблицах будут перезаписаны."
        );
        if (!confirmed) {
            return;
        }

        try {
            databaseManager.initializeDatabaseWithDemoData();
            checkDatabaseAndLoadData();
            DialogUtil.showInfo(getWindow(), "База данных инициализирована",
                    "Таблицы и тестовые данные успешно загружены",
                    "Загружено 6 клиентов и 12 заказов доставки лекарств из КР №1.");
        } catch (Exception e) {
            DialogUtil.showError(getWindow(), "Ошибка инициализации БД",
                    "Не удалось выполнить SQL-скрипты инициализации", e.getMessage());
        }
    }

    private Comparator<MedicineOrder> resolveSelectedComparator() {
        String selected = sortCombo.getValue();
        if (SORT_DATE_ASC.equals(selected)) {
            return Comparator.comparing(MedicineOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if (SORT_AMOUNT_ASC.equals(selected)) {
            return Comparator.comparingDouble(MedicineOrder::getTotalAmount);
        } else if (SORT_AMOUNT_DESC.equals(selected)) {
            return Comparator.comparingDouble(MedicineOrder::getTotalAmount).reversed();
        } else if (SORT_STATUS.equals(selected)) {
            return Comparator.comparingInt(o -> o.getStatus().ordinal());
        } else if (SORT_CLIENT.equals(selected)) {
            return Comparator.comparing(MedicineOrder::getClientDisplayName, String.CASE_INSENSITIVE_ORDER);
        }
        return Comparator.comparing(MedicineOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private void populateDetailsPane(MedicineOrder order) {
        detailOrderNumberLabel.setText(order.getOrderNumber() + " (ID: " + order.getId() + ")");
        detailStatusLabel.setText(order.getStatus().getDisplayName() + " — " + order.getStatus().getDescription());
        detailAmountLabel.setText(String.format(Locale.US, "%.2f ₽", order.getTotalAmount()));
        detailPaymentLabel.setText(order.getPaymentMethod().getDisplayName());
        detailPrescriptionLabel.setText(order.getPrescriptionDisplay());
        detailAddressLabel.setText(order.getDeliveryAddress());
        detailPhoneLabel.setText(order.getContactPhone());
        detailCreatedLabel.setText(order.getCreatedAtFormatted());
        detailDeliveredLabel.setText(order.getDeliveredAt() != null
                ? order.getDeliveredAtFormatted()
                : (order.getStatus() == OrderStatus.CANCELLED ? "Заказ отменен" : "В процессе выполнения"));
        detailNotesArea.setText(order.getDeliveryNotes() != null && !order.getDeliveryNotes().isBlank()
                ? order.getDeliveryNotes()
                : "Примечания к заказу отсутствуют.");

        User client = order.getClient();
        if (client != null) {
            detailClientNameLabel.setText(client.getFullName() + " (ID: " + client.getId() + ")");
            detailClientPhoneLabel.setText(client.getPhone());
            detailClientEmailLabel.setText(client.getEmail());
            detailClientAddressLabel.setText(client.getDefaultAddress());
        } else {
            detailClientNameLabel.setText("Клиент #" + order.getClientId());
            detailClientPhoneLabel.setText("—");
            detailClientEmailLabel.setText("—");
            detailClientAddressLabel.setText("—");
        }
    }

    private void clearDetailsPane() {
        detailOrderNumberLabel.setText("Заказ не выбран");
        detailStatusLabel.setText("—");
        detailAmountLabel.setText("—");
        detailPaymentLabel.setText("—");
        detailPrescriptionLabel.setText("—");
        detailAddressLabel.setText("—");
        detailPhoneLabel.setText("—");
        detailCreatedLabel.setText("—");
        detailDeliveredLabel.setText("—");
        detailNotesArea.setText("Выберите строку в таблице заказов слева, чтобы просмотреть подробную карточку заказа и данные клиента.");

        detailClientNameLabel.setText("—");
        detailClientPhoneLabel.setText("—");
        detailClientEmailLabel.setText("—");
        detailClientAddressLabel.setText("—");
    }

    private void markFieldError(Control field, String message) {
        if (!field.getStyleClass().contains("input-error")) {
            field.getStyleClass().add("input-error");
        }
        showInlineFilterError(message);
    }

    private void showInlineFilterError(String message) {
        filterErrorLabel.setText(message);
        filterErrorLabel.setVisible(true);
        filterErrorLabel.setManaged(true);
    }

    private void clearValidationStyles() {
        minAmountField.getStyleClass().remove("input-error");
        maxAmountField.getStyleClass().remove("input-error");
        filterErrorLabel.setText("");
        filterErrorLabel.setVisible(false);
        filterErrorLabel.setManaged(false);
    }

    private Stage createModalStage(String title, Parent root, double width, double height) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        if (getWindow() != null) {
            stage.initOwner(getWindow());
        }
        Scene scene = new Scene(root, width, height);
        URL cssUrl = getClass().getResource("/ru/mirea/project/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);
        return stage;
    }

    private Window getWindow() {
        return ordersTable != null && ordersTable.getScene() != null
                ? ordersTable.getScene().getWindow()
                : null;
    }
}
