# Информационная система «Заказ доставки лекарств» — Контрольная работа №2 (JavaFX)

Настольное графическое приложение на **JavaFX 21** с прямым **JDBC-подключением к PostgreSQL**, разработанное на основе **Контрольной работы №1** (авторы: Веневцев А.А., Николаев Е.А., Кальченко Е.В., группа ЭФБО-06-24).

## 1. Архитектура и слои приложения

Проект разделён по уровням ответственности в соответствии с требованиями методички КР №2:

- **`ru.mirea.project.view` (FXML)** и **`ru.mirea.project.css` (CSS)**:
  - `main-view.fxml` — главное окно диспетчера-фармацевта с таблицей заказов (`TableView`), панелью поиска/фильтрации/сортировки и карточкой детального просмотра заказа и связанного клиента.
  - `order-form-dialog.fxml` — модальное окно добавления и редактирования заказа с выбором клиента через `ComboBox<User>` и валидацией ввода.
  - `status-dialog.fxml` — модальное окно управления жизненным циклом заказа (`OrderStatus.canTransitionTo`).
  - `clients-dialog.fxml` — модальное окно справочника связанных сущностей (`User` — клиенты аптечной сети) и истории их заказов.
  - `statistics-dialog.fxml` — модальное окно аналитики (10 показателей, сводные таблицы, диаграммы `PieChart` и `BarChart`).
  - `styles.css` — визуальное оформление кнопок, таблиц, карточек, статусных бейджей и состояний ошибок (`.input-error`, `.error-banner`).
- **`ru.mirea.project.controller` (Controller)**:
  - `MainController`, `OrderFormController`, `StatusChangeController`, `ClientsController`, `StatisticsController`.
- **`ru.mirea.project.service` (Service)**:
  - `MedicineOrderService`, `MedicineOrderServiceImpl`, `UserService`, `UserServiceImpl` — бизнес-логика, проверка переходов статусов, проверка ограничений рецептурного отпуска, комбинированная фильтрация, сортировка и расчёт статистики.
- **`ru.mirea.project.repository` (Repository / DAO)**:
  - `CrudRepository`, `MedicineOrderRepository`, `JdbcMedicineOrderRepository`, `UserRepository`, `JdbcUserRepository` — параметризованные SQL-запросы (`PreparedStatement`) с `JOIN users ON medicine_orders.client_id = users.id`.
- **`ru.mirea.project.model` (Model)**:
  - `MedicineOrder` (основная сущность), `User` (связанная сущность), `OrderStatus`, `PaymentMethod`, `OrderStatistics`.
- **`ru.mirea.project.util` (Util)**:
  - `DatabaseManager` (чтение настроек из `src/main/resources/database.properties`, автосоздание БД `pharmacy_db`, инициализация `schema.sql` и `data.sql`), `ExcelExporter` (`.xlsx`), `CsvExporter` (`.csv`), `DialogUtil`.

## 2. Настройка подключения к PostgreSQL

Параметры подключения вынесены в `src/main/resources/database.properties`:

```properties
db.driver=org.postgresql.Driver
db.url=jdbc:postgresql://localhost:5432/pharmacy_db
db.user=postgres
db.password=fghtkm-2006
```

> Если PostgreSQL запущен в Docker на нестандартном порту (например, `55432`), укажите фактический порт в `database.properties` или через системное свойство `-Ddb.url=jdbc:postgresql://localhost:55432/pharmacy_db`.
> При первом запуске можно нажать кнопку **«Инициализация БД»** в верхней панели приложения — программа автоматически выполнит `schema.sql` и `data.sql`.

## 3. Команды сборки и запуска Maven

```bash
docker compose up -d

mvn clean compile

mvn test

mvn javafx:run
```
