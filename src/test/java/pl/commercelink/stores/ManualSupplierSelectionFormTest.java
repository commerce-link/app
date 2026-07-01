package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualSupplierSelectionFormTest {

    @Test
    void storeFormHoldsManualSelections() {
        // given
        ManualSupplierSelectionForm selection = new ManualSupplierSelectionForm();
        selection.setIdentity("manual:A");
        selection.setEnabled(true);
        StoreForm form = new StoreForm();

        // when
        form.setManualSupplierSelections(java.util.List.of(selection));

        // then
        assertEquals(1, form.getManualSupplierSelections().size());
        assertTrue(form.getManualSupplierSelections().get(0).isEnabled());
    }
}
