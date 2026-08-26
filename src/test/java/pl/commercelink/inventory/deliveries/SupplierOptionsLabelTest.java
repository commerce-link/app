package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionChoice;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SupplierOptionsLabelTest {

    @Test
    void joinsOptionLabelsWithChoiceLabelsInDeclaredOrder() {
        // given
        List<SupplierOrderOption> options = List.of(
                new SupplierOrderOption("paymentMethod", "Sposób zapłaty", List.of(new SupplierOrderOptionChoice("1.Przelew", "1.Przelew", null)), null, true),
                new SupplierOrderOption("deliveryMethod", "Sposób dostawy", List.of(new SupplierOrderOptionChoice("DPD Kurier", "DPD Kurier", "maks. 30 kg")), null, true));

        // when / then
        assertEquals("Sposób zapłaty: 1.Przelew · Sposób dostawy: DPD Kurier",
                SupplierOptionsLabel.of(options, Map.of("deliveryMethod", "DPD Kurier", "paymentMethod", "1.Przelew")));
    }

    @Test
    void fallsBackToRawValuesAndReturnsNullWhenNothingChosen() {
        // when / then
        assertEquals("deliveryMethod: X", SupplierOptionsLabel.of(List.of(), Map.of("deliveryMethod", "X")));
        assertNull(SupplierOptionsLabel.of(List.of(), Map.of()));
    }
}
