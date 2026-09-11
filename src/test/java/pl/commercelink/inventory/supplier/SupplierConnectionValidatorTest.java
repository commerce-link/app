package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplierConnectionValidatorTest {

    private final SupplierConnectionValidator validator = new SupplierConnectionValidator(5);

    private static ProviderField urlField() {
        return new ProviderField("url", "Feed URL", ProviderField.FieldType.URL, true, "https://...");
    }

    @Test
    void globalModeRejectedWhenStoreCannotUseGlobalSuppliers() {
        // when
        List<ErrorMessage> errors = validator.validate(false,
                List.of(new StoreSupplierConnection("Action", ConnectionMode.GLOBAL)),
                Map.of(), Map.of(), Set.of());

        // then
        assertEquals(1, errors.size());
        assertEquals("Action", errors.get(0).args()[0]);
    }

    @Test
    void ownModeAllowedWhenAllRequiredFieldsPresent() {
        // when
        List<ErrorMessage> errors = validator.validate(false,
                List.of(new StoreSupplierConnection("Action", ConnectionMode.OWN)),
                Map.of("Action", List.of(urlField())),
                Map.of("Action", Map.of("url", "https://feed.example.com")),
                Set.of());

        // then
        assertTrue(errors.isEmpty());
    }

    @Test
    void globalModeAllowedWhenStoreCanUseGlobalSuppliers() {
        // when
        List<ErrorMessage> errors = validator.validate(true,
                List.of(new StoreSupplierConnection("Action", ConnectionMode.GLOBAL)),
                Map.of(), Map.of(), Set.of());

        // then
        assertTrue(errors.isEmpty());
    }

    @Test
    void ownModeRejectedWhenRequiredFieldMissing() {
        // when
        List<ErrorMessage> errors = validator.validate(true,
                List.of(new StoreSupplierConnection("Action", ConnectionMode.OWN)),
                Map.of("Action", List.of(urlField())),
                Map.of("Action", Map.of("url", "")),
                Set.of());

        // then
        assertEquals(1, errors.size());
        assertEquals("Action", errors.get(0).args()[0]);
    }

    @Test
    void ownModeAllowsBlankPasswordWhenSecretAlreadyStored() {
        // given
        ProviderField password = new ProviderField(
                "password", "Password", ProviderField.FieldType.PASSWORD, true, "");

        // when
        List<ErrorMessage> errors = validator.validate(true,
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
        ProviderField password = new ProviderField(
                "password", "Password", ProviderField.FieldType.PASSWORD, true, "");

        // when
        List<ErrorMessage> errors = validator.validate(true,
                List.of(new StoreSupplierConnection("Also", ConnectionMode.OWN)),
                Map.of("Also", List.of(password)),
                Map.of("Also", Map.of("password", "")),
                Set.of());

        // then
        assertEquals(1, errors.size());
    }

    private static StoreSupplierConnection ownWithSchedule(String schedule) {
        StoreSupplierConnection connection = new StoreSupplierConnection("Action", ConnectionMode.OWN);
        connection.setFeedSchedule(schedule);
        return connection;
    }

    @Test
    void ownModeAcceptsValidFeedSchedule() {
        // when
        List<ErrorMessage> errors = validator.validate(true,
                List.of(ownWithSchedule("0/30 9-17 * * ? *")), Map.of(), Map.of(), Set.of());

        // then
        assertTrue(errors.isEmpty());
    }

    @Test
    void ownModeRejectsMalformedFeedSchedule() {
        // when
        List<ErrorMessage> errors = validator.validate(true,
                List.of(ownWithSchedule("every 5 minutes")), Map.of(), Map.of(), Set.of());

        // then
        assertEquals(1, errors.size());
        assertEquals("store.supplier.connection.error.invalid.schedule", errors.get(0).code());
        assertEquals("Action", errors.get(0).args()[0]);
        assertEquals("every 5 minutes", errors.get(0).args()[1]);
    }

    @Test
    void ownModeRejectsFeedScheduleBelowTheFloor() {
        // when
        List<ErrorMessage> errors = validator.validate(true,
                List.of(ownWithSchedule("0/2 * * * ? *")), Map.of(), Map.of(), Set.of());

        // then
        assertEquals(1, errors.size());
        assertEquals("store.supplier.connection.error.schedule.too.frequent", errors.get(0).code());
        assertEquals(5, errors.get(0).args()[1]);
    }

    @Test
    void globalModeIgnoresFeedSchedule() {
        // given
        StoreSupplierConnection global = new StoreSupplierConnection("Action", ConnectionMode.GLOBAL);
        global.setFeedSchedule("nonsense");

        // when
        List<ErrorMessage> errors = validator.validate(true, List.of(global), Map.of(), Map.of(), Set.of());

        // then
        assertTrue(errors.isEmpty());
    }
}
