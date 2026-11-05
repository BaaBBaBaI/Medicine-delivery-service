# Medicine Delivery Service (Заказ доставки лекарств)

Современная информационная система для аптек и служб доставки медикаментов. Сервис предоставляет REST API и веб-интерфейс для оформления заказов, управления базой клиентов, контроля рецептурного отпуска, автоматического расчёта приоритетов доставки и ведения журнала диспетчера.

---

## Основные возможности

- **Управление заказами**:
  - Полный жизненный цикл заказа (`CREATED` → `CONFIRMED` → `ASSEMBLING` → `IN_DELIVERY` → `DELIVERED` / `CANCELLED`).
  - Учёт рецептурных препаратов и валидация рецептов.
  - Автоматический расчёт приоритета доставки (`PriorityCalculator` — от `LOW` до `CRITICAL`).
  - Метки (теги) к заказам (`срочно`, `термолабильный`, `антибиотик` и др.).
  - Служебные заметки фармацевта-диспетчера с поддержкой закрепления.
- **Клиентская база**:
  - Справочник постоянных клиентов (карты лояльности, контакты, адреса по умолчанию).
- **Безопасность и аудит**:
  - Аутентификация на основе JWT с контролем серверных refresh-сессий и ротацией токенов.
  - Ролевой доступ: `VIEWER`, `PHARMACIST`, `ADMIN`.
  - Защита от подбора паролей и соблюдение строгой политики паролей.
  - Подробный журнал аудита действий (`audit_event`) с отслеживанием `X-Correlation-ID`.
- **Аналитика и экспорт данных**:
  - Аналитическая панель (выручка, средний чек, распределение заказов по статусам и приоритетам).
  - Экспорт и импорт заказов в формате CSV с предварительным просмотром (`preview`).

---

## Стек технологий

- **Backend**: Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA
- **Database**: PostgreSQL 17, миграции Flyway (H2 Database для тестов)
- **Frontend**: Встроенный Web UI (HTML5, CSS3, JavaScript)
- **API Документация**: Swagger UI / OpenAPI 3
- **Контейнеризация**: Docker, Docker Compose

---

## Быстрый запуск

### Требования
- Установленный **Docker** и **Docker Compose**.

### 1. Запуск через Docker Compose (рекомендуемый способ)

Выполните команду из корня проекта:

```bash
docker compose up -d --wait
```

Контейнеры с базой данных PostgreSQL 17 и приложением Spring Boot поднимутся автоматически, применятся миграции Flyway и загрузятся начальные демо-данные.

> Также доступны вспомогательные скрипты:
> - **Linux / macOS**: `./backend/start-compose.sh`
> - **Windows (PowerShell)**: `.\backend\start-compose.ps1`

### 2. Остановка сервиса

```bash
docker compose down
```
*(Данные базы данных сохраняются в именованном Docker Volume `medicine_delivery_postgres`)*.

---

## Доступ к сервисам

После успешного запуска доступны следующие адреса:

| Сервис | Адрес | Описание |
|---|---|---|
| **Web UI** | [http://localhost:8080/](http://localhost:8080/) | Веб-интерфейс диспетчера |
| **Swagger UI** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Интерактивная документация REST API |
| **OpenAPI Docs** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | Спецификация OpenAPI в формате JSON |
| **Healthcheck** | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | Проверка работоспособности сервиса |
| **PostgreSQL** | `localhost:55432` | Порт БД для внешних подключений (DBeaver, DataGrip и т.д.) |

### Данные для входа по умолчанию

- **Логин**: `admin@example.org`
- **Временный пароль**: `Adm1!750DF9E52689A0B47E4DE85BC5C8AAC0`
  *(При первом входе система предложит сменить временный пароль на постоянный)*.

Параметры подключения к PostgreSQL:
- **База**: `medicine_delivery`
- **Пользователь**: `medicine_delivery_app`
- **Пароль**: `Db1!D90F176A0758B82E72A92FC9BE3F8652`
- **Порт**: `55432`

---

## Локальная разработка и запуск тестов

### Локальный запуск (без контейнера приложения)

Если требуется разрабатывать бэкенд локально:
1. Запустите только базу данных:
   ```bash
   docker compose up -d db
   ```
2. Запустите Spring Boot приложение:
   ```bash
   cd backend
   mvn spring-boot:run
   ```

### Запуск тестов

Тесты используют легковесную in-memory базу H2 и не требуют запущенного PostgreSQL:

```bash
cd backend
mvn clean test
```

В проект включены:
- `ContextSmokeTest` — проверка корректности поднятия Spring контекста.
- `PriorityCalculatorTest` — проверка расчёта приоритетов доставки.
- `SmartFilterParserTest` — тестирование языка поисковых фильтров.
- `PasswordPolicyTest` — валидация правил надежности паролей.

---

## Обзор REST API

| Группа | Метод и путь | Описание | Доступ |
|---|---|---|---|
| **Аутентификация** | `POST /api/auth/login` | Вход и получение JWT-токенов | Публичный |
| | `POST /api/auth/refresh` | Обновление токена доступа | Публичный |
| | `POST /api/auth/logout` | Завершение сессии | Публичный |
| | `GET /api/auth/me` | Данные текущего пользователя | Авторизован |
| | `POST /api/auth/change-password` | Смена пароля | Авторизован |
| **Заказы** | `GET /api/orders` | Список заказов с фильтрацией и пагинацией | `VIEWER`, `PHARMACIST`, `ADMIN` |
| | `GET /api/orders/{id}` | Детальная карточка заказа | `VIEWER`, `PHARMACIST`, `ADMIN` |
| | `POST /api/orders` | Создание нового заказа | `PHARMACIST`, `ADMIN` |
| | `PUT /api/orders/{id}` | Редактирование заказа (с контролем версий) | `PHARMACIST`, `ADMIN` |
| | `DELETE /api/orders/{id}` | Архивирование заказа | `PHARMACIST`, `ADMIN` |
| | `POST /api/orders/{id}/restore` | Восстановление заказа из архива | `PHARMACIST`, `ADMIN` |
| | `DELETE /api/orders/{id}/permanent` | Окончательное удаление заказа | `ADMIN` |
| | `POST /api/orders/bulk` | Массовые операции над заказами | `PHARMACIST`, `ADMIN` |
| **Заметки** | `GET/POST /api/orders/{id}/notes` | Просмотр и добавление заметок к заказу | `PHARMACIST`, `ADMIN` |
| **Импорт / Экспорт** | `POST /api/orders/import/apply` | Импорт заказов из CSV | `PHARMACIST`, `ADMIN` |
| | `GET /api/orders/export` | Экспорт заказов в CSV | `PHARMACIST`, `ADMIN` |
| **Клиенты** | `GET /api/customers` | Справочник клиентов | `VIEWER`, `PHARMACIST`, `ADMIN` |
| | `POST/PUT /api/customers` | Добавление / редактирование клиента | `ADMIN` |
| **Аналитика** | `GET /api/analytics/summary` | Сводная аналитика и ключевые показатели | `VIEWER`, `PHARMACIST`, `ADMIN` |
| **Администрирование** | `GET/POST /api/admin/users` | Управление учётными записями | `ADMIN` |
| | `GET /api/admin/audit` | Просмотр системного журнала аудита | `ADMIN` |

---

## Структура проекта

```text
.
├── backend/
│   ├── src/main/java/ru/mirea/medicinedelivery/
│   │   ├── config/        # Конфигурация Spring Security, OpenAPI, CORS
│   │   ├── controller/    # REST-контроллеры API
│   │   ├── domain/        # Сущности базы данных (JPA Entities)
│   │   ├── dto/           # Модели запросов и ответов (DTO) с валидацией
│   │   ├── repository/    # Репозитории Spring Data JPA
│   │   ├── security/      # JWT сервис, фильтры и политики паролей
│   │   └── service/       # Бизнес-логика, транзакции, калькулятор приоритета
│   ├── src/main/resources/
│   │   ├── db/migration/  # Миграции базы данных Flyway
│   │   └── static/        # Статические файлы Web UI (HTML, CSS, JS)
│   └── docker-compose.yml # Конфигурация сервисов бэкенда и БД
├── .env.example           # Пример конфигурации переменных окружения
└── docker-compose.yml     # Корневой файл Docker Compose
```
