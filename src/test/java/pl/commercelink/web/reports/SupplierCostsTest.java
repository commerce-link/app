package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.SupplierDeliveries;
import pl.commercelink.inventory.supplier.SupplierLabelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierCostsTest {

    @Mock
    private SupplierLabelMap labels;

    @Test
    void rowsShowLabelsAMeterAgainstTheBusiestSupplierAndATotalThatAddsUp() {
        // given
        when(labels.of("Action")).thenReturn("Action Gdańsk");
        when(labels.of("AB")).thenReturn("AB");

        // when
        SupplierCosts costs = SupplierCosts.of(List.of(
                new SupplierDeliveries("Action", 20, 290.9, 46.9),
                new SupplierDeliveries("AB", 10, 347.5, 9.9),
                new SupplierDeliveries(null, 1, 10, 0)), labels);

        // then
        assertThat(costs.rows()).extracting(SupplierCosts.Row::name).containsExactly("Action Gdańsk", "AB", null);
        assertThat(costs.rows().get(0).meter()).isEqualTo(100);
        assertThat(costs.rows().get(1).meter()).isEqualTo(50);
        assertThat(costs.deliveries()).isEqualTo(31);
        assertThat(costs.shipping()).isEqualTo("648,40");
        assertThat(costs.totalCosts()).isEqualTo("705,20");
    }

    @Test
    void noDeliveriesIsEmpty() {
        // then
        assertThat(SupplierCosts.of(List.of(), labels).empty()).isTrue();
    }
}
