DROP TABLE IF EXISTS medicine_orders CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(25) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    default_address VARCHAR(255) NOT NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_phone_not_empty CHECK (length(trim(phone)) >= 6),
    CONSTRAINT chk_users_name_not_empty CHECK (length(trim(full_name)) >= 2)
);

CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE medicine_orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL UNIQUE,
    client_id BIGINT NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(25) NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    prescription_required BOOLEAN NOT NULL DEFAULT FALSE,
    prescription_number VARCHAR(60),
    delivery_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    
    CONSTRAINT fk_orders_client FOREIGN KEY (client_id) 
        REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
        
    CONSTRAINT chk_orders_amount_positive CHECK (total_amount > 0),
    CONSTRAINT chk_orders_status CHECK (status IN (
        'CREATED', 'CONFIRMED', 'ASSEMBLING', 'IN_DELIVERY', 'DELIVERED', 'CANCELLED'
    )),
    CONSTRAINT chk_orders_payment CHECK (payment_method IN (
        'CASH', 'CARD_ONLINE', 'CARD_ON_DELIVERY', 'SBP'
    )),
    CONSTRAINT chk_orders_prescription CHECK (
        prescription_required = FALSE OR 
        (prescription_required = TRUE AND prescription_number IS NOT NULL AND length(trim(prescription_number)) > 0)
    )
);

CREATE INDEX idx_orders_client_id ON medicine_orders(client_id);
CREATE INDEX idx_orders_status ON medicine_orders(status);
CREATE INDEX idx_orders_created_at ON medicine_orders(created_at);
