package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.rma.RMAItem;

import static org.assertj.core.api.Assertions.assertThat;

class RmaItemFormTest {

    private static RMAItem stored() {
        RMAItem item = new RMAItem();
        item.setRmaId("rma-1");
        item.setRmaItemId("item-1");
        item.setName("RTX 4070");
        item.setQty(2);
        item.setSerialNo("A1,A2");
        item.setReason("Artefacts");
        item.setComment("Box ok");
        item.setVersion(7L);
        return item;
    }

    @Test
    void ofCopiesTheThreeFieldsAndTheVersion() {
        // when
        RmaItemForm form = RmaItemForm.of(stored());

        // then
        assertThat(form.getSerialNo()).isEqualTo("A1,A2");
        assertThat(form.getReason()).isEqualTo("Artefacts");
        assertThat(form.getComment()).isEqualTo("Box ok");
        assertThat(form.getVersion()).isEqualTo("7");
    }

    @Test
    void itemWithoutVersionGetsTheNoVersionMarker() {
        // given
        RMAItem item = stored();
        item.setVersion(null);

        // when / then
        assertThat(RmaItemForm.versionOf(item)).isEqualTo(RmaItemForm.NO_VERSION);
    }

    @Test
    void tooLongNewValuesAreErrorsInFieldOrder() {
        // given
        RmaItemForm form = RmaItemForm.of(stored());
        form.setComment("c".repeat(RmaItemForm.MAX_COMMENT + 1));
        form.setSerialNo("s".repeat(RmaItemForm.MAX_SERIAL + 1));
        form.setReason("r".repeat(RmaItemForm.MAX_REASON + 1));

        // when / then
        assertThat(form.validate(stored())).containsExactly(
                java.util.Map.entry("serialNo", "rma.item.page.error.serialNo"),
                java.util.Map.entry("reason", "rma.item.page.error.reason"),
                java.util.Map.entry("comment", "rma.item.page.error.comment"));
    }

    @Test
    void limitsApplyOnlyToChangedValues() {
        // given
        RMAItem item = stored();
        item.setReason("r".repeat(RmaItemForm.MAX_REASON + 50));
        RmaItemForm form = RmaItemForm.of(item);
        form.setReason(item.getReason());
        form.setComment("New comment");

        // when / then
        assertThat(form.validate(item)).isEmpty();
    }

    @Test
    void textareaLineBreaksDoNotCountAsAChange() {
        // given
        RMAItem item = stored();
        item.setComment("line 1\n" + "c".repeat(RmaItemForm.MAX_COMMENT));
        RmaItemForm form = RmaItemForm.of(item);
        form.setComment("line 1\r\n" + "c".repeat(RmaItemForm.MAX_COMMENT));

        // when / then
        assertThat(form.validate(item)).isEmpty();
    }

    @Test
    void applyTrimsBlanksToNullAndNormalisesSerialNumbers() {
        // given
        RMAItem item = stored();
        RmaItemForm form = new RmaItemForm();
        form.setSerialNo(" B2 , B1,B2,, ");
        form.setReason("   ");
        form.setComment("  Checked  ");

        // when
        form.applyTo(item);

        // then
        assertThat(item.getSerialNo()).isEqualTo("B2,B1");
        assertThat(item.getReason()).isNull();
        assertThat(item.getComment()).isEqualTo("Checked");
        assertThat(item.getQty()).isEqualTo(2);
        assertThat(item.getName()).isEqualTo("RTX 4070");
    }

    @Test
    void valuesAtTheLimitAreAccepted() {
        // given
        RmaItemForm form = RmaItemForm.of(stored());
        form.setSerialNo("s".repeat(RmaItemForm.MAX_SERIAL));
        form.setReason("r".repeat(RmaItemForm.MAX_REASON));
        form.setComment("c".repeat(RmaItemForm.MAX_COMMENT));

        // when / then
        assertThat(form.validate(stored())).isEmpty();
    }

    @Test
    void serialLimitCountsTheStoredFormNotWhatWasTyped() {
        // given: 501 characters typed, 500 once the spaces after the commas are dropped
        RmaItemForm form = RmaItemForm.of(stored());
        form.setSerialNo("a".repeat(249) + ", " + "b".repeat(250));

        // when / then
        assertThat(form.validate(stored())).isEmpty();
    }

    @Test
    void unchangedSerialNumbersTypedWithSpacesAreNotAChange() {
        // given
        RMAItem item = stored();
        item.setSerialNo("s".repeat(RmaItemForm.MAX_SERIAL) + ",T1");
        RmaItemForm form = RmaItemForm.of(item);
        form.setSerialNo("s".repeat(RmaItemForm.MAX_SERIAL) + ", T1");

        // when / then
        assertThat(form.validate(item)).isEmpty();
    }

    @Test
    void savedSerialNumbersAreRemovedFromTheOrderItemWhenPartOfItsQuantityIsReturned() {
        // given: the order item has 3 units and the operator narrows the RMA item to two of them
        RMAItem item = stored();
        RmaItemForm form = new RmaItemForm();
        form.setSerialNo("A, B");
        form.applyTo(item);
        OrderItem orderItem = new OrderItem();
        orderItem.setSerialNo("A,B,C");

        // when: OrdersRMAManager.splitOrderItem on accepting the return or a replacement
        orderItem.removeSerialNumbers(item.getSerialNo());

        // then
        assertThat(orderItem.getSerialNo()).isEqualTo("C");
    }
}
