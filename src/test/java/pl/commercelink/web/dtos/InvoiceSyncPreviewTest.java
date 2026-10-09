package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        assertThat(preview.getChangedItemCount()).isEqualTo(2);
    }

    @Test
    void matchingRowsAreNotCountedAboveTheTable() {
        // given
        InvoiceSyncPreview preview = preview(mapping("MFN-1", 100.00, "p1"), mapping("MFN-2", 50.00, "p2"));

        // when / then
        assertThat(preview.getCountedStates()).doesNotContain(MatchState.EXACT);
        assertThat(preview.isAnyCounted()).isFalse();
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
        preview.setDeliveryPayments(List.of(new InvoiceSyncPreview.PaymentLine(100.0, "FV/1"),
                new InvoiceSyncPreview.PaymentLine(20.0, null)));

        // when / then
        assertThat(preview.getPaymentSync()).isEqualTo(InvoicePaymentSync.REMOVE);
    }

    @ParameterizedTest
    @CsvSource({
            "15.00, 15.004, EXACT",
            "15.00, 14.996, EXACT",
            "15.00, 15.01, CLOSE",
            "15.00, 14.99, CLOSE",
            "15.00, 15.0149, CLOSE",
            "15.00, 14.9851, CLOSE",
            "15.00, 15.015, DIFFERENT",
            "15.00, 14.985, DIFFERENT",
            "15.00, 15.02, DIFFERENT",
            "15.00, 0.00, DIFFERENT"
    })
    void stateBandsAreHalfAGroszAroundEqualAndAroundOneGroszEitherWay(double invoiceAmount, double deliveryAmount, MatchState expected) {
        // when / then
        assertThat(MatchState.compare(invoiceAmount, deliveryAmount)).isEqualTo(expected);
    }

    @Test
    void additionalCostEqualToTheChosenLineIsExact() {
        // given
        InvoiceSyncPreview preview = preview();
        preview.setPaymentCost(50.004);
        preview.setPaymentCostPositionId("p3");

        // when / then
        assertThat(preview.getPaymentCostState()).isEqualTo(MatchState.EXACT);
    }

    @Test
    void dueDateIsNoEffectWithoutTheDaysTheSaveStores() {
        // given
        InvoiceSyncPreview preview = preview();
        preview.setInvoicePaymentToDate("2026-10-22");
        preview.setDeliveryPaymentDueDate(null);

        // when / then
        assertThat(preview.isPaymentDueDateDiffers()).isFalse();
    }

    @Test
    void blankInvoiceShortcutIsNoCounterpartyChange() {
        // given
        InvoiceSyncPreview preview = preview();
        preview.setInvoiceShortcut(" ");
        preview.setDeliveryProvider("AB");

        // when / then
        assertThat(preview.isShortcutDiffers()).isFalse();
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
