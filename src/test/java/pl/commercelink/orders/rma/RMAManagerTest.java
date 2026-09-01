package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link RMAManager} had no tests before this class. These two pin the gate between a store
 * clerk's checkbox and a real refund leaving the store's account: only items that were actually
 * physically received back may be moved on and refunded.
 */
@ExtendWith(MockitoExtension.class)
class RMAManagerTest {

    @Mock private RMARepository rmaRepository;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private RMALifecycle rmaLifecycle;

    @InjectMocks
    private RMAManager rmaManager;

    @Test
    void returnSelectedItemsIgnoresItemsThatWereNeverReceived() {
        // given: this filter is the gate between a checkbox and real money leaving
        RMAItem received = rmaItem("a", RMAItemStatus.Received);
        RMAItem fresh = rmaItem("b", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(received, fresh));

        // when
        RMAManager.OperationResult result = rmaManager.returnSelectedItems("store-1", "rma-1", List.of("a", "b"));

        // then
        assertEquals(1, result.getRmaItems().size());
        assertEquals("a", result.getRmaItems().get(0).getRmaItemId());
    }

    @Test
    void returnSelectedItemsFailsWhenNothingWasReceived() {
        // given
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(rmaItem("b", RMAItemStatus.New)));

        // when
        RMAManager.OperationResult result = rmaManager.returnSelectedItems("store-1", "rma-1", List.of("b"));

        // then: MarketplaceReturnDecisions.returnAccepted is never reached
        assertTrue(result.isFailure());
    }

    private static RMAItem rmaItem(String rmaItemId, RMAItemStatus status) {
        RMAItem item = new RMAItem();
        item.setRmaItemId(rmaItemId);
        item.setStatus(status);
        return item;
    }
}
