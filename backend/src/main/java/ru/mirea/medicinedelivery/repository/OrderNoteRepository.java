package ru.mirea.medicinedelivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.medicinedelivery.domain.OrderNote;

import java.util.List;
import java.util.UUID;

/** Репозиторий заметок к заказам доставки лекарств. */
public interface OrderNoteRepository extends JpaRepository<OrderNote, UUID> {
    List<OrderNote> findByOrderIdOrderByPinnedDescCreatedAtDesc(UUID orderId);
}
