-- ============================================================================
-- Информационная система «Заказ доставки лекарств» (Medicine Delivery Service)
-- Контрольная работа №3 — Схема базы данных PostgreSQL (Flyway V1)
-- Основана на базе данных КР №1 / КР №2 (таблицы клиентов и заказов доставки лекарств)
-- ============================================================================

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE app_user_role (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID,
    user_agent VARCHAR(500),
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_user
    ON refresh_session(user_id);

CREATE INDEX idx_refresh_expires
    ON refresh_session(expires_at);


-- Справочная сущность: Клиенты аптечной сети (расширяет таблицу users из КР №1/№2)
CREATE TABLE customer (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(254),
    default_address VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_customer_name_not_empty CHECK (length(trim(full_name)) >= 2),
    CONSTRAINT chk_customer_phone_not_empty CHECK (length(trim(phone)) >= 6)
);

CREATE INDEX idx_customer_name
    ON customer(full_name);

CREATE INDEX idx_customer_phone
    ON customer(phone);


-- Основная сущность: Заказы доставки лекарств (расширяет таблицу medicine_orders из КР №1/№2)
CREATE TABLE medicine_order (
    id UUID PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL UNIQUE,
    customer_id UUID REFERENCES customer(id) ON DELETE RESTRICT,
    medication_summary VARCHAR(500) NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    prescription_required BOOLEAN NOT NULL DEFAULT FALSE,
    prescription_number VARCHAR(60),
    delivery_notes VARCHAR(1000),
    priority_level VARCHAR(32) NOT NULL,
    delivered_at TIMESTAMPTZ,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMPTZ,
    archived_by UUID REFERENCES app_user(id),
    created_by UUID REFERENCES app_user(id),
    updated_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_order_total_amount_positive
        CHECK (total_amount > 0.00 AND total_amount <= 1000000.00),

    CONSTRAINT chk_order_prescription
        CHECK (
            prescription_required = FALSE
            OR (prescription_required = TRUE AND prescription_number IS NOT NULL AND length(trim(prescription_number)) > 0)
        )
);

COMMENT ON COLUMN medicine_order.total_amount
    IS 'Итоговая стоимость заказа доставки лекарств в рублях (> 0)';

COMMENT ON COLUMN medicine_order.priority_level
    IS 'Вычисляемый показатель приоритета и фармацевтического контроля доставки';

CREATE INDEX idx_order_number
    ON medicine_order(order_number);

CREATE INDEX idx_order_customer
    ON medicine_order(customer_id);

CREATE INDEX idx_order_status
    ON medicine_order(status);

CREATE INDEX idx_order_archived
    ON medicine_order(archived);

CREATE INDEX idx_order_priority
    ON medicine_order(priority_level);

CREATE INDEX idx_order_created_at
    ON medicine_order(created_at DESC);


-- Теги основной сущности (медикаментозные и логистические метки заказа)
CREATE TABLE medicine_order_tag (
    order_id UUID NOT NULL REFERENCES medicine_order(id) ON DELETE CASCADE,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (order_id, tag)
);

CREATE INDEX idx_order_tag_value
    ON medicine_order_tag(tag);


-- Заметки фармацевтов-диспетчеров к заказу
CREATE TABLE order_note (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES medicine_order(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES app_user(id),
    body VARCHAR(4000) NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_note_order
    ON order_note(order_id, pinned DESC, created_at DESC);


-- Журнал аудита
CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor_id UUID,
    actor_email VARCHAR(254),
    event_type VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80),
    entity_id UUID,
    summary VARCHAR(1000) NOT NULL,
    correlation_id VARCHAR(100),
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    details_json TEXT
);

CREATE INDEX idx_audit_occurred
    ON audit_event(occurred_at DESC);

CREATE INDEX idx_audit_actor
    ON audit_event(actor_email);

CREATE INDEX idx_audit_entity
    ON audit_event(entity_type, entity_id);
