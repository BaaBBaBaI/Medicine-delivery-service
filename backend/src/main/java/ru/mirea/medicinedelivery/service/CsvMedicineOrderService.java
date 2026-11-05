package ru.mirea.medicinedelivery.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.medicinedelivery.config.AppProperties;
import ru.mirea.medicinedelivery.domain.Customer;
import ru.mirea.medicinedelivery.domain.MedicineOrder;
import ru.mirea.medicinedelivery.domain.OrderStatus;
import ru.mirea.medicinedelivery.domain.PaymentMethod;
import ru.mirea.medicinedelivery.domain.UserAccount;
import ru.mirea.medicinedelivery.dto.ImportDtos.ImportMode;
import ru.mirea.medicinedelivery.dto.ImportDtos.ImportPreview;
import ru.mirea.medicinedelivery.dto.ImportDtos.ImportResult;
import ru.mirea.medicinedelivery.dto.ImportDtos.RowIssue;
import ru.mirea.medicinedelivery.dto.OrderDtos.OrderRequest;
import ru.mirea.medicinedelivery.dto.OrderDtos.OrderResponse;
import ru.mirea.medicinedelivery.error.BadRequestException;
import ru.mirea.medicinedelivery.filter.MedicineOrderSpecifications;
import ru.mirea.medicinedelivery.filter.SmartFilterParser;
import ru.mirea.medicinedelivery.repository.CustomerRepository;
import ru.mirea.medicinedelivery.repository.MedicineOrderRepository;
import ru.mirea.medicinedelivery.security.CurrentUserService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Сервис защищённого импорта и экспорта заказов доставки лекарств в формате CSV.
 */
@Service
public class CsvMedicineOrderService {
    private static final String[] HEADERS = {
            "orderNumber", "customerCode", "medicationSummary", "deliveryAddress", "contactPhone",
            "totalAmount", "status", "paymentMethod", "prescriptionRequired", "prescriptionNumber",
            "deliveryNotes", "tags"
    };

    private final MedicineOrderRepository orders;
    private final CustomerRepository customers;
    private final MedicineOrderService orderService;
    private final CurrentUserService currentUser;
    private final SmartFilterParser parser;
    private final MedicineOrderMapper mapper;
    private final Validator validator;
    private final AppProperties properties;
    private final AuditService audit;

    public CsvMedicineOrderService(MedicineOrderRepository orders, CustomerRepository customers,
                                   MedicineOrderService orderService, CurrentUserService currentUser,
                                   SmartFilterParser parser, MedicineOrderMapper mapper,
                                   Validator validator, AppProperties properties, AuditService audit) {
        this.orders = orders;
        this.customers = customers;
        this.orderService = orderService;
        this.currentUser = currentUser;
        this.parser = parser;
        this.mapper = mapper;
        this.validator = validator;
        this.properties = properties;
        this.audit = audit;
    }

    /** Выполняет предварительную проверку CSV-файла без изменения базы данных. */
    @Transactional(readOnly = true)
    public ImportPreview preview(MultipartFile file, ImportMode mode) {
        ParsedFile parsed = parse(file, mode == null ? ImportMode.CREATE_ONLY : mode);
        return new ImportPreview(parsed.totalRows(), parsed.rows().size(), parsed.issues().size(), parsed.issues());
    }

    /** Применяет проверенный CSV-файл в выбранном режиме импорта. */
    @Transactional
    public ImportResult apply(MultipartFile file, ImportMode mode) {
        ImportMode effectiveMode = mode == null ? ImportMode.CREATE_ONLY : mode;
        ParsedFile parsed = parse(file, effectiveMode);
        UserAccount actor = currentUser.requireEntity();
        int created = 0;
        int updated = 0;
        int skipped = parsed.issues().size();
        List<RowIssue> runtimeIssues = new ArrayList<>(parsed.issues());

        for (ParsedRow row : parsed.rows()) {
            try {
                MedicineOrder existing = orders.findByOrderNumberIgnoreCase(row.request().orderNumber()).orElse(null);
                if (existing != null && effectiveMode == ImportMode.CREATE_ONLY) {
                    runtimeIssues.add(new RowIssue(row.rowNumber(), "orderNumber", "Заказ с таким номером уже существует"));
                    skipped++;
                    continue;
                }
                orderService.upsertFromImport(existing, row.request(), actor);
                if (existing == null) created++; else updated++;
            } catch (RuntimeException ex) {
                runtimeIssues.add(new RowIssue(row.rowNumber(), "row", safeMessage(ex)));
                skipped++;
            }
        }
        audit.record("ORDER_CSV_IMPORTED", "MEDICINE_ORDER", null,
                "Импортирован CSV с заказами доставки лекарств",
                Map.of("created", created, "updated", updated, "skipped", skipped, "mode", effectiveMode.name()));
        return new ImportResult(parsed.totalRows(), created, updated, skipped, List.copyOf(runtimeIssues));
    }

    /** Экспортирует заказы с учётом текущего поискового фильтра в CSV (UTF-8 с BOM). */
    @Transactional(readOnly = true)
    public void export(String query, OutputStream output) throws IOException {
        output.write("\ufeff".getBytes(StandardCharsets.UTF_8));
        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).get())) {
            int page = 0;
            while (true) {
                var result = orders.findAll(MedicineOrderSpecifications.from(parser.parse(query)),
                        PageRequest.of(page, 500, Sort.by("createdAt").descending().and(Sort.by("orderNumber").ascending())));
                for (MedicineOrder order : result.getContent()) {
                    OrderResponse dto = mapper.toResponse(order);
                    printer.printRecord(
                            safeCell(dto.orderNumber()),
                            safeCell(dto.customerCode()),
                            safeCell(dto.medicationSummary()),
                            safeCell(dto.deliveryAddress()),
                            safeCell(dto.contactPhone()),
                            dto.totalAmount(),
                            dto.status(),
                            dto.paymentMethod(),
                            dto.prescriptionRequired(),
                            safeCell(dto.prescriptionNumber()),
                            safeCell(dto.deliveryNotes()),
                            safeCell(String.join("|", dto.tags()))
                    );
                }
                if (result.isLast()) break;
                page++;
            }
            printer.flush();
        }
        audit.record("ORDER_CSV_EXPORTED", "MEDICINE_ORDER", null,
                "Экспортирована выборка заказов в CSV", Map.of("query", query == null ? "" : query));
    }

    private ParsedFile parse(MultipartFile file, ImportMode mode) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Выберите непустой CSV-файл");
        List<ParsedRow> validRows = new ArrayList<>();
        List<RowIssue> issues = new ArrayList<>();
        Set<String> orderNumbersInFile = new LinkedHashSet<>();
        int total = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csv = CSVParser.parse(reader, format)) {
            validateHeaders(csv.getHeaderMap());
            for (CSVRecord record : csv) {
                total++;
                long rowNumber = record.getRecordNumber() + 1;
                if (total > properties.importLimits().maxRows()) {
                    throw new BadRequestException("CSV содержит больше " + properties.importLimits().maxRows() + " строк");
                }
                try {
                    ParsedRow row = toRow(record, rowNumber);
                    Set<ConstraintViolation<OrderRequest>> violations = validator.validate(row.request());
                    if (!violations.isEmpty()) {
                        for (ConstraintViolation<OrderRequest> violation : violations) {
                            issues.add(new RowIssue(rowNumber, violation.getPropertyPath().toString(), violation.getMessage()));
                        }
                        continue;
                    }
                    if (row.request().prescriptionRequired()
                            && (row.request().prescriptionNumber() == null || row.request().prescriptionNumber().isBlank())) {
                        issues.add(new RowIssue(rowNumber, "prescriptionNumber", "Для рецептурного заказа обязателен номер рецепта"));
                        continue;
                    }
                    String key = row.request().orderNumber().toLowerCase(Locale.ROOT);
                    if (!orderNumbersInFile.add(key)) {
                        issues.add(new RowIssue(rowNumber, "orderNumber", "Дубликат orderNumber внутри CSV-файла"));
                        continue;
                    }
                    if (mode == ImportMode.CREATE_ONLY && orders.existsByOrderNumberIgnoreCase(row.request().orderNumber())) {
                        issues.add(new RowIssue(rowNumber, "orderNumber", "Заказ с таким номером уже существует"));
                        continue;
                    }
                    validRows.add(row);
                } catch (RuntimeException ex) {
                    issues.add(new RowIssue(rowNumber, "row", safeMessage(ex)));
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Не удалось прочитать CSV в кодировке UTF-8");
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Некорректная структура CSV: " + ex.getMessage());
        }
        return new ParsedFile(total, List.copyOf(validRows), List.copyOf(issues));
    }

    private ParsedRow toRow(CSVRecord record, long rowNumber) {
        String orderNumber = required(record, "orderNumber").toUpperCase(Locale.ROOT);
        String customerCode = nullable(record, "customerCode");
        Customer customer = customerCode == null ? null : customers.findByCodeIgnoreCase(customerCode)
                .orElseThrow(() -> new BadRequestException("Клиент не найден по коду: " + customerCode));
        if (customer != null && !customer.isActive()) {
            throw new BadRequestException("Клиент отключён в справочнике: " + customerCode);
        }

        OrderRequest request = new OrderRequest(
                orderNumber,
                customer == null ? null : customer.getId(),
                required(record, "medicationSummary"),
                required(record, "deliveryAddress"),
                required(record, "contactPhone"),
                parseAmount(required(record, "totalAmount")),
                parseStatus(required(record, "status")),
                parsePaymentMethod(required(record, "paymentMethod")),
                parseBoolean(nullable(record, "prescriptionRequired")),
                nullable(record, "prescriptionNumber"),
                nullable(record, "deliveryNotes"),
                parseTags(nullable(record, "tags")),
                null
        );
        return new ParsedRow(rowNumber, request);
    }

    private static void validateHeaders(Map<String, Integer> actual) {
        for (String header : HEADERS) {
            if (!actual.containsKey(header)) {
                throw new BadRequestException("Отсутствует обязательная колонка CSV: " + header);
            }
        }
    }

    private static String required(CSVRecord record, String field) {
        String value = nullable(record, field);
        if (value == null) throw new BadRequestException("Не заполнено обязательное поле " + field);
        return value;
    }

    private static String nullable(CSVRecord record, String field) {
        String value = record.get(field);
        if (value == null) return null;
        value = value.replace("\ufeff", "").trim();
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Некорректное числовое значение totalAmount");
        }
    }

    private static boolean parseBoolean(String value) {
        if (value == null) return false;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "да" -> true;
            case "false", "0", "no", "нет" -> false;
            default -> throw new BadRequestException("Поле prescriptionRequired принимает true/false или да/нет");
        };
    }

    private static OrderStatus parseStatus(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "created", "создан", "новый" -> OrderStatus.CREATED;
            case "confirmed", "подтвержден", "подтверждён" -> OrderStatus.CONFIRMED;
            case "assembling", "собирается", "сборка" -> OrderStatus.ASSEMBLING;
            case "in_delivery", "доставка", "в_доставке" -> OrderStatus.IN_DELIVERY;
            case "delivered", "доставлен" -> OrderStatus.DELIVERED;
            case "cancelled", "canceled", "отменен", "отменён" -> OrderStatus.CANCELLED;
            default -> throw new BadRequestException("Неизвестный статус заказа: " + value);
        };
    }

    private static PaymentMethod parsePaymentMethod(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "cash", "наличные" -> PaymentMethod.CASH;
            case "card_on_delivery", "картой_курьеру" -> PaymentMethod.CARD_ON_DELIVERY;
            case "card_online", "картой_онлайн", "онлайн" -> PaymentMethod.CARD_ONLINE;
            case "sbp", "сбп" -> PaymentMethod.SBP;
            default -> throw new BadRequestException("Неизвестный способ оплаты: " + value);
        };
    }

    private static Set<String> parseTags(String value) {
        if (value == null) return Set.of();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Arrays.stream(value.split("\\|")).map(String::trim).filter(v -> !v.isEmpty())
                .map(v -> v.toLowerCase(Locale.ROOT)).forEach(tags::add);
        return tags;
    }

    private static String safeCell(Object value) {
        if (value == null) return "";
        String cell = String.valueOf(value);
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) return "'" + cell;
        return cell;
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "Некорректная строка" : message;
    }

    private record ParsedRow(long rowNumber, OrderRequest request) { }
    private record ParsedFile(int totalRows, List<ParsedRow> rows, List<RowIssue> issues) { }
}
