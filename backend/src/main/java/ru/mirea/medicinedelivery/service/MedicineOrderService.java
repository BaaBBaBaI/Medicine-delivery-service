package ru.mirea.medicinedelivery.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.medicinedelivery.config.AppProperties;
import ru.mirea.medicinedelivery.domain.Customer;
import ru.mirea.medicinedelivery.domain.MedicineOrder;
import ru.mirea.medicinedelivery.domain.OrderStatus;
import ru.mirea.medicinedelivery.domain.UserAccount;
import ru.mirea.medicinedelivery.dto.OrderDtos.BulkAction;
import ru.mirea.medicinedelivery.dto.OrderDtos.BulkFailure;
import ru.mirea.medicinedelivery.dto.OrderDtos.BulkRequest;
import ru.mirea.medicinedelivery.dto.OrderDtos.BulkResult;
import ru.mirea.medicinedelivery.dto.OrderDtos.OrderRequest;
import ru.mirea.medicinedelivery.dto.OrderDtos.OrderResponse;
import ru.mirea.medicinedelivery.error.BadRequestException;
import ru.mirea.medicinedelivery.error.ConflictException;
import ru.mirea.medicinedelivery.error.NotFoundException;
import ru.mirea.medicinedelivery.filter.MedicineOrderSpecifications;
import ru.mirea.medicinedelivery.filter.OrderFilter;
import ru.mirea.medicinedelivery.filter.SmartFilterParser;
import ru.mirea.medicinedelivery.repository.CustomerRepository;
import ru.mirea.medicinedelivery.repository.MedicineOrderRepository;
import ru.mirea.medicinedelivery.security.CurrentUserService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Основной прикладной сервис управления заказами доставки лекарств. */
@Service
public class MedicineOrderService {
    private static final DateTimeFormatter ORDER_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MedicineOrderRepository orders;
    private final CustomerRepository customers;
    private final CurrentUserService currentUser;
    private final SmartFilterParser parser;
    private final MedicineOrderMapper mapper;
    private final PriorityCalculator priorityCalculator;
    private final AuditService audit;
    private final AppProperties properties;

    public MedicineOrderService(MedicineOrderRepository orders, CustomerRepository customers,
                                CurrentUserService currentUser, SmartFilterParser parser,
                                MedicineOrderMapper mapper, PriorityCalculator priorityCalculator,
                                AuditService audit, AppProperties properties) {
        this.orders = orders;
        this.customers = customers;
        this.currentUser = currentUser;
        this.parser = parser;
        this.mapper = mapper;
        this.priorityCalculator = priorityCalculator;
        this.audit = audit;
        this.properties = properties;
    }

    /** Выполняет безопасный умный поиск и фильтрацию с серверной пагинацией. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> search(String query, Pageable pageable) {
        OrderFilter filter = parser.parse(query);
        return orders.findAll(MedicineOrderSpecifications.from(filter), pageable).map(mapper::toResponse);
    }

    /** Возвращает карточку заказа по UUID. */
    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    /** Создаёт новый заказ доставки лекарств и вычисляет приоритет доставки. */
    @Transactional
    public OrderResponse create(OrderRequest request) {
        UserAccount actor = currentUser.requireEntity();
        String orderNumber = resolveOrderNumber(request.orderNumber(), null);
        MedicineOrder order = new MedicineOrder(
                UUID.randomUUID(),
                orderNumber,
                normalizeRequired(request.medicationSummary()),
                normalizeRequired(request.deliveryAddress()),
                normalizeRequired(request.contactPhone()),
                request.totalAmount(),
                actor
        );
        apply(order, request, orderNumber, actor);
        orders.save(order);
        audit.record("ORDER_CREATED", "MEDICINE_ORDER", order.getId(),
                "Создан заказ доставки лекарств " + order.getOrderNumber(), Map.of());
        return mapper.toResponse(order);
    }

    /** Изменяет заказ только при совпадении optimistic version. */
    @Transactional
    public OrderResponse update(UUID id, OrderRequest request) {
        MedicineOrder order = require(id);
        verifyVersion(request.version(), order.getVersion());
        String orderNumber = resolveOrderNumber(request.orderNumber(), id);
        UserAccount actor = currentUser.requireEntity();
        apply(order, request, orderNumber, actor);
        audit.record("ORDER_UPDATED", "MEDICINE_ORDER", id,
                "Изменён заказ доставки лекарств " + order.getOrderNumber(),
                Map.of("version", order.getVersion(), "status", order.getStatus().name()));
        return mapper.toResponse(order);
    }

    /** Архивирует заказ вместо физического удаления. */
    @Transactional
    public OrderResponse archive(UUID id) {
        MedicineOrder order = require(id);
        archiveInternal(order, currentUser.requireEntity());
        audit.record("ORDER_ARCHIVED", "MEDICINE_ORDER", id,
                "Заказ " + order.getOrderNumber() + " перемещён в архив", Map.of());
        return mapper.toResponse(order);
    }

    /** Восстанавливает заказ из архива. */
    @Transactional
    public OrderResponse restore(UUID id) {
        MedicineOrder order = require(id);
        order.setArchived(false);
        order.setArchivedAt(null);
        order.setArchivedBy(null);
        order.setUpdatedBy(currentUser.requireEntity());
        audit.record("ORDER_RESTORED", "MEDICINE_ORDER", id,
                "Заказ " + order.getOrderNumber() + " восстановлен из архива", Map.of());
        return mapper.toResponse(order);
    }

    /** Окончательно удаляет заказ только администратором при включённом флаге и заголовке подтверждения. */
    @Transactional
    public void permanentDelete(UUID id, String confirmation) {
        if (!properties.permanentDeleteEnabled()) {
            throw new BadRequestException("Безвозвратное удаление отключено конфигурацией сервера");
        }
        if (!"PERMANENTLY_DELETE".equals(confirmation)) {
            throw new BadRequestException("Для окончательного удаления требуется заголовок X-Confirm-Delete: PERMANENTLY_DELETE");
        }
        MedicineOrder order = require(id);
        String number = order.getOrderNumber();
        orders.delete(order);
        audit.record("ORDER_PERMANENTLY_DELETED", "MEDICINE_ORDER", id,
                "Безвозвратно удалён заказ " + number, Map.of());
    }

    /** Выполняет массовую операцию над набором UUID и возвращает отчёт по успехам и ошибкам. */
    @Transactional
    public BulkResult bulk(BulkRequest request) {
        validateBulkRequest(request);
        UserAccount actor = currentUser.requireEntity();
        int succeeded = 0;
        var failures = new ArrayList<BulkFailure>();
        for (UUID id : request.ids()) {
            MedicineOrder order = orders.findById(id).orElse(null);
            if (order == null) {
                failures.add(new BulkFailure(id, "Заказ не найден"));
                continue;
            }
            try {
                applyBulk(order, request, actor);
                succeeded++;
            } catch (RuntimeException ex) {
                failures.add(new BulkFailure(id, ex.getMessage()));
            }
        }
        audit.record("ORDER_BULK_" + request.action().name(), "MEDICINE_ORDER", null,
                "Выполнена массовая операция над заказами",
                Map.of("requested", request.ids().size(), "succeeded", succeeded));
        return new BulkResult(request.ids().size(), succeeded, failures);
    }

    /** Используется CSV-сервисом при импорте без дублирования бизнес-правил. */
    MedicineOrder upsertFromImport(MedicineOrder existing, OrderRequest request, UserAccount actor) {
        String orderNumber = normalizeRequired(request.orderNumber()).toUpperCase(Locale.ROOT);
        if (existing == null) {
            MedicineOrder created = new MedicineOrder(
                    UUID.randomUUID(),
                    orderNumber,
                    normalizeRequired(request.medicationSummary()),
                    normalizeRequired(request.deliveryAddress()),
                    normalizeRequired(request.contactPhone()),
                    request.totalAmount(),
                    actor
            );
            apply(created, request, orderNumber, actor);
            return orders.save(created);
        }
        apply(existing, request, orderNumber, actor);
        return existing;
    }

    private void apply(MedicineOrder order, OrderRequest request, String orderNumber, UserAccount actor) {
        validatePrescription(request.prescriptionRequired(), request.prescriptionNumber());
        order.setOrderNumber(orderNumber);
        order.setCustomer(resolveCustomer(request.customerId()));
        order.setMedicationSummary(normalizeRequired(request.medicationSummary()));
        order.setDeliveryAddress(normalizeRequired(request.deliveryAddress()));
        order.setContactPhone(normalizeRequired(request.contactPhone()));
        order.setTotalAmount(request.totalAmount());
        order.setStatus(request.status());
        order.setPaymentMethod(request.paymentMethod());
        order.setPrescriptionRequired(request.prescriptionRequired());
        order.setPrescriptionNumber(request.prescriptionRequired()
                ? normalizeRequired(request.prescriptionNumber())
                : null);
        order.setDeliveryNotes(normalizeNullable(request.deliveryNotes()));
        order.setTags(normalizeTags(request.tags()));
        order.setPriorityLevel(priorityCalculator.calculate(
                request.status(), request.totalAmount(), request.prescriptionRequired()));
        if (request.status() == OrderStatus.DELIVERED) {
            if (order.getDeliveredAt() == null) {
                order.setDeliveredAt(Instant.now());
            }
        } else {
            order.setDeliveredAt(null);
        }
        order.setUpdatedBy(actor);
    }

    private void applyBulk(MedicineOrder order, BulkRequest request, UserAccount actor) {
        switch (request.action()) {
            case ARCHIVE -> archiveInternal(order, actor);
            case RESTORE -> {
                order.setArchived(false);
                order.setArchivedAt(null);
                order.setArchivedBy(null);
                order.setUpdatedBy(actor);
            }
            case SET_STATUS -> {
                order.setStatus(request.status());
                if (request.status() == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
                    order.setDeliveredAt(Instant.now());
                } else if (request.status() != OrderStatus.DELIVERED) {
                    order.setDeliveredAt(null);
                }
                order.setPriorityLevel(priorityCalculator.calculate(
                        order.getStatus(), order.getTotalAmount(), order.isPrescriptionRequired()));
                order.setUpdatedBy(actor);
            }
            case ADD_TAG -> {
                Set<String> tags = new LinkedHashSet<>(order.getTags());
                tags.add(normalizeTag(request.tag()));
                order.setTags(tags);
                order.setUpdatedBy(actor);
            }
            case REMOVE_TAG -> {
                Set<String> tags = new LinkedHashSet<>(order.getTags());
                tags.remove(normalizeTag(request.tag()));
                order.setTags(tags);
                order.setUpdatedBy(actor);
            }
        }
    }

    private static void validatePrescription(boolean required, String number) {
        if (required && (number == null || number.trim().isEmpty())) {
            throw new BadRequestException("Для рецептурного заказа необходимо указать номер рецепта");
        }
    }

    private static void validateBulkRequest(BulkRequest request) {
        if (request.action() == BulkAction.SET_STATUS && request.status() == null) {
            throw new BadRequestException("Для операции SET_STATUS необходимо указать status");
        }
        if ((request.action() == BulkAction.ADD_TAG || request.action() == BulkAction.REMOVE_TAG)
                && (request.tag() == null || request.tag().isBlank())) {
            throw new BadRequestException("Для операции с тегом необходимо указать tag");
        }
    }

    private static void archiveInternal(MedicineOrder order, UserAccount actor) {
        order.setArchived(true);
        order.setArchivedAt(Instant.now());
        order.setArchivedBy(actor);
        order.setUpdatedBy(actor);
    }

    private MedicineOrder require(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Заказ не найден"));
    }

    private Customer resolveCustomer(UUID id) {
        if (id == null) return null;
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new BadRequestException("Клиент из справочника не найден"));
        if (!customer.isActive()) {
            throw new BadRequestException("Нельзя назначить отключённого клиента из справочника");
        }
        return customer;
    }

    private String resolveOrderNumber(String rawOrderNumber, UUID currentId) {
        String normalized = normalizeNullable(rawOrderNumber);
        if (normalized == null) {
            String datePart = LocalDate.now(ZoneOffset.UTC).format(ORDER_DATE_FMT);
            for (int attempt = 0; attempt < 20; attempt++) {
                String candidate = String.format("ORD-%s-%04d", datePart, ThreadLocalRandom.current().nextInt(1000, 10000));
                if (!orders.existsByOrderNumberIgnoreCase(candidate)) {
                    return candidate;
                }
            }
            return "ORD-" + datePart + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
        String code = normalized.toUpperCase(Locale.ROOT);
        boolean exists = currentId == null
                ? orders.existsByOrderNumberIgnoreCase(code)
                : orders.existsByOrderNumberIgnoreCaseAndIdNot(code, currentId);
        if (exists) {
            throw new ConflictException("Заказ с номером " + code + " уже существует");
        }
        return code;
    }

    private static void verifyVersion(Long requested, long actual) {
        if (requested == null) throw new BadRequestException("Для изменения записи требуется поле version");
        if (requested != actual) throw new ConflictException("Заказ уже изменён другим пользователем. Обновите карточку");
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) result.add(normalizeTag(tag));
        if (result.size() > 20) throw new BadRequestException("Допустимо не более 20 тегов");
        return result;
    }

    private static String normalizeTag(String value) {
        String normalized = normalizeRequired(value).toLowerCase(Locale.ROOT);
        if (normalized.length() > 50) throw new BadRequestException("Тег длиннее 50 символов");
        return normalized;
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException("Обязательное текстовое поле не заполнено");
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeNullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().replaceAll("\\s+", " ");
    }
}
