package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
        assertTrue(coverage.coveredByAnotherOpenRma(STORE_ID, "oi-1", "rma-new"));
    }

    @Test
    void aRejectedRmaReleasesTheItem() {
        // given
        when(rmaItemsRepository.findByOrderItemId("oi-1")).thenReturn(List.of(rmaItem("rma-a", "oi-1")));
        when(rmaRepository.findById(STORE_ID, "rma-a")).thenReturn(rma("rma-a", RMAStatus.Rejected));

        // when / then
        assertFalse(coverage.coveredByAnotherOpenRma(STORE_ID, "oi-1", "rma-new"));
    }

    @Test
    void theRmaBeingEditedDoesNotCountAsCoverage() {
        // given
        when(rmaItemsRepository.findByOrderItemId("oi-1")).thenReturn(List.of(rmaItem("rma-a", "oi-1")));

        // when / then
        assertFalse(coverage.coveredByAnotherOpenRma(STORE_ID, "oi-1", "rma-a"));
    }

    @Test
    void loadsEachRmaOnceEvenWhenItHoldsSeveralFragmentsOfTheItem() {
        // given: an RMA item split into two fragments points twice at the same RMA
        when(rmaItemsRepository.findByOrderItemId("oi-1"))
                .thenReturn(List.of(rmaItem("rma-a", "oi-1"), rmaItem("rma-a", "oi-1")));
        when(rmaRepository.findById(STORE_ID, "rma-a")).thenReturn(rma("rma-a", RMAStatus.Rejected));

        // when
        coverage.coveredByAnotherOpenRma(STORE_ID, "oi-1", "rma-new");

        // then
        verify(rmaRepository, times(1)).findById(STORE_ID, "rma-a");
    }

    @Test
    void anItemInAnOpenRmaIsClaimedAndARejectedOneIsFree() {
        // given
        when(rmaItemsRepository.findByOrderItemIds(List.of("i1", "i2", "i3")))
                .thenReturn(List.of(rmaItem("open", "i1"), rmaItem("rejected", "i2")));
        when(rmaRepository.findById(STORE_ID, "open")).thenReturn(rma("open", RMAStatus.Approved));
        when(rmaRepository.findById(STORE_ID, "rejected")).thenReturn(rma("rejected", RMAStatus.Rejected));

        // when
        Map<String, String> claims = coverage.claims(STORE_ID, List.of("i1", "i2", "i3"));

        // then
        assertThat(claims).containsExactly(Map.entry("i1", "open"));
    }

    @Test
    void aCompletedRmaReleasesItsItemForANewRma() {
        // given: a repaired item goes back to the client and stays Delivered on the order
        when(rmaItemsRepository.findByOrderItemIds(List.of("i1"))).thenReturn(List.of(rmaItem("done", "i1")));
        when(rmaRepository.findById(STORE_ID, "done")).thenReturn(rma("done", RMAStatus.Completed));

        // when
        Map<String, String> claims = coverage.claims(STORE_ID, List.of("i1"));

        // then
        assertThat(claims).isEmpty();
    }

    @Test
    void everyOpenStatusAndAMissingStatusClaim() {
        // given
        List<RMAStatus> open = Arrays.stream(RMAStatus.values()).filter(s -> !s.isClosed()).toList();
        List<String> ids = new ArrayList<>();
        List<RMAItem> items = new ArrayList<>();
        for (RMAStatus status : open) {
            ids.add("i-" + status);
            items.add(rmaItem("rma-" + status, "i-" + status));
            when(rmaRepository.findById(STORE_ID, "rma-" + status)).thenReturn(rma("rma-" + status, status));
        }
        ids.add("i-none");
        items.add(rmaItem("rma-none", "i-none"));
        when(rmaRepository.findById(STORE_ID, "rma-none")).thenReturn(rma("rma-none", null));
        when(rmaItemsRepository.findByOrderItemIds(ids)).thenReturn(items);

        // when
        Map<String, String> claims = coverage.claims(STORE_ID, ids);

        // then
        assertThat(open).containsExactly(RMAStatus.New, RMAStatus.Approved, RMAStatus.WaitingForItems,
                RMAStatus.ItemsReceived, RMAStatus.Processing);
        assertThat(claims).containsOnlyKeys(ids);
    }

    @Test
    void aCompletedRmaStillCoversTheItemForAddItem() {
        // given: add-item and the marketplace importer keep the broader rule
        when(rmaItemsRepository.findByOrderItemId("oi-1")).thenReturn(List.of(rmaItem("rma-a", "oi-1")));
        when(rmaRepository.findById(STORE_ID, "rma-a")).thenReturn(rma("rma-a", RMAStatus.Completed));

        // when / then
        assertTrue(coverage.coveredByAnotherOpenRma(STORE_ID, "oi-1", "rma-new"));
    }

    @Test
    void anRmaOfAnotherStoreDoesNotClaim() {
        // given: order item ids are not store-scoped, the RMA lookup is
        when(rmaItemsRepository.findByOrderItemIds(List.of("i1"))).thenReturn(List.of(rmaItem("foreign", "i1")));
        when(rmaRepository.findById(STORE_ID, "foreign")).thenReturn(null);

        // when
        Map<String, String> claims = coverage.claims(STORE_ID, List.of("i1"));

        // then
        assertThat(claims).isEmpty();
    }

    @Test
    void noItemsReadNothing() {
        // when
        Map<String, String> claims = coverage.claims(STORE_ID, List.of());

        // then
        assertThat(claims).isEmpty();
        verify(rmaItemsRepository, never()).findByOrderItemIds(List.of());
        verify(rmaRepository, never()).findById(anyString(), anyString());
    }
}
