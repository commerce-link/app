package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the resource names derived from identities that already exist on production. A change in
 * any assertion here means a deployed secret, feed key or schedule would stop being found.
 */
class SupplierIdentityCompatibilityTest {

    @Test
    void secretNameOfALegacyOwnConnectionIsUnchanged() {
        // given
        Store store = new Store();
        store.setStoreId("oh4d5y15it");

        // then
        assertThat(store.getSecretesName("Kosatec")).isEqualTo("oh4d5y15it-kosatec");
        assertThat(store.getSecretesName("IncomGroup")).isEqualTo("oh4d5y15it-incomgroup");
    }

    @Test
    void secretNameOfATokenedOwnConnectionIsAValidSecretsManagerName() {
        // given
        Store store = new Store();
        store.setStoreId("oh4d5y15it");

        // then
        assertThat(store.getSecretesName("Kosatec-k7f3a9c2")).isEqualTo("oh4d5y15it-kosatec-k7f3a9c2")
                .matches("^[A-Za-z0-9/_+=.@-]+$");
    }

    @Test
    void legacyIdentitiesResolveToTheirTypeAndLabel() {
        // when / then
        assertThat(SupplierIdentity.typeOf("Kosatec")).isEqualTo("Kosatec");
        assertThat(SupplierIdentity.typeOf("manual:Asus")).isEqualTo("manual");
        assertThat(SupplierIdentity.legacyLabel("manual:Asus")).isEqualTo("Asus");
    }
}
