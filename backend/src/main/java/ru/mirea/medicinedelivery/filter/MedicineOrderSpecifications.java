package ru.mirea.medicinedelivery.filter;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.medicinedelivery.domain.Customer;
import ru.mirea.medicinedelivery.domain.MedicineOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Создаёт параметризованную JPA Specification из разобранного фильтра заказов. */
public final class MedicineOrderSpecifications {
    private MedicineOrderSpecifications() {
    }

    public static Specification<MedicineOrder> from(OrderFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            query.distinct(true);

            if (filter.archived() != null) {
                predicates.add(cb.equal(root.get("archived"), filter.archived()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.prescriptionRequired() != null) {
                predicates.add(cb.equal(root.get("prescriptionRequired"), filter.prescriptionRequired()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priorityLevel"), filter.priority()));
            }

            Join<MedicineOrder, Customer> customerJoin = root.join("customer", JoinType.LEFT);
            if (filter.customer() != null) {
                String pattern = "%" + escape(filter.customer()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(customerJoin.get("code")), pattern, '\\'),
                        cb.like(cb.lower(customerJoin.get("fullName")), pattern, '\\')));
            }
            if (filter.tag() != null) {
                Join<MedicineOrder, String> tagJoin = root.joinSet("tags", JoinType.LEFT);
                predicates.add(cb.equal(cb.lower(tagJoin), filter.tag().toLowerCase(Locale.ROOT)));
            }
            if (filter.freeText() != null && !filter.freeText().isBlank()) {
                String like = "%" + escape(filter.freeText()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), like, '\\'),
                        cb.like(cb.lower(root.get("medicationSummary")), like, '\\'),
                        cb.like(cb.lower(root.get("deliveryAddress")), like, '\\'),
                        cb.like(cb.lower(root.get("contactPhone")), like, '\\'),
                        cb.like(cb.lower(root.get("prescriptionNumber")), like, '\\'),
                        cb.like(cb.lower(root.get("deliveryNotes")), like, '\\'),
                        cb.like(cb.lower(customerJoin.get("code")), like, '\\'),
                        cb.like(cb.lower(customerJoin.get("fullName")), like, '\\')));
            }
            filter.amounts().forEach(constraint -> predicates.add(switch (constraint.operator()) {
                case LT -> cb.lessThan(root.get("totalAmount"), constraint.value());
                case LTE -> cb.lessThanOrEqualTo(root.get("totalAmount"), constraint.value());
                case EQ -> cb.equal(root.get("totalAmount"), constraint.value());
                case GTE -> cb.greaterThanOrEqualTo(root.get("totalAmount"), constraint.value());
                case GT -> cb.greaterThan(root.get("totalAmount"), constraint.value());
            }));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escape(String value) {
        return value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
