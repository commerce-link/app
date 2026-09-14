package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierConnectionValidatorTest {

    private final SupplierConnectionValidator validator = validatorWith(5);

    private static ProviderField urlField() {
        return new ProviderField("url", "Feed URL", ProviderField.FieldType.URL, true, "https://...");
    }

    private static SupplierConnectionValidator validatorWith(int minIntervalMinutes, String... types) {
        List<SupplierProviderDescriptor> descriptors =
                java.util.Arrays.stream(types).map(SupplierConnectionValidatorTest::descriptorFor).toList();
        SupplierProviderFactory factory = mock(SupplierProviderFactory.class);
        when(factory.availableProviders()).thenReturn(descriptors);
        return new SupplierConnectionValidator(minIntervalMinutes, new SupplierRegistry(factory));
    }

    private static SupplierProviderDescriptor descriptorFor(String type) {
        SupplierInfo info = new SupplierInfo(type, SupplierType.Distributor, 1, "PL",
                new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())), null);
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(info);
        return descriptor;
    }

    private static StoreSupplierConnection connection(String identity, ConnectionMode mode, String label) {
        StoreSupplierConnection connection = new StoreSupplierConnection(identity, mode, true, true);
        connection.setLabel(label);
        return connection;
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

    @Test
    void labelIsRequiredUniqueAndBoundedForOwnConnections() {
        // given
        SupplierConnectionValidator validator = validatorWith(15, "Kosatec");

        // when / then
        assertThat(validator.validateLabel(connection("Kosatec-k7f3a9c2", ConnectionMode.OWN, " "), List.of()))
                .extracting(ErrorMessage::code)
                .containsExactly("store.supplier.connection.error.label.required");
        assertThat(validator.validateLabel(connection("Kosatec-k7f3a9c2", ConnectionMode.OWN, "x".repeat(61)), List.of()))
                .extracting(ErrorMessage::code)
                .containsExactly("store.supplier.connection.error.label.too.long");
        assertThat(validator.validateLabel(connection("Kosatec-k7f3a9c2", ConnectionMode.OWN, "Kosatec"), List.of("kosatec")))
                .extracting(ErrorMessage::code)
                .containsExactly("store.supplier.connection.error.label.taken");
        assertThat(validator.validateLabel(connection("Kosatec", ConnectionMode.GLOBAL, null), List.of())).isEmpty();
        assertThat(validator.validateLabel(connection("Kosatec-k7f3a9c2", ConnectionMode.OWN, "Fine"), List.of("Hurtownia")))
                .isEmpty();
    }

    @Test
    void ownLabelsCannotUseBuiltInOrForeignTypeNames() {
        // given
        SupplierConnectionValidator validator = validatorWith(15, "Kosatec", "Elko");

        // when / then
        assertThat(validator.validateLabel(connection("Kosatec-k7f3a9c2", ConnectionMode.OWN, "Warehouse"), List.of()))
                .extracting(ErrorMessage::code)
                .containsExactly("store.supplier.connection.error.label.reserved");
        assertThat(validator.validateLabel(connection("Kosatec-k7f3a9c2", ConnectionMode.OWN, "Elko"), List.of()))
                .extracting(ErrorMessage::code)
                .containsExactly("store.supplier.connection.error.label.reserved");
        // the connection's own type stays allowed: the modal suggests it for the first instance
        // and every legacy OWN connection already carries it
        assertThat(validator.validateLabel(connection("Kosatec-k7f3a9c2", ConnectionMode.OWN, "Kosatec"), List.of()))
                .isEmpty();
        assertThat(validator.validateLabel(connection("Kosatec", ConnectionMode.OWN, "Kosatec"), List.of()))
                .isEmpty();
    }

    @Test
    void globalConnectionRefusesItsTypeNameAlreadyUsedAsAnotherLabel() {
        // given
        SupplierConnectionValidator validator = validatorWith(15, "Elko");

        // when
        List<ErrorMessage> errors = validator.validateLabel(connection("Elko", ConnectionMode.GLOBAL, null), List.of("elko"));

        // then
        assertThat(errors).extracting(ErrorMessage::code)
                .containsExactly("store.supplier.connection.error.label.taken");
    }

    @Test
    void requiredFieldsAreLookedUpByTheTypeOfATokenedIdentity() {
        // given
        SupplierConnectionValidator validator = validatorWith(15);
        StoreSupplierConnection connection = new StoreSupplierConnection("Kosatec-k7f3a9c2", ConnectionMode.OWN, true, true);
        Map<String, List<ProviderField>> fields = Map.of("Kosatec",
                List.of(new ProviderField("cid", "CID", ProviderField.FieldType.TEXT, true, null)));

        // when
        List<ErrorMessage> errors = validator.validate(true, List.of(connection), fields,
                Map.of("Kosatec-k7f3a9c2", Map.of()), Set.of());

        // then
        assertThat(errors).extracting(ErrorMessage::code).containsExactly("store.supplier.connection.error.requires.field");
    }
}
