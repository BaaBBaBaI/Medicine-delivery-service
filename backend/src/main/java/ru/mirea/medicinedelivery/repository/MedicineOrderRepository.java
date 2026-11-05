package ru.mirea.medicinedelivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import ru.mirea.medicinedelivery.domain.MedicineOrder;
import ru.mirea.medicinedelivery.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Репозиторий заказов доставки лекарств, фильтрации по спецификациям и аналитических сводок. */
public interface MedicineOrderRepository extends JpaRepository<MedicineOrder, UUID>, JpaSpecificationExecutor<MedicineOrder> {
    boolean existsByOrderNumberIgnoreCase(String orderNumber);
    boolean existsByOrderNumberIgnoreCaseAndIdNot(String orderNumber, UUID id);
    Optional<MedicineOrder> findByOrderNumberIgnoreCase(String orderNumber);
    long countByArchivedFalse();
    long countByArchivedTrue();
    long countByArchivedFalseAndPrescriptionRequiredTrue();
    long countByArchivedFalseAndStatus(OrderStatus status);

    @Query("select coalesce(avg(o.totalAmount), 0) from MedicineOrder o where o.archived = false and o.totalAmount is not null")
    BigDecimal averageAmountForActive();

    @Query("select coalesce(sum(o.totalAmount), 0) from MedicineOrder o where o.archived = false and o.status = ru.mirea.medicinedelivery.domain.OrderStatus.DELIVERED")
    BigDecimal totalDeliveredRevenue();

    @Query("select coalesce(c.fullName, 'Без клиента'), count(o) from MedicineOrder o left join o.customer c " +
           "where o.archived = false group by c.fullName order by count(o) desc")
    List<Object[]> countActiveByCustomer();

    @Query("select o.status, count(o) from MedicineOrder o where o.archived = false group by o.status order by count(o) desc")
    List<Object[]> countActiveByStatus();

    @Query("select o.priorityLevel, count(o) from MedicineOrder o where o.archived = false group by o.priorityLevel order by count(o) desc")
    List<Object[]> countActiveByPriority();
}
