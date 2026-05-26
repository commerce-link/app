package pl.commercelink.financials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.warehouse.builtin.ProductWeightOriginComplianceReportRow;
import pl.commercelink.warehouse.builtin.ProductWeightOriginComplianceReportService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductWeightOriginComplianceReportExportTest {

    @Mock private ProductWeightOriginComplianceReportService service;

    @Test
    void runWritesHeaderLineAndRowWithCountryFirst() throws Exception {
        when(service.generate(eq("store-1"), any(), any())).thenReturn(List.of(new ProductWeightOriginComplianceReportRow(
                "Germany", "GPU", "ASUS", "DUAL 4070", "MFN-A", 5,
                2100, 2400, 10500L, 12000L
        )));

        byte[] bytes = new ProductWeightOriginComplianceReportExport(service)
                .run("store-1", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\"Country\";\"Category\";\"Brand\";\"Name\";\"MFN\";\"Quantity\";\"Net weight (g)\";\"Gross weight (g)\";\"Total net weight (g)\";\"Total gross weight (g)\"");
        assertThat(csv).contains("\"Germany\";\"GPU\";\"ASUS\";\"DUAL 4070\";\"MFN-A\";\"5\";\"2100\";\"2400\";\"10500\";\"12000\"");
    }
}
