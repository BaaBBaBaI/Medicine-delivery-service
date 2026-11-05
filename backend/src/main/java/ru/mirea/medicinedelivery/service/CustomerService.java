package ru.mirea.medicinedelivery.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.medicinedelivery.domain.Customer;
import ru.mirea.medicinedelivery.dto.CustomerDtos.CustomerRequest;
import ru.mirea.medicinedelivery.dto.CustomerDtos.CustomerResponse;
import ru.mirea.medicinedelivery.error.BadRequestException;
import ru.mirea.medicinedelivery.error.ConflictException;
import ru.mirea.medicinedelivery.error.NotFoundException;
import ru.mirea.medicinedelivery.repository.CustomerRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Сервис справочника клиентов аптечной сети. */
@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, AuditService audit) {
        this.customers = customers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list(boolean activeOnly) {
        return (activeOnly ? customers.findByActiveTrueOrderByCodeAsc() : customers.findAllByOrderByCodeAsc())
                .stream().map(CustomerService::toResponse).toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String code = normalizeCode(request.code());
        if (customers.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Клиент с кодом " + code + " уже существует в справочнике");
        }
        Customer customer = new Customer(
                UUID.randomUUID(),
                code,
                normalizeRequired(request.fullName()),
                normalizeRequired(request.phone()),
                normalizeEmail(request.email()),
                normalizeRequired(request.defaultAddress())
        );
        if (request.active() != null) customer.setActive(request.active());
        customers.save(customer);
        audit.record("CUSTOMER_CREATED", "CUSTOMER", customer.getId(),
                "Добавлен клиент " + code + " (" + customer.getFullName() + ")", Map.of());
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = require(id);
        if (request.version() == null || request.version() != customer.getVersion()) {
            throw new ConflictException("Карточка клиента уже изменена. Обновите список");
        }
        String code = normalizeCode(request.code());
        customers.findByCodeIgnoreCase(code).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Клиент с кодом " + code + " уже существует");
        });
        customer.setCode(code);
        customer.setFullName(normalizeRequired(request.fullName()));
        customer.setPhone(normalizeRequired(request.phone()));
        customer.setEmail(normalizeEmail(request.email()));
        customer.setDefaultAddress(normalizeRequired(request.defaultAddress()));
        if (request.active() != null) customer.setActive(request.active());
        audit.record("CUSTOMER_UPDATED", "CUSTOMER", id,
                "Изменён клиент " + code + " (" + customer.getFullName() + ")", Map.of());
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse setActive(UUID id, boolean active, long version) {
        Customer customer = require(id);
        if (version != customer.getVersion()) {
            throw new ConflictException("Карточка клиента уже изменена. Обновите список");
        }
        customer.setActive(active);
        audit.record(active ? "CUSTOMER_ACTIVATED" : "CUSTOMER_DEACTIVATED", "CUSTOMER", id,
                (active ? "Включён" : "Отключён") + " клиент " + customer.getCode(), Map.of());
        return toResponse(customer);
    }

    private Customer require(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Клиент не найден"));
    }

    private static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getDefaultAddress(),
                customer.isActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                customer.getVersion()
        );
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("Код клиента обязателен");
        return value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) throw new BadRequestException("Обязательное поле не заполнено");
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeEmail(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
