package ru.mirea.medicinedelivery.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.medicinedelivery.dto.AnalyticsDtos.AnalyticsResponse;
import ru.mirea.medicinedelivery.dto.AnalyticsDtos.NamedCount;
import ru.mirea.medicinedelivery.repository.MedicineOrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Формирует аналитическую сводку по заказам доставки лекарств. */
@Service
public class AnalyticsService {
    private final MedicineOrderRepository orders;

    public AnalyticsService(MedicineOrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse summary() {
        long total = orders.count();
        long active = orders.countByArchivedFalse();
        long archived = orders.countByArchivedTrue();
        long prescriptionRequired = orders.countByArchivedFalseAndPrescriptionRequiredTrue();
        BigDecimal averageAmount = orders.averageAmountForActive();
        if (averageAmount != null) averageAmount = averageAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deliveredRevenue = orders.totalDeliveredRevenue();
        if (deliveredRevenue != null) deliveredRevenue = deliveredRevenue.setScale(2, RoundingMode.HALF_UP);

        return new AnalyticsResponse(
                total,
                active,
                archived,
                prescriptionRequired,
                averageAmount,
                deliveredRevenue,
                map(orders.countActiveByCustomer()),
                map(orders.countActiveByStatus()),
                map(orders.countActiveByPriority())
        );
    }

    private static List<NamedCount> map(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new NamedCount(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }
}
