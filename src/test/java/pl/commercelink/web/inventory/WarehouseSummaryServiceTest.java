package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.StockSummary;
import pl.commercelink.warehouse.api.Warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseSummaryServiceTest {

    @Mock
    private Warehouse warehouse;

    @Mock
    private StockQueryService stock;

    @InjectMocks
    private WarehouseSummaryService service;

    @Test
    void secondRequestWithinTheCacheWindowDoesNotScanTheWarehouseAgain() {
        // given
        when(warehouse.stockQueryService("store-1")).thenReturn(stock);
        when(stock.summarizeAvailable("store-1")).thenReturn(new StockSummary(184, 612, 2));

        // when
        StockSummary first = service.summaryFor("store-1");
        StockSummary second = service.summaryFor("store-1");

        // then
        assertThat(first).isEqualTo(new StockSummary(184, 612, 2));
        assertThat(second).isEqualTo(first);
        verify(stock, times(1)).summarizeAvailable("store-1");
    }
}
