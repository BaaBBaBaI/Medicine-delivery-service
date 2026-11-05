package ru.mirea.medicinedelivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTO-контракты заметок диспетчеров-фармацевтов к заказу. */
public final class NoteDtos {
    private NoteDtos() {
    }

    public record NoteRequest(
            @NotBlank @Size(max = 4000) String body,
            boolean pinned,
            Long version) {
    }

    public record NoteResponse(
            UUID id,
            UUID orderId,
            UUID authorId,
            String authorName,
            String body,
            boolean pinned,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
