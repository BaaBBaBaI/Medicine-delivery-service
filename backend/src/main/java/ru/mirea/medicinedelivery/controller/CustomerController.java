package ru.mirea.medicinedelivery.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.medicinedelivery.dto.CustomerDtos.CustomerRequest;
import ru.mirea.medicinedelivery.dto.CustomerDtos.CustomerResponse;
import ru.mirea.medicinedelivery.service.CustomerService;

import java.util.List;
import java.util.UUID;

/** REST-контроллер справочника клиентов аптечной сети. */
@RestController
@RequestMapping("/api/customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','PHARMACIST','ADMIN')")
    public List<CustomerResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return customers.list(activeOnly);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(201).body(customers.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse setActive(@PathVariable UUID id,
                                      @RequestParam boolean active,
                                      @RequestParam long version) {
        return customers.setActive(id, active, version);
    }
}
