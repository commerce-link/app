package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.details.DeliveryFixtures.*;

/** The allocations form posts every allocation once, in order, so binding never fills gaps with empty allocations. */
class DeliveryAllocationsBindingTest {

    private static final Pattern INPUT = Pattern.compile("<input[^>]*>");
    private static final Pattern NAME = Pattern.compile("\\sname=\"([^\"]+)\"");
    private static final Pattern VALUE = Pattern.compile("\\svalue=\"([^\"]*)\"");

    /** What a browser submits: every named input of the form, a checkbox only when checked. */
    private static MutablePropertyValues submitted(String html) {
        String form = html.substring(html.indexOf("<form id=\"allocationsForm\""), html.indexOf("</form>", html.indexOf("<form id=\"allocationsForm\"")));
        MutablePropertyValues values = new MutablePropertyValues();
        Matcher inputs = INPUT.matcher(form);
        while (inputs.find()) {
            String input = inputs.group();
            Matcher name = NAME.matcher(input);
            if (!name.find() || (input.contains("type=\"checkbox\"") && !input.contains("checked"))) {
                continue;
            }
            Matcher value = VALUE.matcher(input);
            values.addPropertyValue(name.group(1), value.find() ? value.group(1) : "");
        }
        return values;
    }

    @Test
    void renderedAllocationFieldsBindBackWithoutGapsOrDuplicates() {
        // given
        Delivery delivery = withAllocations(partlyReceived(warehouse()),
                orderAllocation("NVIDIA ValueKing RTX Ultra", "5900000000002", "MFN-VALUE-01", 3814.0, 1, false),
                warehouseAllocation("Samsung MirageDrive 2TB NVMe", "5900000000006", "MFN-MIRAGE-01", 635.0, 2),
                orderAllocation("Samsung MirageDrive 2TB NVMe", "5900000000006", "MFN-MIRAGE-01", 635.0, 1, false));
        delivery.getAllocations().get(0).setInAllocation(false);
        String html = DeliveryDetailsTemplates.render(data(delivery, null, Set.of(1)), ADMIN);

        // when
        Map<String, Integer> names = DeliveryDetailsTemplates.fieldNameCounts(
                html.substring(html.indexOf("<form id=\"allocationsForm\""), html.indexOf("</form>", html.indexOf("<form id=\"allocationsForm\""))));
        DeliveryAllocationsForm form = new DeliveryAllocationsForm();
        new WebDataBinder(form, "allocationsForm").bind(submitted(html));

        // then
        assertThat(names).allSatisfy((name, count) -> assertThat(count).as(name).isEqualTo(1));
        assertThat(form.getDeliveryId()).isEqualTo(DELIVERY_ID);
        assertThat(form.getAllocations()).hasSize(3);
        assertThat(form.getAllocations()).allSatisfy(allocation -> assertThat(allocation.getType()).isNotNull());
        assertThat(form.getSelectedWarehouseAllocations()).extracting(Allocation::getMfn).containsExactly("MFN-MIRAGE-01");
        assertThat(form.getSelectedOrderAllocations()).isEmpty();
        assertThat(form.getRemainingAllocations()).hasSize(2);
        assertThat(form.getRemainingAllocations()).filteredOn(Allocation::isInAllocation).hasSize(1);
        assertThat(form.getAllocations().get(0).getType()).isEqualTo(AllocationType.Order);
        assertThat(form.getAllocations().get(0).getKey().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(form.getAllocations().get(1).getUnitCost()).isEqualTo(635.0);
    }
}
