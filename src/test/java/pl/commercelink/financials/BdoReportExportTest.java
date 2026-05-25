package pl.commercelink.financials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.warehouse.builtin.BdoReportRow;
import pl.commercelink.warehouse.builtin.BdoReportService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BdoReportExportTest {

    @Mock private BdoReportService service;

    @Test
    void runWritesHeaderLineAndRow() throws Exception {
        when(service.generate(eq("store-1"), any(), any())).thenReturn(List.of(new BdoReportRow(
                "GPU", "DUAL 4070", "MFN-A", 5,
                2100, 2400, 10500L, 12000L,
                "IngramMicro"
        )));

        byte[] bytes = new BdoReportExport(service).run("store-1", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\"Category\";\"Name\";\"MFN\";\"Quantity\";\"Net weight (g)\";\"Gross weight (g)\";\"Total net weight (g)\";\"Total gross weight (g)\";\"Supplier\"");
        assertThat(csv).contains("\"GPU\";\"DUAL 4070\";\"MFN-A\";\"5\";\"2100\";\"2400\";\"10500\";\"12000\";\"IngramMicro\"");
    }
}
