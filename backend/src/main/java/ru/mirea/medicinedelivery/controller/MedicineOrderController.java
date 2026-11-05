package ru.mirea.medicinedelivery.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.medicinedelivery.dto.ImportDtos.ImportMode;
import ru.mirea.medicinedelivery.dto.ImportDtos.ImportPreview;
import ru.mirea.medicinedelivery.dto.ImportDtos.ImportResult;
import ru.mirea.medicinedelivery.dto.OrderDtos.BulkRequest;
import ru.mirea.medicinedelivery.dto.OrderDtos.BulkResult;
import ru.mirea.medicinedelivery.dto.OrderDtos.OrderRequest;
import ru.mirea.medicinedelivery.dto.OrderDtos.OrderResponse;
import ru.mirea.medicinedelivery.dto.PageResponse;
import ru.mirea.medicinedelivery.service.CsvMedicineOrderService;
import ru.mirea.medicinedelivery.service.MedicineOrderService;
import ru.mirea.medicinedelivery.web.PageableFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** REST-контроллер основной сущности — заказов доставки лекарств. */
@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
public class MedicineOrderController {
    private final MedicineOrderService orders;
    private final CsvMedicineOrderService csv;
    private final PageableFactory pageableFactory;

    public MedicineOrderController(MedicineOrderService orders,
                                   CsvMedicineOrderService csv,
                                   PageableFactory pageableFactory) {
        this.orders = orders;
        this.csv = csv;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','PHARMACIST','ADMIN')")
    public PageResponse<OrderResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "created,desc") String sort) {
        return PageResponse.from(orders.search(q, pageableFactory.orders(page, size, sort)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER','PHARMACIST','ADMIN')")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(201).body(orders.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public OrderResponse update(@PathVariable UUID id, @Valid @RequestBody OrderRequest request) {
        return orders.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public OrderResponse archive(@PathVariable UUID id) {
        return orders.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public OrderResponse restore(@PathVariable UUID id) {
        return orders.restore(id);
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> permanentDelete(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Confirm-Delete", required = false) String confirmation) {
        orders.permanentDelete(id, confirmation);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return orders.bulk(request);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ImportPreview previewImport(@RequestPart("file") MultipartFile file,
                                       @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.preview(file, mode);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ImportResult applyImport(@RequestPart("file") MultipartFile file,
                                    @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.apply(file, mode);
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public void export(@RequestParam(defaultValue = "") String q, HttpServletResponse response) throws IOException {
        String filename = "medicine-orders-" + LocalDate.now() + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        csv.export(q, response.getOutputStream());
    }
}
