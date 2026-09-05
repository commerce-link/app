package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenRmaCoverageTest {

    private static final String STORE_ID = "store-1";

    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private RMARepository rmaRepository;

    @InjectMocks
    private OpenRmaCoverage coverage;

    private static RMAItem rmaItem(String rmaId, String orderItemId) {
        RMAItem item = new RMAItem();
        item.setRmaId(rmaId);
        item.setItemId(orderItemId);
        return item;
    }

    private static RMA rma(String rmaId, RMAStatus status) {
        RMA rma = new RMA(STORE_ID);
        rma.setRmaId(rmaId);
        rma.setStatus(status);
        return rma;
    }

    @Test
    void anOpenRmaHoldingTheItemCoversIt() {
        // given
        when(rmaItemsRepository.findByOrderItemId("oi-1")).thenReturn(List.of(rmaItem("rma-a", "oi-1")));
        when(rmaRepository.findById(STORE_ID, "rma-a")).thenReturn(rma("rma-a", RMAStatus.WaitingForItems));

        // when / then
        assertTrue(coverage.coversOrderItem(STORE_ID, "oi-1", "rma-new"));
    }

    @Test
    void aRejectedRmaReleasesTheItem() {
        // given
        when(rmaItemsRepository.findByOrderItemId("oi-1")).thenReturn(List.of(rmaItem("rma-a", "oi-1")));
        when(rmaRepository.findById(STORE_ID, "rma-a")).thenReturn(rma("rma-a", RMAStatus.Rejected));

        // when / then
        assertFalse(coverage.coversOrderItem(STORE_ID, "oi-1", "rma-new"));
    }

    @Test
    void theRmaBeingEditedDoesNotCountAsCoverage() {
        // given
        when(rmaItemsRepository.findByOrderItemId("oi-1")).thenReturn(List.of(rmaItem("rma-a", "oi-1")));

        // when / then
        assertFalse(coverage.coversOrderItem(STORE_ID, "oi-1", "rma-a"));
    }

    @Test
    void loadsEachRmaOnceEvenWhenItHoldsSeveralFragmentsOfTheItem() {
        // given: an RMA item split into two fragments points twice at the same RMA
        when(rmaItemsRepository.findByOrderItemId("oi-1"))
                .thenReturn(List.of(rmaItem("rma-a", "oi-1"), rmaItem("rma-a", "oi-1")));
        when(rmaRepository.findById(STORE_ID, "rma-a")).thenReturn(rma("rma-a", RMAStatus.Rejected));

        // when
        coverage.coversOrderItem(STORE_ID, "oi-1", "rma-new");

        // then
        verify(rmaRepository, times(1)).findById(STORE_ID, "rma-a");
    }
}
