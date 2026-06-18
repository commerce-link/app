package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierConfigField;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplierConnectionValidatorTest {

    private final SupplierConnectionValidator validator = new SupplierConnectionValidator();

    @Test
    void globalModeRejectedWhenStoreCannotUseGlobalSuppliers() {
        // when
        List<String> errors = validator.validate(false,
                List.of(new StoreSupplierConnection("Action", ConnectionMode.GLOBAL)),
                Map.of(), Map.of(), Set.of());

        // then
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Action"));
    }

    @Test
    void ownModeAllowedWhenAllRequiredFieldsPresent() {
        // when
        List<String> errors = validator.validate(false,
                List.of(new StoreSupplierConnection("Action", ConnectionMode.OWN)),
                Map.of("Action", List.of(SupplierConfigField.url())),
                Map.of("Action", Map.of("url", "https://feed.example.com")),
                Set.of());

        // then
        assertTrue(errors.isEmpty());
    }

    @Test
    void globalModeAllowedWhenStoreCanUseGlobalSuppliers() {
        // when
        List<String> errors = validator.validate(true,
                List.of(new StoreSupplierConnection("Action", ConnectionMode.GLOBAL)),
                Map.of(), Map.of(), Set.of());

        // then
        assertTrue(errors.isEmpty());
    }

    @Test
    void ownModeRejectedWhenRequiredFieldMissing() {
        // when
        List<String> errors = validator.validate(true,
                List.of(new StoreSupplierConnection("Action", ConnectionMode.OWN)),
                Map.of("Action", List.of(SupplierConfigField.url())),
                Map.of("Action", Map.of("url", "")),
                Set.of());

        // then
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Action"));
    }

    @Test
    void ownModeAllowsBlankPasswordWhenSecretAlreadyStored() {
        // given
        SupplierConfigField password = new SupplierConfigField(
                "password", "Password", SupplierConfigField.FieldType.PASSWORD, true, "");

        // when
        List<String> errors = validator.validate(true,
                List.of(new StoreSupplierConnection("Also", ConnectionMode.OWN)),
                Map.of("Also", List.of(password)),
                Map.of("Also", Map.of("password", "")),
                Set.of("Also"));

        // then
        assertTrue(errors.isEmpty());
    }

    @Test
    void ownModeRejectsBlankPasswordWhenNoStoredSecret() {
        // given
        SupplierConfigField password = new SupplierConfigField(
                "password", "Password", SupplierConfigField.FieldType.PASSWORD, true, "");

        // when
        List<String> errors = validator.validate(true,
                List.of(new StoreSupplierConnection("Also", ConnectionMode.OWN)),
                Map.of("Also", List.of(password)),
                Map.of("Also", Map.of("password", "")),
                Set.of());

        // then
        assertEquals(1, errors.size());
    }
}
