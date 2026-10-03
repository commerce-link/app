package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.Price;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseItemAddFormTest {

    private static WarehouseItemAddForm form(String mfn, String cost, String type, String qty, String status) {
        WarehouseItemAddForm form = new WarehouseItemAddForm();
        form.setManufacturerCode(mfn);
        form.setCost(cost);
        form.setPriceType(type);
        form.setQty(qty);
        form.setStatus(status);
        return form;
    }

    @Test
    void acceptsPolishNumbersAndConvertsGross() {
        // given
        WarehouseItemAddForm net = form("X1", "1 243,50", "net", "2", "New");
        WarehouseItemAddForm nbsp = form("X1", "1 243,50", "net", "2", "New");
        WarehouseItemAddForm gross = form("X1", "123", "gross", "1", "Allocation");

        // when / then
        assertThat(net.validate(false)).isEmpty();
        assertThat(net.netCost()).isEqualTo(1243.5);
        assertThat(nbsp.validate(false)).isEmpty();
        assertThat(nbsp.netCost()).isEqualTo(1243.5);
        assertThat(gross.netCost()).isEqualTo(Price.fromGross(123).netValue());
    }

    @Test
    void namesEveryBadField() {
        // when / then
        assertThat(form(" ", "abc", "net", "0", "Delivered").validate(false))
                .containsOnlyKeys("manufacturerCode", "cost", "qty", "status");
        assertThat(form("X1", "-1", "net", "1.5", "New").validate(false)).containsOnlyKeys("cost", "qty");
    }

    @Test
    void productDataIsRequiredOnlyForAnUnknownCode() {
        // given
        WarehouseItemAddForm form = form("X1", "10", "net", "1", "New");

        // when / then
        assertThat(form.validate(false)).isEmpty();
        assertThat(form.validate(true)).containsOnlyKeys("name", "ean");
    }
}
