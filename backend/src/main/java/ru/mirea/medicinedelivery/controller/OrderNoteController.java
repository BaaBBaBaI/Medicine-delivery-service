package ru.mirea.medicinedelivery.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.medicinedelivery.dto.NoteDtos.NoteRequest;
import ru.mirea.medicinedelivery.dto.NoteDtos.NoteResponse;
import ru.mirea.medicinedelivery.service.OrderNoteService;

import java.util.List;
import java.util.UUID;

/** REST-контроллер служебных заметок к заказам доставки лекарств. */
@RestController
@RequestMapping("/api/orders/{orderId}/notes")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
public class OrderNoteController {
    private final OrderNoteService notes;

    public OrderNoteController(OrderNoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    public List<NoteResponse> list(@PathVariable UUID orderId) {
        return notes.list(orderId);
    }

    @PostMapping
    public ResponseEntity<NoteResponse> create(@PathVariable UUID orderId,
                                               @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(201).body(notes.create(orderId, request));
    }

    @PutMapping("/{noteId}")
    public NoteResponse update(@PathVariable UUID orderId,
                               @PathVariable UUID noteId,
                               @Valid @RequestBody NoteRequest request) {
        return notes.update(orderId, noteId, request);
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(@PathVariable UUID orderId, @PathVariable UUID noteId) {
        notes.delete(orderId, noteId);
        return ResponseEntity.noContent().build();
    }
}
