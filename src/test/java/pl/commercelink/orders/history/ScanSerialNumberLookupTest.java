package pl.commercelink.orders.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanSerialNumberLookupTest {

    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private Warehouse warehouse;
    @Mock private StockQueryService stockQueryService;
    @InjectMocks private ScanSerialNumberLookup lookup;

    @Test
    void keepsOnlyRecordsHoldingTheWholeNumber() {
        // given
        when(orderItemsRepository.findBySerialNo("1234")).thenReturn(List.of(orderItem("1234"), orderItem("AB12345")));
        when(rmaItemsRepository.findBySerialNo("1234")).thenReturn(List.of(rmaItem("X,1234"), rmaItem("12345")));
        when(warehouse.stockQueryService("store-1")).thenReturn(stockQueryService);
        when(stockQueryService.findAllBySerialNo("store-1", "1234")).thenReturn(List.of());

        // when
        SerialNumberMatches matches = lookup.find("store-1", " 1234 ");

        // then
        assertThat(matches.orderItems()).extracting(OrderItem::getSerialNo).containsExactly("1234");
        assertThat(matches.rmaItems()).extracting(RMAItem::getSerialNo).containsExactly("X,1234");
    }

    @Test
    void treatsAMissingRepositoryListAsNoMatch() {
        // given
        when(orderItemsRepository.findBySerialNo("SN-1")).thenReturn(null);
        when(rmaItemsRepository.findBySerialNo("SN-1")).thenReturn(null);
        when(warehouse.stockQueryService("store-1")).thenReturn(stockQueryService);
        when(stockQueryService.findAllBySerialNo("store-1", "SN-1")).thenReturn(List.of());

        // when
        SerialNumberMatches matches = lookup.find("store-1", "SN-1");

        // then
        assertThat(matches.orderItems()).isEmpty();
        assertThat(matches.rmaItems()).isEmpty();
        assertThat(matches.warehouseItems()).isEmpty();
    }

    static OrderItem orderItem(String serials) {
        OrderItem item = new OrderItem();
        item.setSerialNo(serials);
        return item;
    }

    static RMAItem rmaItem(String serials) {
        RMAItem item = new RMAItem();
        item.setSerialNo(serials);
        return item;
    }
}
