package ru.mirea.medicinedelivery.dto;

import java.math.BigDecimal;
import java.util.List;

/** DTO аналитической панели сервиса доставки медикаментов. */
public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record NamedCount(String name, long count) {
    }

    public record AnalyticsResponse(
            long total,
            long active,
            long archived,
            long prescriptionRequiredCount,
            BigDecimal averageAmount,
            BigDecimal deliveredRevenue,
            List<NamedCount> byCustomer,
            List<NamedCount> byStatus,
            List<NamedCount> byPriority) {
    }
}
