package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SupplierChoiceTest {

    // The test classpath discovers the `Stub` adapter, so `Stub` is a registered supplier type here.
    private final SupplierChoice choice = new SupplierChoice(new SupplierRegistry(
            new SupplierProviderFactory(new ProviderConfigurationManager(mock(SecretsManager.class)))));

    @Test
    void enabledConnectionIdentityResolvesToItself() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(connection("Acme-k7f3a9c2", true)), "Acme-k7f3a9c2", null);

        // then
        assertThat(resolution.accepted()).isTrue();
        assertThat(resolution.identity()).isEqualTo("Acme-k7f3a9c2");
    }

    @Test
    void disabledConnectionIsRejectedAsUnknown() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(connection("Acme", false)), "Acme", null);

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.unknown");
    }

    @Test
    void customNameUsesTheTypedTextTrimmed() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(connection("Acme", true)), SupplierChoice.CUSTOM, "  HURT-ABC ");

        // then
        assertThat(resolution.accepted()).isTrue();
        assertThat(resolution.identity()).isEqualTo("HURT-ABC");
    }

    @Test
    void blankCustomNameIsRequired() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(), SupplierChoice.CUSTOM, "   ");

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.custom.required");
    }

    @Test
    void customNameWithSpaceIsRejectedAsInvalid() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(), SupplierChoice.CUSTOM, "HURT ABC");

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.custom.invalid");
        assertThat(resolution.errorArgs()).containsExactly("HURT ABC");
    }

    @Test
    void customNameWithSpecialCharacterIsRejectedAsInvalid() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(), SupplierChoice.CUSTOM, "HURT.ABC/1");

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.custom.invalid");
    }

    @Test
    void customNameWithLettersDigitsUnderscoreAndDashIsAccepted() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(), SupplierChoice.CUSTOM, "Hurt_ABC-2ł");

        // then
        assertThat(resolution.accepted()).isTrue();
        assertThat(resolution.identity()).isEqualTo("Hurt_ABC-2ł");
    }

    @Test
    void customNameEqualToAnEnabledConnectionIdentityResolvesToThatConnection() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(connection("Acme-k7f3a9c2", true)), SupplierChoice.CUSTOM, "Acme-k7f3a9c2");

        // then
        assertThat(resolution.accepted()).isTrue();
        assertThat(resolution.identity()).isEqualTo("Acme-k7f3a9c2");
    }

    @Test
    void tokenedIdentityWithoutConnectionIsRejectedAsUnknown() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(connection("Acme", true)), SupplierChoice.CUSTOM, "Bravo-k7f3a9c2");

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.unknown");
    }

    @Test
    void legacyManualPrefixWithoutConnectionIsRejectedAsUnknown() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(), SupplierChoice.CUSTOM, "manual:Asus");

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.unknown");
    }

    @Test
    void registeredSupplierTypeWithoutConnectionIsRejectedAsIntegrated() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(), SupplierChoice.CUSTOM, "stub");

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.integrated");
        assertThat(resolution.errorArgs()).containsExactly("stub");
    }

    @Test
    void registeredSupplierTypeWithOnlyTokenedConnectionsIsRejectedAsIntegrated() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(connection("Stub-k7f3a9c2", true)), SupplierChoice.CUSTOM, "Stub");

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.integrated");
    }

    @Test
    void builtInWarehouseNameIsReserved() {
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(), SupplierChoice.CUSTOM, "warehouse");

        // then
        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.errorCode()).isEqualTo("order.item.assign.supplier.reserved");
        assertThat(resolution.errorArgs()).containsExactly("warehouse");
    }

    @Test
    void unlistedIdentityPostedAsTheChoiceIsTreatedAsACustomName() {
        // The select posts identities; a value outside the list is validated like typed text.
        // when
        SupplierChoice.Resolution resolution = choice.resolve(store(), "HURT-ABC", null);

        // then
        assertThat(resolution.accepted()).isTrue();
        assertThat(resolution.identity()).isEqualTo("HURT-ABC");
    }

    private static StoreSupplierConnection connection(String identity, boolean enabled) {
        StoreSupplierConnection connection = new StoreSupplierConnection(identity, ConnectionMode.OWN);
        connection.setEnabled(enabled);
        return connection;
    }

    private static Store store(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }
}
