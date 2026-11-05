package ru.mirea.medicinedelivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Справочная сущность клиента аптечной сети (соответствует сущности User / таблица users из КР №1 и КР №2).
 *
 * <p>Поддерживает включение и отключение без физического удаления, чтобы история заказов
 * всегда сохраняла целостность внешних ключей.</p>
 */
@Entity
@Table(name = "customer")
public class Customer extends AuditedVersionedEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(name = "default_address", nullable = false, length = 255)
    private String defaultAddress;

    @Column(nullable = false)
    private boolean active = true;

    protected Customer() {
    }

    public Customer(UUID id, String code, String fullName, String phone, String email, String defaultAddress) {
        this.id = id;
        this.code = code;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.defaultAddress = defaultAddress;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(String defaultAddress) { this.defaultAddress = defaultAddress; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
