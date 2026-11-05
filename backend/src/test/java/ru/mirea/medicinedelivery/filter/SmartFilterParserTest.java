package ru.mirea.medicinedelivery.filter;

import org.junit.jupiter.api.Test;
import ru.mirea.medicinedelivery.domain.DeliveryPriority;
import ru.mirea.medicinedelivery.domain.OrderStatus;
import ru.mirea.medicinedelivery.error.BadRequestException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 3. Тест безопасного разбора поисковой строки и фильтрации заказов доставки лекарств.
 */
class SmartFilterParserTest {
    private final SmartFilterParser parser = new SmartFilterParser();

    @Test
    void parsesMixedRussianQuery() {
        OrderFilter result = parser.parse(
                "Амоксиклав клиент:CL-1001 статус:собирается сумма>=2500,50 рецепт:да приоритет:критический тег:срочно"
        );
        assertThat(result.freeText()).isEqualTo("Амоксиклав");
        assertThat(result.customer()).isEqualTo("CL-1001");
        assertThat(result.status()).isEqualTo(OrderStatus.ASSEMBLING);
        assertThat(result.prescriptionRequired()).isTrue();
        assertThat(result.priority()).isEqualTo(DeliveryPriority.CRITICAL);
        assertThat(result.tag()).isEqualTo("срочно");
        assertThat(result.archived()).isFalse();
        assertThat(result.amounts()).hasSize(1);
        assertThat(result.amounts().getFirst().operator()).isEqualTo(OrderFilter.Operator.GTE);
        assertThat(result.amounts().getFirst().value()).isEqualByComparingTo(new BigDecimal("2500.50"));
    }

    @Test
    void supportsQuotedFreeTextAndArchivedAll() {
        OrderFilter result = parser.parse("\"Инсулин Туджео\" status:in_delivery archived:all");
        assertThat(result.freeText()).isEqualTo("Инсулин Туджео");
        assertThat(result.status()).isEqualTo(OrderStatus.IN_DELIVERY);
        assertThat(result.archived()).isNull();
    }

    @Test
    void rejectsOutOfRangeAmount() {
        assertThatThrownBy(() -> parser.parse("сумма>5000000"))
                .isInstanceOf(BadRequestException.class);
    }
}
