package pl.commercelink.web.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.payments.statements.Allocation;
import pl.commercelink.payments.statements.BankOperation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The reconciliation page keeps the statement's operations and the operator's choices in the page itself (nothing is
 * stored on the server), as JSON in form fields and data attributes. Amounts travel in grosze, so the page's script
 * adds them up exactly. Whatever comes back is checked again when booking: the operator could book any payment by hand
 * anyway, so a changed field gains nothing, but a malformed one is dropped instead of failing the page.
 */
@Component
@RequiredArgsConstructor
public class StatementJson {

    private final ObjectMapper objectMapper;

    public record OperationJson(int line, String date, long amount, String title, String sender, String senderAccount,
                                String number) {
    }

    public record AllocationJson(String orderId, long amount, long fee) {
    }

    public String operations(List<BankOperation> operations) {
        return write(operations.stream().map(operation -> new OperationJson(operation.line(), operation.date().toString(),
                grosze(operation.amount()), operation.title(), operation.sender(), operation.senderAccount(),
                operation.number())).toList());
    }

    public List<BankOperation> operations(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<OperationJson> read = objectMapper.readValue(json, new TypeReference<>() {
            });
            return read.stream().map(StatementJson::operation).filter(Objects::nonNull).toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public String allocations(Map<String, List<Allocation>> allocations) {
        Map<String, List<AllocationJson>> json = new LinkedHashMap<>();
        allocations.forEach((number, list) -> json.put(number, list.stream()
                .map(allocation -> new AllocationJson(allocation.orderId(), grosze(allocation.amount()), grosze(allocation.fee())))
                .toList()));
        return write(json);
    }

    public Map<String, List<Allocation>> allocations(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            Map<String, List<AllocationJson>> read = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, List<Allocation>> allocations = new LinkedHashMap<>();
            read.forEach((number, list) -> {
                if (list != null) {
                    allocations.put(number, list.stream()
                            .filter(allocation -> allocation != null && StringUtils.isNotBlank(allocation.orderId()))
                            .map(allocation -> new Allocation(allocation.orderId(), money(allocation.amount()),
                                    money(allocation.fee())))
                            .toList());
                }
            });
            return allocations;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    public List<String> strings(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<String> read = objectMapper.readValue(json, new TypeReference<>() {
            });
            return read.stream().filter(Objects::nonNull).toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    /**
     * JSON safe to put into a script element as it is: client names and titles come from customers and banks, and a
     * "&lt;/script&gt;" in one of them would otherwise end the element. The escapes are still the same JSON.
     */
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The reconciliation page data could not be written", e);
        }
    }

    public static long grosze(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    public static BigDecimal money(long grosze) {
        return BigDecimal.valueOf(grosze, 2);
    }

    private static BankOperation operation(OperationJson json) {
        if (json == null || StringUtils.isBlank(json.number()) || json.amount() == 0) {
            return null;
        }
        try {
            return new BankOperation(json.line(), LocalDate.parse(json.date()), money(json.amount()),
                    StringUtils.defaultString(json.title()), StringUtils.defaultString(json.sender()),
                    StringUtils.defaultString(json.senderAccount()), json.number().trim());
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }
}
