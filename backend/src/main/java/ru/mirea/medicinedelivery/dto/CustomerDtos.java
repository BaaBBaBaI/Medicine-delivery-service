package ru.mirea.medicinedelivery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTO-контракты справочника клиентов аптечной сети. */
public final class CustomerDtos {
    private CustomerDtos() {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotBlank @Pattern(regexp = "^\\+7-\\d{3}-\\d{3}-\\d{2}-\\d{2}$", message = "телефон должен быть в формате +7-999-999-99-99") String phone,
            @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 255) String defaultAddress,
            Boolean active,
            Long version) {
    }

    public record CustomerResponse(
            UUID id,
            String code,
            String fullName,
            String phone,
            String email,
            String defaultAddress,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
