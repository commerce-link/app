package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.WarehouseConfiguration;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseDocumentsFormTest {

    private WarehouseDocumentsForm form(boolean enabled, String warehouseId, String costCenterId) {
        WarehouseDocumentsForm form = new WarehouseDocumentsForm();
        form.setDocumentsEnabled(enabled);
        form.setWarehouseId(warehouseId);
        form.setCostCenterId(costCenterId);
        return form;
    }

    @Test
    void bothIdsAreRequiredWhileDocumentsAreSwitchedOn() {
        // when
        Map<String, String> errors = form(true, " ", null).validate();

        // then
        assertThat(errors).containsExactly(
                Map.entry("warehouseId", "store.warehouse.documents.warehouseId.required"),
                Map.entry("costCenterId", "store.warehouse.documents.costCenterId.required"));
    }

    @Test
    void theIdsMayStayEmptyWhileDocumentsAreSwitchedOff() {
        // when / then
        assertThat(form(false, null, null).validate()).isEmpty();
    }

    @Test
    void savesTrimmedIdsAndKeepsThemWhenDocumentsAreSwitchedOff() {
        // given
        Store store = new Store();
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        Printer printer = new Printer();
        printer.setName("Zebra");
        configuration.setPrinters(new LinkedList<>(List.of(printer)));
        store.setWarehouseConfiguration(configuration);

        // when
        form(false, " MAG-01 ", "KC-01").applyTo(store);

        // then
        assertThat(configuration.isDocumentsGenerationEnabled()).isFalse();
        assertThat(configuration.getWarehouseId()).isEqualTo("MAG-01");
        assertThat(configuration.getCostCenterId()).isEqualTo("KC-01");
        assertThat(configuration.getPrinters()).containsExactly(printer);
    }

    @Test
    void startsFromTheStoredConfigurationOrAnEmptyOne() {
        // given
        Store store = new Store();

        // when
        WarehouseDocumentsForm empty = WarehouseDocumentsForm.from(store);

        // then
        assertThat(empty.isDocumentsEnabled()).isFalse();
        assertThat(empty.getWarehouseId()).isNull();
    }
}
