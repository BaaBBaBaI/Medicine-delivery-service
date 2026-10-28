package ru.mirea.project.service;

import ru.mirea.project.exception.BusinessException;
import ru.mirea.project.exception.EntityNotFoundException;
import ru.mirea.project.model.*;
import ru.mirea.project.repository.MedicineOrderRepository;
import ru.mirea.project.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class MedicineOrderServiceImpl implements MedicineOrderService {

    private final MedicineOrderRepository orderRepository;
    private final UserRepository userRepository;

    public MedicineOrderServiceImpl(MedicineOrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Override
    public MedicineOrder createOrder(MedicineOrder order) {
        if (order == null) {
            throw new BusinessException("Заказ не может быть пустым.");
        }

        if (order.getClientId() == null) {
            throw new BusinessException("Необходимо выбрать клиента-заказчика из списка.");
        }
        User client = userRepository.findById(order.getClientId())
                .orElseThrow(() -> new BusinessException("Нельзя создать заказ: клиент с ID=" + order.getClientId() + " не найден в базе данных."));
        order.setClient(client);

        if (order.getOrderNumber() == null || order.getOrderNumber().trim().isEmpty()) {
            order.setOrderNumber(generateOrderNumber());
        } else {
            order.setOrderNumber(order.getOrderNumber().trim());
            if (order.getOrderNumber().length() > 30) {
                throw new BusinessException("Номер заказа не должен превышать 30 символов.");
            }
        }

        orderRepository.findByOrderNumber(order.getOrderNumber()).ifPresent(existing -> {
            throw new BusinessException("Заказ с номером '" + order.getOrderNumber() + "' уже существует в системе.");
        });

        validateAddressAndPhone(order.getDeliveryAddress(), order.getContactPhone());
        validateAmount(order.getTotalAmount());
        validatePrescription(order.isPrescriptionRequired(), order.getPrescriptionNumber());

        if (order.getPaymentMethod() == null) {
            throw new BusinessException("Необходимо выбрать способ оплаты.");
        }

        order.setDeliveryAddress(order.getDeliveryAddress().trim());
        order.setContactPhone(order.getContactPhone().trim());
        order.setPrescriptionNumber(order.isPrescriptionRequired() ? order.getPrescriptionNumber().trim() : null);
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(LocalDateTime.now());
        order.setDeliveredAt(null);

        return orderRepository.save(order);
    }

    @Override
    public MedicineOrder getOrderById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("ID заказа должен быть положительным числом.");
        }
        if (id > 1_000_000_000L) {
            throw new BusinessException("ID заказа не должен превышать 1 000 000 000.");
        }
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Заказ доставки лекарств", id));
    }

    @Override
    public MedicineOrder getOrderByNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new BusinessException("Номер заказа не может быть пустым.");
        }
        return orderRepository.findByOrderNumber(orderNumber.trim())
                .orElseThrow(() -> new EntityNotFoundException("Заказ с номером " + orderNumber + " не найден."));
    }

    @Override
    public List<MedicineOrder> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public MedicineOrder updateOrder(MedicineOrder order) {
        if (order == null || order.getId() == null) {
            throw new BusinessException("ID заказа обязателен для редактирования.");
        }

        MedicineOrder existing = getOrderById(order.getId());

        if (existing.getStatus().isFinal()) {
            throw new BusinessException("Нельзя редактировать заказ в финальном статусе «"
                    + existing.getStatus().getDisplayName() + "».");
        }

        if (order.getClientId() == null) {
            throw new BusinessException("Необходимо выбрать клиента-заказчика из списка.");
        }
        User client = userRepository.findById(order.getClientId())
                .orElseThrow(() -> new BusinessException("Клиент с ID=" + order.getClientId() + " не найден в базе данных."));

        validateAddressAndPhone(order.getDeliveryAddress(), order.getContactPhone());
        validateAmount(order.getTotalAmount());
        validatePrescription(order.isPrescriptionRequired(), order.getPrescriptionNumber());

        if (order.getPaymentMethod() == null) {
            throw new BusinessException("Необходимо выбрать способ оплаты.");
        }

        existing.setClientId(client.getId());
        existing.setClient(client);
        existing.setDeliveryAddress(order.getDeliveryAddress().trim());
        existing.setContactPhone(order.getContactPhone().trim());
        existing.setTotalAmount(order.getTotalAmount());
        existing.setPaymentMethod(order.getPaymentMethod());
        existing.setPrescriptionRequired(order.isPrescriptionRequired());
        existing.setPrescriptionNumber(order.isPrescriptionRequired() ? order.getPrescriptionNumber().trim() : null);
        existing.setDeliveryNotes(order.getDeliveryNotes());

        boolean updated = orderRepository.update(existing);
        if (!updated) {
            throw new BusinessException("Не удалось обновить заказ ID=" + order.getId());
        }
        return existing;
    }

    @Override
    public void deleteOrder(Long id) {
        MedicineOrder order = getOrderById(id);

        if (order.getStatus() == OrderStatus.IN_DELIVERY || order.getStatus() == OrderStatus.ASSEMBLING) {
            throw new BusinessException("Запрещено удалять заказ в процессе комплектации или доставки (текущий статус: «"
                    + order.getStatus().getDisplayName() + "»). Сначала переведите его в статус «Отменен».");
        }

        boolean deleted = orderRepository.deleteById(id);
        if (!deleted) {
            throw new BusinessException("Не удалось удалить заказ ID=" + id);
        }
    }

    @Override
    public MedicineOrder changeOrderStatus(Long id, OrderStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessException("Необходимо указать новый статус заказа.");
        }
        MedicineOrder order = getOrderById(id);
        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return order;
        }

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new BusinessException(String.format(
                    "Недопустимый переход между статусами: невозможно перевести заказ из «%s» в «%s».",
                    currentStatus.getDisplayName(), newStatus.getDisplayName()));
        }

        if (order.isPrescriptionRequired() && (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.IN_DELIVERY)) {
            if (order.getPrescriptionNumber() == null || order.getPrescriptionNumber().trim().isEmpty()) {
                throw new BusinessException("Невозможно подтвердить или отправить рецептурный заказ без указанного номера рецепта.");
            }
        }

        boolean success = orderRepository.updateStatus(id, newStatus);
        if (!success) {
            throw new BusinessException("Ошибка при обновлении статуса заказа ID=" + id + " в базе данных.");
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }
        return order;
    }

    @Override
    public List<MedicineOrder> searchByKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllOrders();
        }
        return orderRepository.searchByKeyword(keyword.trim());
    }

    @Override
    public List<MedicineOrder> searchByClientName(String clientName) {
        if (clientName == null || clientName.trim().isEmpty()) {
            return getAllOrders();
        }
        String lower = clientName.trim().toLowerCase();
        return getAllOrders().stream()
                .filter(o -> o.getClient() != null && o.getClient().getFullName().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineOrder> searchByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return getAllOrders();
        }
        String cleaned = phone.replaceAll("[^0-9]", "");
        return getAllOrders().stream()
                .filter(o -> {
                    String p1 = o.getContactPhone() != null ? o.getContactPhone().replaceAll("[^0-9]", "") : "";
                    return p1.contains(cleaned);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineOrder> filterByStatus(OrderStatus status) {
        if (status == null) {
            return getAllOrders();
        }
        return getAllOrders().stream()
                .filter(o -> o.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineOrder> filterByPrescription(boolean prescriptionRequired) {
        return getAllOrders().stream()
                .filter(o -> o.isPrescriptionRequired() == prescriptionRequired)
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineOrder> filterByAmountRange(double minAmount, double maxAmount) {
        if (minAmount < 0 || maxAmount < minAmount) {
            throw new BusinessException("Указан некорректный диапазон суммы заказа.");
        }
        return getAllOrders().stream()
                .filter(o -> o.getTotalAmount() >= minAmount && o.getTotalAmount() <= maxAmount)
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineOrder> filterByPaymentMethod(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return getAllOrders();
        }
        return getAllOrders().stream()
                .filter(o -> o.getPaymentMethod() == paymentMethod)
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineOrder> findOrdersWithFilters(
            String keyword,
            OrderStatus status,
            PaymentMethod paymentMethod,
            Boolean prescriptionRequired,
            Long clientId,
            Double minAmount,
            Double maxAmount,
            Comparator<MedicineOrder> sortComparator
    ) {
        if (minAmount != null && minAmount < 0) {
            throw new BusinessException("Минимальная сумма фильтра не может быть отрицательной.");
        }
        if (maxAmount != null && maxAmount < 0) {
            throw new BusinessException("Максимальная сумма фильтра не может быть отрицательной.");
        }
        if (minAmount != null && maxAmount != null && minAmount > maxAmount) {
            throw new BusinessException("Минимальная сумма фильтра не может превышать максимальную сумму.");
        }

        List<MedicineOrder> source = (keyword != null && !keyword.trim().isEmpty())
                ? orderRepository.searchByKeyword(keyword.trim())
                : orderRepository.findAll();

        return source.stream()
                .filter(o -> status == null || o.getStatus() == status)
                .filter(o -> paymentMethod == null || o.getPaymentMethod() == paymentMethod)
                .filter(o -> prescriptionRequired == null || o.isPrescriptionRequired() == prescriptionRequired)
                .filter(o -> clientId == null || Objects.equals(o.getClientId(), clientId))
                .filter(o -> minAmount == null || o.getTotalAmount() >= minAmount)
                .filter(o -> maxAmount == null || o.getTotalAmount() <= maxAmount)
                .sorted(sortComparator != null ? sortComparator : Comparator.comparing(MedicineOrder::getId))
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineOrder> getSortedOrders(Comparator<MedicineOrder> comparator) {
        return getAllOrders().stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    @Override
    public OrderStatistics calculateStatistics() {
        List<MedicineOrder> orders = getAllOrders();
        long totalClients = userRepository.count();
        long totalOrders = orders.size();

        long activeCount = orders.stream().filter(o -> o.getStatus().isActive()).count();
        long deliveredCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long cancelledCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();

        double totalDeliveredRevenue = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .mapToDouble(MedicineOrder::getTotalAmount)
                .sum();

        double averageAmount = orders.stream()
                .mapToDouble(MedicineOrder::getTotalAmount)
                .average()
                .orElse(0.0);

        double minAmount = orders.stream()
                .mapToDouble(MedicineOrder::getTotalAmount)
                .min()
                .orElse(0.0);

        double maxAmount = orders.stream()
                .mapToDouble(MedicineOrder::getTotalAmount)
                .max()
                .orElse(0.0);

        long prescriptionCount = orders.stream()
                .filter(MedicineOrder::isPrescriptionRequired)
                .count();

        double prescriptionPercent = totalOrders > 0 ? ((double) prescriptionCount / totalOrders) * 100.0 : 0.0;

        Map<OrderStatus, Long> statusCounts = orders.stream()
                .collect(Collectors.groupingBy(MedicineOrder::getStatus, Collectors.counting()));

        Map<PaymentMethod, Long> paymentCounts = orders.stream()
                .collect(Collectors.groupingBy(MedicineOrder::getPaymentMethod, Collectors.counting()));

        return new OrderStatistics(
                totalClients,
                totalOrders,
                activeCount,
                deliveredCount,
                cancelledCount,
                totalDeliveredRevenue,
                averageAmount,
                minAmount,
                maxAmount,
                prescriptionCount,
                prescriptionPercent,
                statusCounts,
                paymentCounts
        );
    }

    private void validateAddressAndPhone(String address, String phone) {
        if (address == null || address.trim().length() < 5) {
            throw new BusinessException("Поле «Адрес доставки» обязательно и должно содержать не менее 5 символов.");
        }
        if (address.trim().length() > 255) {
            throw new BusinessException("Поле «Адрес доставки» не должно превышать 255 символов.");
        }
        if (phone == null || phone.trim().length() < 6) {
            throw new BusinessException("Поле «Контактный телефон» обязательно и должно содержать не менее 6 символов.");
        }
        if (phone.trim().length() > 25) {
            throw new BusinessException("Поле «Контактный телефон» не должно превышать 25 символов.");
        }
    }

    private void validateAmount(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0.0) {
            throw new BusinessException("Поле «Сумма заказа» должно быть строго положительным числом (больше 0 руб.).");
        }
        if (amount > 100_000.0) {
            throw new BusinessException("Сумма разового заказа лекарств не может превышать 100 000 руб.");
        }
    }

    private void validatePrescription(boolean isRequired, String prescriptionNumber) {
        if (isRequired) {
            if (prescriptionNumber == null || prescriptionNumber.trim().isEmpty()) {
                throw new BusinessException("Для рецептурных препаратов обязательно заполните поле «Номер рецепта» (например, RX-77-90214).");
            }
            if (prescriptionNumber.trim().length() > 60) {
                throw new BusinessException("Поле «Номер рецепта» не должно превышать 60 символов.");
            }
        }
    }

    private synchronized String generateOrderNumber() {
        return "MED-2026-" + String.format("%04d", (int) (Math.random() * 9000) + 1000);
    }
}
