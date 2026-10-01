package ru.mirea.project.ui;

import ru.mirea.project.exception.BusinessException;
import ru.mirea.project.exception.EntityNotFoundException;
import ru.mirea.project.model.*;
import ru.mirea.project.service.MedicineOrderService;
import ru.mirea.project.service.UserService;
import ru.mirea.project.util.CsvExporter;
import ru.mirea.project.util.DataExporter;
import ru.mirea.project.util.DatabaseManager;
import ru.mirea.project.util.ExcelExporter;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

public class ConsoleMenu {

    private final UserService userService;
    private final MedicineOrderService orderService;
    private final Scanner scanner;

    public ConsoleMenu(UserService userService, MedicineOrderService orderService) {
        this.userService = userService;
        this.orderService = orderService;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        boolean running = true;
        System.out.println("===============================================================================");
        System.out.println("   ИНФОРМАЦИОННАЯ СИСТЕМА «ЗАКАЗ ДОСТАВКИ ЛЕКАРСТВ» (РТУ МИРЭА, КР 1)");
        System.out.println("   Авторы: Веневцев А.А., Николаев Е.А., Кальченко Е.В. (ЭФБО-06-24)");
        System.out.println("===============================================================================");

        while (running) {
            printMainMenu();
            int choice = readInt("Выберите действие: ", 0, 9);
            System.out.println();

            try {
                switch (choice) {
                    case 1:
                        handleClientsMenu();
                        break;
                    case 2:
                        handleOrdersMenu();
                        break;
                    case 3:
                        handleSearchMenu();
                        break;
                    case 4:
                        handleFilterMenu();
                        break;
                    case 5:
                        handleSortMenu();
                        break;
                    case 6:
                        handleStatistics();
                        break;
                    case 7:
                        handleExportMenu();
                        break;
                    case 8:
                        handleViewDatabaseTables();
                        break;
                    case 9:
                        handleResetDatabase();
                        break;
                    case 0:
                        running = false;
                        System.out.println("Завершение работы информационной системы. До свидания!");
                        break;
                    default:
                        System.out.println("Неизвестный пункт меню.");
                }
            } catch (BusinessException e) {
                System.out.println("⚠️  ОШИБКА БИЗНЕС-ПРАВИЛА: " + e.getMessage());
            } catch (EntityNotFoundException e) {
                System.out.println("⚠️  ОШИБКА ПОИСКА: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("❌  ОШИБКА: " + e.getMessage());
            }
            System.out.println();
        }
    }

    private void printMainMenu() {
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("                           ГЛАВНОЕ МЕНЮ");
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println(" 1. Клиенты (пользователи аптечной сети)");
        System.out.println(" 2. Заказы доставки лекарств (CRUD, смена статусов)");
        System.out.println(" 3. Поиск заказов (по ключевым словам, клиенту, телефону)");
        System.out.println(" 4. Фильтрация заказов (по статусу, рецептурности, сумме)");
        System.out.println(" 5. Сортировка заказов (Stream API & Collections)");
        System.out.println(" 6. Аналитика и статистика системы");
        System.out.println(" 7. Экспорт данных (Excel .xlsx / CSV .csv)");
        System.out.println(" 8. Вывести таблицы базы данных (структура и записи)");
        System.out.println(" 9. Сброс / Инициализация тестовых данных БД");
        System.out.println(" 0. Выход");
        System.out.println("-------------------------------------------------------------------------------");
    }

 
    private void handleClientsMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("--- УПРАВЛЕНИЕ КЛИЕНТАМИ ---");
            System.out.println("1. Список всех клиентов");
            System.out.println("2. Найти клиента по ID");
            System.out.println("3. Поиск клиентов по ФИО");
            System.out.println("4. Зарегистрировать нового клиента");
            System.out.println("0. Назад в главное меню");
            int subChoice = readInt("Выберите действие: ", 0, 4);
            System.out.println();

            switch (subChoice) {
                case 1:
                    List<User> clients = userService.getAllUsers();
                    printClientsTable(clients);
                    break;
                case 2:
                    long id = readLong("Введите ID клиента: ");
                    User user = userService.getUserById(id);
                    printSingleClient(user);
                    break;
                case 3:
                    String query = readString("Введите часть ФИО клиента: ", true);
                    List<User> found = userService.searchUsersByName(query);
                    printClientsTable(found);
                    break;
                case 4:
                    createNewClient();
                    break;
                case 0:
                    back = true;
                    break;
            }
            System.out.println();
        }
    }

    private void createNewClient() {
        System.out.println("Регистрация нового клиента:");
        String name = readString("ФИО клиента: ", true);
        String phone = readString("Телефон (например, +7 (999) 000-00-00): ", true);
        String email = readString("Email: ", true);
        String address = readString("Адрес по умолчанию: ", true);

        User user = new User(name, phone, email, address);
        User saved = userService.registerUser(user);
        System.out.println("✅ Клиент успешно зарегистрирован! ID = " + saved.getId());
    }

    private void handleOrdersMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("--- УПРАВЛЕНИЕ ЗАКАЗАМИ ДОСТАВКИ ЛЕКАРСТВ ---");
            System.out.println("1. Список всех заказов");
            System.out.println("2. Просмотр заказа по ID");
            System.out.println("3. Просмотр заказа по номеру");
            System.out.println("4. Создать новый заказ доставки");
            System.out.println("5. Изменить заказ (адрес, телефон, состав, сумма)");
            System.out.println("6. Изменить статус заказа (жизненный цикл)");
            System.out.println("7. Удалить заказ");
            System.out.println("0. Назад в главное меню");
            int subChoice = readInt("Выберите действие: ", 0, 7);
            System.out.println();

            switch (subChoice) {
                case 1:
                    printOrdersTable(orderService.getAllOrders());
                    break;
                case 2:
                    long id = readLong("Введите ID заказа: ");
                    printSingleOrder(orderService.getOrderById(id));
                    break;
                case 3:
                    String num = readString("Введите номер заказа (например, MED-2026-001): ", true);
                    printSingleOrder(orderService.getOrderByNumber(num));
                    break;
                case 4:
                    createNewOrder();
                    break;
                case 5:
                    editOrder();
                    break;
                case 6:
                    changeOrderStatus();
                    break;
                case 7:
                    deleteOrder();
                    break;
                case 0:
                    back = true;
                    break;
            }
            System.out.println();
        }
    }

    private void createNewOrder() {
        System.out.println("--- ОФОРМЛЕНИЕ НОВОГО ЗАКАЗА ЛЕКАРСТВ ---");
        long clientId = readLong("Введите ID клиента-заказчика: ");
        User client = userService.getUserById(clientId);
        System.out.println("Выбран клиент: " + client.getFullName());

        String useDefaultAddr = readString("Использовать адрес клиента по умолчанию (" + client.getDefaultAddress() + ")? (y/n): ", false);
        String address = ("y".equalsIgnoreCase(useDefaultAddr) || "yes".equalsIgnoreCase(useDefaultAddr) || useDefaultAddr.isEmpty())
                ? client.getDefaultAddress()
                : readString("Введите адрес доставки: ", true);

        String useDefaultPhone = readString("Использовать телефон клиента (" + client.getPhone() + ")? (y/n): ", false);
        String phone = ("y".equalsIgnoreCase(useDefaultPhone) || "yes".equalsIgnoreCase(useDefaultPhone) || useDefaultPhone.isEmpty())
                ? client.getPhone()
                : readString("Введите контактный телефон: ", true);

        double amount = readDouble("Сумма заказа в рублях (0.01 - 100000): ", 0.01, 100000.0);

        System.out.println("Способы оплаты:");
        PaymentMethod[] methods = PaymentMethod.values();
        for (int i = 0; i < methods.length; i++) {
            System.out.println((i + 1) + ". " + methods[i].getDisplayName());
        }
        int payChoice = readInt("Выберите способ оплаты: ", 1, methods.length);
        PaymentMethod paymentMethod = methods[payChoice - 1];

        String prescrAnswer = readString("Требуются ли рецептурные препараты? (y/n): ", false);
        boolean isPrescription = "y".equalsIgnoreCase(prescrAnswer) || "yes".equalsIgnoreCase(prescrAnswer);
        String prescrNum = null;
        if (isPrescription) {
            prescrNum = readString("Введите номер рецепта (например, RX-77-90214): ", true);
        }

        String notes = readString("Состав заказа / Примечание для курьера: ", false);

        MedicineOrder order = new MedicineOrder();
        order.setClientId(client.getId());
        order.setDeliveryAddress(address);
        order.setContactPhone(phone);
        order.setTotalAmount(amount);
        order.setPaymentMethod(paymentMethod);
        order.setPrescriptionRequired(isPrescription);
        order.setPrescriptionNumber(prescrNum);
        order.setDeliveryNotes(notes);

        MedicineOrder saved = orderService.createOrder(order);
        System.out.println("✅ Заказ успешно создан!");
        System.out.println("   ID: " + saved.getId() + " | Номер: " + saved.getOrderNumber() + " | Статус: " + saved.getStatus().getDisplayName());
    }

    private void editOrder() {
        long id = readLong("Введите ID заказа для редактирования: ");
        MedicineOrder order = orderService.getOrderById(id);

        System.out.println("Текущие данные заказа #" + order.getOrderNumber() + ":");
        System.out.println("Адрес: " + order.getDeliveryAddress());
        System.out.println("Телефон: " + order.getContactPhone());
        System.out.println("Сумма: " + order.getTotalAmount());
        System.out.println("Примечание: " + order.getDeliveryNotes());

        System.out.println("\nВведите новые значения (нажмите Enter, чтобы оставить текущее):");
        String newAddr = readString("Новый адрес доставки [" + order.getDeliveryAddress() + "]: ", false);
        if (!newAddr.isEmpty()) order.setDeliveryAddress(newAddr);

        String newPhone = readString("Новый телефон [" + order.getContactPhone() + "]: ", false);
        if (!newPhone.isEmpty()) order.setContactPhone(newPhone);

        String amountStr = readString("Новая сумма [" + order.getTotalAmount() + "]: ", false);
        if (!amountStr.isEmpty()) {
            try {
                double newAmt = Double.parseDouble(amountStr);
                order.setTotalAmount(newAmt);
            } catch (NumberFormatException e) {
                System.out.println("Некорректный формат суммы, оставлено прежнее значение.");
            }
        }

        String newNotes = readString("Новое примечание [" + order.getDeliveryNotes() + "]: ", false);
        if (!newNotes.isEmpty()) order.setDeliveryNotes(newNotes);

        MedicineOrder updated = orderService.updateOrder(order);
        System.out.println("✅ Заказ успешно обновлен!");
    }

    private void changeOrderStatus() {
        long id = readLong("Введите ID заказа для смены статуса: ");
        MedicineOrder order = orderService.getOrderById(id);

        System.out.println("Текущий статус заказа #" + order.getOrderNumber() + ": " + order.getStatus().getDisplayName());
        System.out.println("Доступные статусы:");
        OrderStatus[] statuses = OrderStatus.values();
        for (int i = 0; i < statuses.length; i++) {
            boolean allowed = order.getStatus().canTransitionTo(statuses[i]);
            System.out.printf(" %d. %s %s%n", (i + 1), statuses[i].getDisplayName(), allowed ? "(Допустимо)" : "(Запрещено)");
        }

        int choice = readInt("Выберите новый статус: ", 1, statuses.length);
        OrderStatus newStatus = statuses[choice - 1];

        MedicineOrder updated = orderService.changeOrderStatus(id, newStatus);
        System.out.println("✅ Статус заказа успешно изменен на: " + updated.getStatus().getDisplayName());
    }

    private void deleteOrder() {
        long id = readLong("Введите ID заказа для удаления: ");
        MedicineOrder order = orderService.getOrderById(id);
        String confirm = readString("Вы действительно хотите удалить заказ " + order.getOrderNumber() + "? (y/n): ", true);
        if ("y".equalsIgnoreCase(confirm) || "yes".equalsIgnoreCase(confirm)) {
            orderService.deleteOrder(id);
            System.out.println("✅ Заказ ID=" + id + " успешно удален.");
        } else {
            System.out.println("Удаление отменено.");
        }
    }

    private void handleSearchMenu() {
        System.out.println("--- ПОИСК ЗАКАЗОВ ---");
        System.out.println("1. Поиск по ключевому слову (номер, адрес, состав/примечание)");
        System.out.println("2. Поиск по ФИО клиента");
        System.out.println("3. Поиск по контактному номеру телефона");
        System.out.println("0. Назад");
        int choice = readInt("Выберите способ поиска: ", 0, 3);
        System.out.println();

        switch (choice) {
            case 1:
                String kw = readString("Введите ключевое слово: ", true);
                List<MedicineOrder> byKw = orderService.searchByKeyword(kw);
                printOrdersTable(byKw);
                break;
            case 2:
                String name = readString("Введите ФИО или часть имени клиента: ", true);
                List<MedicineOrder> byName = orderService.searchByClientName(name);
                printOrdersTable(byName);
                break;
            case 3:
                String phone = readString("Введите номер телефона: ", true);
                List<MedicineOrder> byPhone = orderService.searchByPhone(phone);
                printOrdersTable(byPhone);
                break;
            case 0:
                break;
        }
    }

    private void handleFilterMenu() {
        System.out.println("--- ФИЛЬТРАЦИЯ ЗАКАЗОВ ---");
        System.out.println("1. По статусу заказа");
        System.out.println("2. По требованию рецепта (только рецептурные / без рецепта)");
        System.out.println("3. По диапазону суммы заказа (мин - макс)");
        System.out.println("4. По способу оплаты");
        System.out.println("0. Назад");
        int choice = readInt("Выберите фильтр: ", 0, 4);
        System.out.println();

        switch (choice) {
            case 1:
                OrderStatus[] statuses = OrderStatus.values();
                for (int i = 0; i < statuses.length; i++) {
                    System.out.println((i + 1) + ". " + statuses[i].getDisplayName());
                }
                int stChoice = readInt("Выберите статус: ", 1, statuses.length);
                printOrdersTable(orderService.filterByStatus(statuses[stChoice - 1]));
                break;
            case 2:
                System.out.println("1. Только рецептурные лекарства (требуется рецепт)");
                System.out.println("2. Только безрецептурные препараты");
                int rxChoice = readInt("Выберите вариант: ", 1, 2);
                printOrdersTable(orderService.filterByPrescription(rxChoice == 1));
                break;
            case 3:
                double min = readDouble("Минимальная сумма: ", 0, 1000000);
                double max = readDouble("Максимальная сумма: ", min, 1000000);
                printOrdersTable(orderService.filterByAmountRange(min, max));
                break;
            case 4:
                PaymentMethod[] methods = PaymentMethod.values();
                for (int i = 0; i < methods.length; i++) {
                    System.out.println((i + 1) + ". " + methods[i].getDisplayName());
                }
                int payChoice = readInt("Выберите способ оплаты: ", 1, methods.length);
                printOrdersTable(orderService.filterByPaymentMethod(methods[payChoice - 1]));
                break;
            case 0:
                break;
        }
    }


    private void handleSortMenu() {
        System.out.println("--- СОРТИРОВКА ЗАКАЗОВ (Java Collections & Stream API) ---");
        System.out.println("1. По дате оформления: сначала новые (сверху вниз)");
        System.out.println("2. По дате оформления: сначала старые");
        System.out.println("3. По сумме заказа: по возрастанию (от дешевых к дорогим)");
        System.out.println("4. По сумме заказа: по убыванию (от дорогих к дешевым)");
        System.out.println("5. По статусу заказа");
        System.out.println("0. Назад");
        int choice = readInt("Выберите способ сортировки: ", 0, 5);
        System.out.println();

        Comparator<MedicineOrder> comp = null;
        switch (choice) {
            case 1:
                comp = Comparator.comparing(MedicineOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
                break;
            case 2:
                comp = Comparator.comparing(MedicineOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case 3:
                comp = Comparator.comparingDouble(MedicineOrder::getTotalAmount);
                break;
            case 4:
                comp = Comparator.comparingDouble(MedicineOrder::getTotalAmount).reversed();
                break;
            case 5:
                comp = Comparator.comparing(o -> o.getStatus().ordinal());
                break;
            case 0:
                return;
        }

        if (comp != null) {
            List<MedicineOrder> sorted = orderService.getSortedOrders(comp);
            printOrdersTable(sorted);
        }
    }

    private void handleStatistics() {
        OrderStatistics stats = orderService.calculateStatistics();
        System.out.println("===============================================================================");
        System.out.println("                     АНАЛИТИКА И СТАТИСТИКА СИСТЕМЫ");
        System.out.println("===============================================================================");
        System.out.printf(" 1. Зарегистрировано клиентов в аптечной сети:    %d чел.%n", stats.getTotalClients());
        System.out.printf(" 2. Всего оформлено заказов доставки:              %d шт.%n", stats.getTotalOrders());
        System.out.printf(" 3. Активных заказов в обработке/доставке:         %d шт.%n", stats.getActiveOrdersCount());
        System.out.printf(" 4. Успешно доставленных заказов (выполненных):     %d шт.%n", stats.getDeliveredOrdersCount());
        System.out.printf(" 5. Отмененных заказов:                            %d шт.%n", stats.getCancelledOrdersCount());
        System.out.printf(" 6. Выручка по доставленным заказам:               %.2f руб.%n", stats.getTotalDeliveredRevenue());
        System.out.printf(" 7. Средний чек заказа:                            %.2f руб.%n", stats.getAverageOrderAmount());
        System.out.printf(" 8. Заказов с рецептурными препаратами:            %d шт. (%.1f%% от общего числа)%n",
                stats.getPrescriptionOrdersCount(), stats.getPrescriptionPercentage());
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("Распределение заказов по статусам:");
        for (OrderStatus st : OrderStatus.values()) {
            long count = stats.getOrdersByStatus().getOrDefault(st, 0L);
            System.out.printf("   - %-25s: %d шт.%n", st.getDisplayName(), count);
        }
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("Распределение по способам оплаты:");
        for (PaymentMethod pm : PaymentMethod.values()) {
            long count = stats.getOrdersByPaymentMethod().getOrDefault(pm, 0L);
            System.out.printf("   - %-32s: %d шт.%n", pm.getDisplayName(), count);
        }
        System.out.println("===============================================================================");
    }

    private void handleExportMenu() {
        System.out.println("--- ЭКСПОРТ ДАННЫХ ИЗ СИСТЕМЫ ---");
        System.out.println("1. Экспорт в Microsoft Excel (.xlsx)");
        System.out.println("2. Экспорт в CSV (.csv)");
        System.out.println("0. Назад");
        int choice = readInt("Выберите формат экспорта: ", 0, 2);
        System.out.println();

        if (choice == 0) return;

        List<MedicineOrder> orders = orderService.getAllOrders();
        if (orders.isEmpty()) {
            System.out.println("⚠️  Нет заказов для экспорта.");
            return;
        }

        DataExporter exporter;
        String defaultFilename;
        if (choice == 1) {
            exporter = new ExcelExporter();
            defaultFilename = "exports/medicine_orders_export.xlsx";
        } else {
            exporter = new CsvExporter();
            defaultFilename = "exports/medicine_orders_export.csv";
        }

        String filename = readString("Путь к файлу [" + defaultFilename + "]: ", false);
        if (filename.isEmpty()) {
            filename = defaultFilename;
        }

        // create папку, если не существует
        File file = new File(filename);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try {
            exporter.exportOrders(orders, filename);
            System.out.println("✅ Данные успешно экспортированы в формат " + exporter.getFormatName() + "!");
            System.out.println("   Файл: " + file.getAbsolutePath());
            System.out.println("   Количество выгруженных записей: " + orders.size());
        } catch (Exception e) {
            System.out.println("❌ Ошибка при экспорте данных: " + e.getMessage());
        }
    }


    private void handleViewDatabaseTables() {
        System.out.println("===============================================================================");
        System.out.println("                 ТАБЛИЦЫ БАЗЫ ДАННЫХ (PostgreSQL)");
        System.out.println("===============================================================================");
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("СУБД: " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());
            System.out.println("URL:  " + meta.getURL());
            System.out.println("User: " + meta.getUserName());
            System.out.println();

            // Таблица 1: users
            System.out.println(">>> ТАБЛИЦА: users (Клиенты/пользователи)");
            printClientsTable(userService.getAllUsers());
            System.out.println();

            // Таблица 2: medicine_orders
            System.out.println(">>> ТАБЛИЦА: medicine_orders (Заказы доставки лекарств)");
            printOrdersTable(orderService.getAllOrders());

        } catch (Exception e) {
            System.out.println("❌ Ошибка получения информации о БД: " + e.getMessage());
        }
    }

    private void handleResetDatabase() {
        System.out.println("⚠️  ВНИМАНИЕ! Это действие пересоздаст таблицы и загрузит демонстрационные тестовые данные.");
        String confirm = readString("Продолжить? (y/n): ", true);
        if ("y".equalsIgnoreCase(confirm) || "yes".equalsIgnoreCase(confirm)) {
            try {
                DatabaseManager db = DatabaseManager.getInstance();
                System.out.print("Выполнение schema.sql... ");
                db.executeScript("schema.sql");
                System.out.println("OK");

                System.out.print("Выполнение data.sql... ");
                db.executeScript("data.sql");
                System.out.println("OK");

                System.out.println("✅ База данных успешно инициализирована тестовыми данными!");
            } catch (Exception e) {
                System.out.println("❌ Ошибка инициализации базы данных: " + e.getMessage());
            }
        } else {
            System.out.println("Действие отменено.");
        }
    }



    private int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int val = Integer.parseInt(input);
                if (val >= min && val <= max) {
                    return val;
                }
                System.out.printf("Ошибка: число должно быть в диапазоне от %d до %d.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: ввод должен быть целым числом. Повторите попытку.");
            }
        }
    }

    private long readLong(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                long val = Long.parseLong(input);
                if (val > 0 && val <= 1_000_000_000L) {
                    return val;
                }
                if (val <= 0) {
                    System.out.println("Ошибка: ID должен быть строго положительным числом.");
                } else {
                    System.out.println("Ошибка: ID не может превышать 1 000 000 000. Повторите попытку.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: ID должен быть положительным целым числом (макс. 1 000 000 000). Повторите попытку.");
            }
        }
    }

    private double readDouble(String prompt, double min, double max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim().replace(",", ".");
            try {
                double val = Double.parseDouble(input);
                if (val >= min && val <= max) {
                    return val;
                }
                System.out.printf("Ошибка: число должно быть в диапазоне от %.2f до %.2f.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите корректное число с плавающей точкой.");
            }
        }
    }

    private String readString(String prompt, boolean required) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!required || !input.isEmpty()) {
                return input;
            }
            System.out.println("Ошибка: значение не может быть пустым. Повторите ввод.");
        }
    }

    private void printClientsTable(List<User> clients) {
        if (clients.isEmpty()) {
            System.out.println("Список клиентов пуст.");
            return;
        }
        System.out.println("+----+-----------------------------+---------------------+----------------------------+--------------------------------------------+");
        System.out.println("| ID | ФИО клиента                 | Телефон             | Email                      | Адрес по умолчанию                         |");
        System.out.println("+----+-----------------------------+---------------------+----------------------------+--------------------------------------------+");
        for (User u : clients) {
            System.out.printf("| %-2d | %-27s | %-19s | %-26s | %-42s |%n",
                    u.getId(), truncate(u.getFullName(), 27), u.getPhone(),
                    truncate(u.getEmail(), 26), truncate(u.getDefaultAddress(), 42));
        }
        System.out.println("+----+-----------------------------+---------------------+----------------------------+--------------------------------------------+");
        System.out.println("Всего клиентов: " + clients.size());    
    }

    private void printSingleClient(User u) {
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("Информация о клиенте ID=" + u.getId() + ":");
        System.out.println("  ФИО:                  " + u.getFullName());
        System.out.println("  Контактный телефон:   " + u.getPhone());
        System.out.println("  Email:                " + u.getEmail());
        System.out.println("  Адрес по умолчанию:   " + u.getDefaultAddress());
        System.out.println("  Дата регистрации:     " + (u.getRegisteredAt() != null ? u.getRegisteredAt().format(MedicineOrder.DATE_FORMATTER) : "—"));
        System.out.println("-------------------------------------------------------------------------------");
    }

    private void printOrdersTable(List<MedicineOrder> orders) {
        if (orders.isEmpty()) {
            System.out.println("Список заказов пуст.");
            return;
        }
        System.out.println("+----+---------------+-------------------------+------------+-------------------------+--------+---------------------+");
        System.out.println("| ID | Номер заказа  | Клиент                  | Сумма(руб) | Статус                  | Рецепт | Дата создания       |");
        System.out.println("+----+---------------+-------------------------+------------+-------------------------+--------+---------------------+");
        for (MedicineOrder o : orders) {
            String clientName = o.getClient() != null ? o.getClient().getFullName() : ("ID #" + o.getClientId());
            String rx = o.isPrescriptionRequired() ? "ДА" : "нет";
            String dateStr = o.getCreatedAt() != null ? o.getCreatedAt().format(MedicineOrder.DATE_FORMATTER) : "—";
            System.out.printf("| %-2d | %-13s | %-23s | %10.2f | %-23s | %-6s | %-19s |%n",
                    o.getId(), o.getOrderNumber(), truncate(clientName, 23),
                    o.getTotalAmount(), truncate(o.getStatus().getDisplayName(), 23), rx, dateStr);
        }
        System.out.println("+----+---------------+-------------------------+------------+-------------------------+--------+---------------------+");
        System.out.println("Всего заказов: " + orders.size());
    }

    private void printSingleOrder(MedicineOrder o) {
        System.out.println("===============================================================================");
        System.out.printf("ДЕТАЛИ ЗАКАЗА ДОСТАВКИ: %s (ID: %d)%n", o.getOrderNumber(), o.getId());
        System.out.println("===============================================================================");
        String clientName = o.getClient() != null ? o.getClient().getFullName() : ("Клиент #" + o.getClientId());
        System.out.println("  Клиент:               " + clientName);
        System.out.println("  Адрес доставки:       " + o.getDeliveryAddress());
        System.out.println("  Контактный телефон:   " + o.getContactPhone());
        System.out.printf("  Сумма заказа:         %.2f руб.%n", o.getTotalAmount());
        System.out.println("  Текущий статус:       " + o.getStatus().getDisplayName() + " (" + o.getStatus().getDescription() + ")");
        System.out.println("  Способ оплаты:        " + o.getPaymentMethod().getDisplayName());
        System.out.println("  Требуется рецепт:     " + (o.isPrescriptionRequired() ? ("ДА (Номер рецепта: " + o.getPrescriptionNumber() + ")") : "НЕТ"));
        System.out.println("  Состав / Примечание:  " + (o.getDeliveryNotes() != null ? o.getDeliveryNotes() : "—"));
        System.out.println("  Дата создания:        " + (o.getCreatedAt() != null ? o.getCreatedAt().format(MedicineOrder.DATE_FORMATTER) : "—"));
        System.out.println("  Дата вручения:        " + (o.getDeliveredAt() != null ? o.getDeliveredAt().format(MedicineOrder.DATE_FORMATTER) : "В процессе доставки"));
        System.out.println("===============================================================================");
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 1) + "…";
    }
}
