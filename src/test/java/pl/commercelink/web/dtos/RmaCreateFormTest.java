package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.rma.RMAResolutionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RmaCreateFormTest {

    private static OrderItem orderItem(String id, String name, int qty) {
        OrderItem item = new OrderItem();
        item.setItemId(id);
        item.setName(name);
        item.setQty(qty);
        return item;
    }

    private static RmaCreateForm.Item item(String id, boolean selected, String qty, RMAResolutionType resolution, String reason) {
        RmaCreateForm.Item item = new RmaCreateForm.Item();
        item.setItemId(id);
        item.setSelected(selected);
        item.setQty(qty);
        item.setResolution(resolution);
        item.setReason(reason);
        return item;
    }

    private static RmaCreateForm form(String email, RmaCreateForm.Item... items) {
        RmaCreateForm form = new RmaCreateForm();
        form.setOrderId("order-1");
        form.setEmail(email);
        form.setItems(new ArrayList<>(List.of(items)));
        return form;
    }

    private static final Map<String, OrderItem> REPORTABLE = Map.of(
            "i1", orderItem("i1", "Karta graficzna", 1), "i2", orderItem("i2", "Dysk SSD", 2));

    @Test
    void aCompleteSelectionIsValid() {
        // given
        RmaCreateForm form = form("anna@x.pl", item("i1", true, "1", RMAResolutionType.Return, "Artefakty"),
                item("i2", false, "1", null, null));

        // when
        List<RmaCreateForm.Error> errors = form.validate(REPORTABLE, Map.of());

        // then
        assertThat(errors).isEmpty();
        assertThat(form.selectedItems()).extracting(RmaCreateForm.Item::getItemId).containsExactly("i1");
    }

    @Test
    void nothingSelectedIsAPageError() {
        // given
        RmaCreateForm form = form("anna@x.pl", item("i1", false, "1", null, null));

        // when
        List<RmaCreateForm.Error> errors = form.validate(REPORTABLE, Map.of());

        // then
        assertThat(errors).extracting(RmaCreateForm.Error::field, RmaCreateForm.Error::key)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(null, "rma.new.error.noItems"));
    }

    @Test
    void eachSelectedItemIsCheckedInPageOrderThenTheEmail() {
        // given
        RmaCreateForm form = form("anna@gmail", item("i1", true, "0", null, "x".repeat(1001)),
                item("i2", true, "3", RMAResolutionType.Repair, null));

        // when
        List<RmaCreateForm.Error> errors = form.validate(REPORTABLE, Map.of("i2", "e3b0c442-aaaa"));

        // then
        assertThat(errors).extracting(RmaCreateForm.Error::field).containsExactly(
                "items0.qty", "items0.resolution", "items0.reason", "items1.qty", "items1.selected", "email");
        assertThat(errors.get(0).args()).containsExactly(1);
        assertThat(errors.get(0).itemName()).isEqualTo("Karta graficzna");
        assertThat(errors.get(4).key()).isEqualTo("rma.new.error.claimed");
        assertThat(errors.get(4).args()).containsExactly("e3b0c442");
        assertThat(errors.get(5).key()).isEqualTo("rma.new.error.email.invalid");
    }

    @Test
    void aQuantityThatIsNotANumberIsAnError() {
        // given
        RmaCreateForm form = form("anna@x.pl", item("i2", true, "dwa", RMAResolutionType.Return, null));

        // when
        List<RmaCreateForm.Error> errors = form.validate(REPORTABLE, Map.of());

        // then
        assertThat(errors).extracting(RmaCreateForm.Error::field).containsExactly("items0.qty");
    }

    @Test
    void aForeignItemIsAPageError() {
        // given
        RmaCreateForm form = form("anna@x.pl", item("other-order-item", true, "1", RMAResolutionType.Return, null));

        // when
        List<RmaCreateForm.Error> errors = form.validate(REPORTABLE, Map.of());

        // then
        assertThat(errors).extracting(RmaCreateForm.Error::key).containsExactly("rma.new.error.itemsChanged");
    }

    @Test
    void aBlankEmailIsRequired() {
        // given
        RmaCreateForm form = form("  ", item("i1", true, "1", RMAResolutionType.Return, null));

        // when
        List<RmaCreateForm.Error> errors = form.validate(REPORTABLE, Map.of());

        // then
        assertThat(errors).extracting(RmaCreateForm.Error::key).containsExactly("rma.new.error.email.required");
    }

    @Test
    void aRepeatedItemIsAPageError() {
        // given
        RmaCreateForm form = form("anna@x.pl", item("i1", true, "1", RMAResolutionType.Return, null),
                item("i1", true, "1", RMAResolutionType.Return, null));

        // when
        List<RmaCreateForm.Error> errors = form.validate(REPORTABLE, Map.of());

        // then
        assertThat(errors).extracting(RmaCreateForm.Error::field, RmaCreateForm.Error::key)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(null, "rma.new.error.itemsChanged"));
    }

    @Test
    void trailingWhitespaceDoesNotMakeAReasonTooLong() {
        // given
        RmaCreateForm form = form("anna@x.pl", item("i1", true, "1", RMAResolutionType.Return, "x".repeat(1000) + "   "));

        // when
        List<RmaCreateForm.Error> errors = form.validate(REPORTABLE, Map.of());

        // then
        assertThat(errors).isEmpty();
    }
}
