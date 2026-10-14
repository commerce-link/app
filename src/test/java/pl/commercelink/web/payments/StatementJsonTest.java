package pl.commercelink.web.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.commercelink.payments.statements.Allocation;
import pl.commercelink.payments.statements.BankOperation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StatementJsonTest {

    private final StatementJson json = new StatementJson(new ObjectMapper());

    @Test
    void operationsComeBackAsTheyWereSent() {
        // given
        BankOperation operation = new BankOperation(12, LocalDate.of(2026, 10, 7), new BigDecimal("1249.00"),
                "Zamowienie 3f9a2c1e", "JAN KOWALSKI", "83102018110000030201459921", "WB-7F3K9Q2M2A");

        // when
        List<BankOperation> read = json.operations(json.operations(List.of(operation)));

        // then
        assertThat(read).containsExactly(operation);
    }

    @Test
    void allocationsTravelInGrosze() {
        // given
        Map<String, List<Allocation>> allocations = Map.of("WB-1",
                List.of(new Allocation("order-1", new BigDecimal("649.00"), new BigDecimal("64.90"))));

        // when
        String written = json.allocations(allocations);

        // then
        assertThat(written).contains("\"amount\":64900").contains("\"fee\":6490");
        assertThat(json.allocations(written)).isEqualTo(allocations);
    }

    @Test
    void escapesWhatWouldEndAScriptElement() {
        // when
        String written = json.write(List.of("</script><b>&"));

        // then
        assertThat(written).doesNotContain("<").doesNotContain(">").doesNotContain("&");
        assertThat(json.strings(written)).containsExactly("</script><b>&");
    }

    @Test
    void dropsWhatCannotBeRead() {
        // when / then
        assertThat(json.operations("not json")).isEmpty();
        assertThat(json.operations("[{\"line\":1,\"date\":\"wczoraj\",\"amount\":100,\"number\":\"WB-1\"}]")).isEmpty();
        assertThat(json.allocations("{")).isEmpty();
    }
}
