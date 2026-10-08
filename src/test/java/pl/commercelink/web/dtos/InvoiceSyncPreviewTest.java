package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.InvoicePaymentSync;
import pl.commercelink.web.dtos.InvoiceSyncPreview.MatchState;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceSyncPreviewTest {

    @Test
    void rowStateFollowsTheChosenPositionNotTheAutomaticMatch() {
        // given
        InvoiceSyncPreview preview = preview(
                mapping("MFN-1", 100.00, "p1"),
                mapping("MFN-2", 49.99, "p2"),
                mapping("MFN-3", 80.00, "p3"),
                mapping("MFN-4", 10.00, null));

        // when / then
        assertThat(preview.stateOf(preview.getMappings().get(0))).isEqualTo(MatchState.EXACT);
        assertThat(preview.stateOf(preview.getMappings().get(1))).isEqualTo(MatchState.CLOSE);
        assertThat(preview.stateOf(preview.getMappings().get(2))).isEqualTo(MatchState.DIFFERENT);
        assertThat(preview.stateOf(preview.getMappings().get(3))).isEqualTo(MatchState.UNASSIGNED);
        assertThat(preview.count(MatchState.EXACT)).isEqualTo(1);
        assertThat(preview.getAssignedItemCount()).isEqualTo(3);
        assertThat(preview.isAllExact()).isFalse();
    }

    @Test
    void extraCostWithoutAPositionIsNoCostOnlyWhenItIsZero() {
        // given
        InvoiceSyncPreview preview = preview();
        preview.setShippingCost(0.0);
        preview.setPaymentCost(12.0);

        // when / then
        assertThat(preview.getShippingCostState()).isEqualTo(MatchState.NO_COST);
        assertThat(preview.getPaymentCostState()).isEqualTo(MatchState.UNASSIGNED);
    }

    @Test
    void extraCostComparesTheWholePositionValue() {
        // given
        InvoiceSyncPreview preview = preview();
        preview.setShippingCost(60.0);
        preview.setShippingCostPositionId("p4");

        // when / then
        assertThat(preview.getShippingCostState()).isEqualTo(MatchState.EXACT);
    }

    @Test
    void positionsNoRowChoseAreListedWithTheirValue() {
        // given
        InvoiceSyncPreview preview = preview(mapping("MFN-1", 100.00, "p1"), mapping("MFN-2", 50.00, "p1"));
        preview.setShippingCostPositionId("p4");

        // when / then
        assertThat(preview.getUnassignedOptions()).extracting(InvoiceSyncPreview.Option::getId).containsExactly("p2", "p3");
        assertThat(preview.getUnassignedNet()).isEqualTo(250.0);
        assertThat(preview.getAssignedNet()).isEqualTo(260.0);
    }

    @Test
    void paymentEffectComesFromTheRuleTheSaveUses() {
        // given
        InvoiceSyncPreview preview = preview();
        preview.setInvoicePaid(false);
        preview.setDeliveryPaid(true);
        preview.setDeliveryPaymentsCount(2);

        // when / then
        assertThat(preview.getPaymentSync()).isEqualTo(InvoicePaymentSync.REMOVE);
    }

    @Test
    void invoiceAmountsCarryTheInvoiceCurrency() {
        // given
        InvoiceSyncPreview preview = preview();
        preview.setCurrency("EUR");

        // when / then
        assertThat(preview.invoiceMoney(1234.5)).isEqualTo("1 234,50 EUR");
        assertThat(preview.money(1234.5)).isEqualTo("1\u00a0234,50 PLN");
        assertThat(preview.date("2026-10-22")).isEqualTo("22.10.2026");
    }

    private static InvoiceSyncPreview preview(InvoiceSyncPreview.Mapping... mappings) {
        InvoiceSyncPreview preview = new InvoiceSyncPreview();
        preview.setCurrency("PLN");
        preview.setOptions(List.of(option("p1", 2, 100.00), option("p2", 4, 50.00), option("p3", 1, 50.00), option("p4", 1, 60.00)));
        preview.setMappings(List.of(mappings));
        return preview;
    }

    private static InvoiceSyncPreview.Option option(String id, int qty, double priceNet) {
        InvoiceSyncPreview.Option option = new InvoiceSyncPreview.Option();
        option.setId(id);
        option.setName("Position " + id);
        option.setQty(qty);
        option.setPriceNet(priceNet);
        option.setTotalNet(qty * priceNet);
        return option;
    }

    private static InvoiceSyncPreview.Mapping mapping(String mfn, double unitCost, String positionId) {
        InvoiceSyncPreview.Mapping mapping = new InvoiceSyncPreview.Mapping();
        mapping.setMfn(mfn);
        mapping.setQty(1);
        mapping.setUnitCost(unitCost);
        mapping.setSelectedPositionId(positionId);
        return mapping;
    }
}
