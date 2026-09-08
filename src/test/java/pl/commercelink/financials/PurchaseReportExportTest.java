package pl.commercelink.financials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.warehouse.builtin.PurchaseReportRow;
import pl.commercelink.warehouse.builtin.PurchaseReportService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseReportExportTest {

    @Mock private PurchaseReportService service;

    @InjectMocks private PurchaseReportExport export;

    @Test
    void runWritesHeaderLineAndRowWithSupplierFirst() throws Exception {
        // given
        when(service.generate(eq("store-1"), any(), any()))
                .thenReturn(List.of(new PurchaseReportRow("AcmeA", "GPU", "MFN-A", "DUAL 4070", 5)));

        // when
        byte[] bytes = export.run("store-1", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // then
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\"Supplier\";\"Category\";\"MFN\";\"Name\";\"Quantity\"");
        assertThat(csv).contains("\"AcmeA\";\"GPU\";\"MFN-A\";\"DUAL 4070\";\"5\"");
    }
}
