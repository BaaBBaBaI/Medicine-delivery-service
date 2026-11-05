package ru.mirea.medicinedelivery.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.medicinedelivery.domain.MedicineOrder;
import ru.mirea.medicinedelivery.domain.OrderNote;
import ru.mirea.medicinedelivery.domain.Role;
import ru.mirea.medicinedelivery.domain.UserAccount;
import ru.mirea.medicinedelivery.dto.NoteDtos.NoteRequest;
import ru.mirea.medicinedelivery.dto.NoteDtos.NoteResponse;
import ru.mirea.medicinedelivery.error.ConflictException;
import ru.mirea.medicinedelivery.error.NotFoundException;
import ru.mirea.medicinedelivery.repository.MedicineOrderRepository;
import ru.mirea.medicinedelivery.repository.OrderNoteRepository;
import ru.mirea.medicinedelivery.security.CurrentUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Управляет служебными заметками фармацевтов-диспетчеров к заказам доставки лекарств. */
@Service
public class OrderNoteService {
    private final OrderNoteRepository notes;
    private final MedicineOrderRepository orders;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public OrderNoteService(OrderNoteRepository notes, MedicineOrderRepository orders,
                            CurrentUserService currentUser, AuditService audit) {
        this.notes = notes;
        this.orders = orders;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID orderId) {
        requireOrder(orderId);
        return notes.findByOrderIdOrderByPinnedDescCreatedAtDesc(orderId)
                .stream().map(OrderNoteService::toResponse).toList();
    }

    @Transactional
    public NoteResponse create(UUID orderId, NoteRequest request) {
        MedicineOrder order = requireOrder(orderId);
        UserAccount actor = currentUser.requireEntity();
        OrderNote note = new OrderNote(UUID.randomUUID(), order, actor, request.body().trim(), request.pinned());
        notes.save(note);
        audit.record("NOTE_CREATED", "ORDER_NOTE", note.getId(),
                "Добавлена заметка к заказу " + order.getOrderNumber(), Map.of("orderId", orderId));
        return toResponse(note);
    }

    @Transactional
    public NoteResponse update(UUID orderId, UUID noteId, NoteRequest request) {
        OrderNote note = requireNote(orderId, noteId);
        UserAccount actor = currentUser.requireEntity();
        assertOwnerOrAdmin(note, actor);
        if (request.version() == null || request.version() != note.getVersion()) {
            throw new ConflictException("Заметка уже изменена другим пользователем. Обновите список");
        }
        note.setBody(request.body().trim());
        note.setPinned(request.pinned());
        audit.record("NOTE_UPDATED", "ORDER_NOTE", noteId,
                "Изменена заметка к заказу", Map.of("orderId", orderId));
        return toResponse(note);
    }

    @Transactional
    public void delete(UUID orderId, UUID noteId) {
        OrderNote note = requireNote(orderId, noteId);
        UserAccount actor = currentUser.requireEntity();
        assertOwnerOrAdmin(note, actor);
        notes.delete(note);
        audit.record("NOTE_DELETED", "ORDER_NOTE", noteId,
                "Удалена заметка к заказу", Map.of("orderId", orderId));
    }

    private MedicineOrder requireOrder(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Заказ не найден"));
    }

    private OrderNote requireNote(UUID orderId, UUID noteId) {
        OrderNote note = notes.findById(noteId).orElseThrow(() -> new NotFoundException("Заметка не найдена"));
        if (!note.getOrder().getId().equals(orderId)) throw new NotFoundException("Заметка не найдена");
        return note;
    }

    private static void assertOwnerOrAdmin(OrderNote note, UserAccount actor) {
        if (!note.getAuthor().getId().equals(actor.getId()) && !actor.getRoles().contains(Role.ADMIN)) {
            throw new AccessDeniedException("Изменять или удалять заметку может только её автор или администратор");
        }
    }

    private static NoteResponse toResponse(OrderNote note) {
        return new NoteResponse(
                note.getId(),
                note.getOrder().getId(),
                note.getAuthor().getId(),
                note.getAuthor().getDisplayName(),
                note.getBody(),
                note.isPinned(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                note.getVersion()
        );
    }
}
