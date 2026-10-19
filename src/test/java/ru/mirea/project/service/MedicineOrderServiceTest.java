package ru.mirea.project.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mirea.project.exception.BusinessException;
import ru.mirea.project.model.*;
import ru.mirea.project.repository.MedicineOrderRepository;
import ru.mirea.project.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Модульные тесты бизнес-логики, валидации, фильтрации и расчета статистики для КР №2.
 */
class MedicineOrderServiceTest {

    private InMemoryUserRepository userRepository;
    private InMemoryOrderRepository orderRepository;
    private MedicineOrderService orderService;
    private User testClient;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        orderRepository = new InMemoryOrderRepository();
        orderService = new MedicineOrderServiceImpl(orderRepository, userRepository);

        testClient = userRepository.save(new User(
                1L, "Веневцев Артём Андреевич", "+7 (916) 111-22-33",
                "venevtsev.artem@mirea.ru", "г. Москва, пр-т Вернадского, д. 78", LocalDateTime.now()
        ));
    }

    @Test
    @DisplayName("Создание корректного заказа присваивает статус CREATED и генерирует номер")
    void createValidOrderSuccess() {
        MedicineOrder order = new MedicineOrder();
        order.setClientId(testClient.getId());
        order.setDeliveryAddress("г. Москва, пр-т Вернадского, д. 78, кв. 101");
        order.setContactPhone("+7 (916) 111-22-33");
        order.setTotalAmount(2450.00);
        order.setPaymentMethod(PaymentMethod.CARD_ONLINE);
        order.setPrescriptionRequired(false);

        MedicineOrder created = orderService.createOrder(order);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getOrderNumber()).startsWith("MED-2026-");
        assertThat(created.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("Валидация отклоняет рецептурный заказ без номера рецепта")
    void createPrescriptionOrderWithoutNumberThrowsException() {
        MedicineOrder order = new MedicineOrder();
        order.setClientId(testClient.getId());
        order.setDeliveryAddress("г. Москва, пр-т Вернадского, д. 78");
        order.setContactPhone("+7 (916) 111-22-33");
        order.setTotalAmount(3200.00);
        order.setPaymentMethod(PaymentMethod.SBP);
        order.setPrescriptionRequired(true);
        order.setPrescriptionNumber("   ");

        assertThatThrownBy(() -> orderService.createOrder(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Номер рецепта");
    }

    @Test
    @DisplayName("Запрещено удалять заказ в статусе IN_DELIVERY или ASSEMBLING")
    void deleteOrderInDeliveryThrowsException() {
        MedicineOrder order = new MedicineOrder();
        order.setClientId(testClient.getId());
        order.setDeliveryAddress("г. Москва, пр-т Вернадского, д. 78");
        order.setContactPhone("+7 (916) 111-22-33");
        order.setTotalAmount(1500.00);
        MedicineOrder saved = orderService.createOrder(order);

        orderService.changeOrderStatus(saved.getId(), OrderStatus.CONFIRMED);
        orderService.changeOrderStatus(saved.getId(), OrderStatus.ASSEMBLING);

        assertThatThrownBy(() -> orderService.deleteOrder(saved.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Запрещено удалять заказ");
    }

    @Test
    @DisplayName("Расчет статистики возвращает корректные показатели выручки, мин/макс и долей")
    void calculateStatisticsReturnsAccurateMetrics() {
        MedicineOrder o1 = new MedicineOrder();
        o1.setClientId(testClient.getId());
        o1.setDeliveryAddress("г. Москва, ул. Пироговская, д. 1");
        o1.setContactPhone("+7 (916) 111-22-33");
        o1.setTotalAmount(1000.00);
        o1.setPrescriptionRequired(true);
        o1.setPrescriptionNumber("RX-100");
        o1 = orderService.createOrder(o1);
        orderService.changeOrderStatus(o1.getId(), OrderStatus.CONFIRMED);
        orderService.changeOrderStatus(o1.getId(), OrderStatus.ASSEMBLING);
        orderService.changeOrderStatus(o1.getId(), OrderStatus.IN_DELIVERY);
        orderService.changeOrderStatus(o1.getId(), OrderStatus.DELIVERED);

        MedicineOrder o2 = new MedicineOrder();
        o2.setClientId(testClient.getId());
        o2.setDeliveryAddress("г. Москва, ул. Вавилова, д. 28");
        o2.setContactPhone("+7 (916) 111-22-33");
        o2.setTotalAmount(3000.00);
        o2.setPrescriptionRequired(false);
        orderService.createOrder(o2);

        OrderStatistics stats = orderService.calculateStatistics();

        assertThat(stats.getTotalClients()).isEqualTo(1L);
        assertThat(stats.getTotalOrders()).isEqualTo(2L);
        assertThat(stats.getDeliveredOrdersCount()).isEqualTo(1L);
        assertThat(stats.getActiveOrdersCount()).isEqualTo(1L);
        assertThat(stats.getTotalDeliveredRevenue()).isEqualTo(1000.00);
        assertThat(stats.getAverageOrderAmount()).isEqualTo(2000.00);
        assertThat(stats.getMinOrderAmount()).isEqualTo(1000.00);
        assertThat(stats.getMaxOrderAmount()).isEqualTo(3000.00);
        assertThat(stats.getPrescriptionPercentage()).isEqualTo(50.0);
    }

    // Вспомогательные In-Memory репозитории для изолированного тестирования сервисного слоя
    private static class InMemoryUserRepository implements UserRepository {
        private final Map<Long, User> storage = new LinkedHashMap<>();
        private long seq = 1;

        @Override
        public User save(User entity) {
            if (entity.getId() == null) entity.setId(seq++);
            storage.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<User> findById(Long id) { return Optional.ofNullable(storage.get(id)); }

        @Override
        public List<User> findAll() { return new ArrayList<>(storage.values()); }

        @Override
        public boolean update(User entity) {
            storage.put(entity.getId(), entity);
            return true;
        }

        @Override
        public boolean deleteById(Long id) { return storage.remove(id) != null; }

        @Override
        public long count() { return storage.size(); }

        @Override
        public Optional<User> findByPhone(String phone) {
            return storage.values().stream().filter(u -> u.getPhone().equals(phone)).findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return storage.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public List<User> searchByName(String namePart) {
            return storage.values().stream().filter(u -> u.getFullName().contains(namePart)).toList();
        }
    }

    private static class InMemoryOrderRepository implements MedicineOrderRepository {
        private final Map<Long, MedicineOrder> storage = new LinkedHashMap<>();
        private long seq = 1;

        @Override
        public MedicineOrder save(MedicineOrder entity) {
            if (entity.getId() == null) entity.setId(seq++);
            storage.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<MedicineOrder> findById(Long id) { return Optional.ofNullable(storage.get(id)); }

        @Override
        public List<MedicineOrder> findAll() { return new ArrayList<>(storage.values()); }

        @Override
        public boolean update(MedicineOrder entity) {
            storage.put(entity.getId(), entity);
            return true;
        }

        @Override
        public boolean deleteById(Long id) { return storage.remove(id) != null; }

        @Override
        public long count() { return storage.size(); }

        @Override
        public Optional<MedicineOrder> findByOrderNumber(String orderNumber) {
            return storage.values().stream().filter(o -> o.getOrderNumber().equals(orderNumber)).findFirst();
        }

        @Override
        public List<MedicineOrder> findByClientId(Long clientId) {
            return storage.values().stream().filter(o -> Objects.equals(o.getClientId(), clientId)).toList();
        }

        @Override
        public List<MedicineOrder> findByStatus(OrderStatus status) {
            return storage.values().stream().filter(o -> o.getStatus() == status).toList();
        }

        @Override
        public List<MedicineOrder> searchByKeyword(String keyword) {
            String low = keyword.toLowerCase();
            return storage.values().stream()
                    .filter(o -> o.getOrderNumber().toLowerCase().contains(low)
                            || o.getDeliveryAddress().toLowerCase().contains(low))
                    .toList();
        }

        @Override
        public boolean updateStatus(Long orderId, OrderStatus newStatus) {
            MedicineOrder order = storage.get(orderId);
            if (order == null) return false;
            order.setStatus(newStatus);
            return true;
        }
    }
}
