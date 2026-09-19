package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierSettingsFormTest {

    private static final List<ProviderField> FIELDS = List.of(
            new ProviderField("login", "Login", FieldType.TEXT, true, null),
            new ProviderField("password", "Hasło", FieldType.PASSWORD, true, null));

    private static SupplierSettingsForm acme(String label) {
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier("Acme");
        form.setLabel(label);
        form.setSettings(new HashMap<>(Map.of("Acme.login", "sklep")));
        return form;
    }

    @Test
    void anOwnConnectionNeedsANameItsAccessDetailsAndAValidSchedule() {
        // given
        SupplierSettingsForm form = acme(" ");
        form.setFeedSchedule("cron(nonsense)");

        // when
        Map<String, String> errors = form.validate(FIELDS, Set.of(), 15);

        // then
        assertThat(errors).containsOnlyKeys("label", "setting-Acme-password", "feedSchedule");
    }

    @Test
    void aSavedSecretMayBeLeftEmpty() {
        // when / then
        assertThat(acme("Acme 2").validate(FIELDS, Set.of("password"), 15)).isEmpty();
    }

    @Test
    void aGlobalConnectionHasNothingOfItsOwnToCheck() {
        // given
        SupplierSettingsForm form = acme(null);
        form.setMode(ConnectionMode.GLOBAL.name());
        form.setFeedSchedule("cron(nonsense)");

        // when / then
        assertThat(form.global()).isTrue();
        assertThat(form.validate(FIELDS, Set.of(), 15)).isEmpty();
    }

    @Test
    void aPriceListNeedsOnlyItsNameEvenWhenTheFormSaysGlobal() {
        // given
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier(SupplierSettingsForm.CSV);
        form.setMode(ConnectionMode.GLOBAL.name());

        // when / then
        assertThat(form.global()).isFalse();
        assertThat(form.validate(null, Set.of(), 15)).containsOnlyKeys("label");
    }

    @Test
    void anUncheckedBoxIsFalseWhileANewSupplierStartsWithBothUses() {
        // when / then
        assertThat(new SupplierSettingsForm().isIncludeInPricing()).isFalse();
        assertThat(SupplierSettingsForm.newSupplier("Acme").isIncludeInPricing()).isTrue();
        assertThat(SupplierSettingsForm.newSupplier("Acme").isIncludeInFulfilment()).isTrue();
    }

    @Test
    void anExistingConnectionFillsTheFormWithoutItsSecrets() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("Acme-abcd1234", ConnectionMode.OWN, true, false);
        connection.setLabel("Acme 2");
        connection.setFeedSchedule("rate(2 hours)");
        connection.setExternalSupplierId("17");

        // when
        SupplierSettingsForm form = SupplierSettingsForm.of(connection, Map.of("login", "sklep", "password", ""), FIELDS);

        // then
        assertThat(form.getProviderName()).isEqualTo("Acme");
        assertThat(form.getLabel()).isEqualTo("Acme 2");
        assertThat(form.getFeedSchedule()).isEqualTo("rate(2 hours)");
        assertThat(form.isIncludeInFulfilment()).isFalse();
        assertThat(form.value("Acme", FIELDS.get(0))).isEqualTo("sklep");
        assertThat(form.value("Acme", FIELDS.get(1))).isNull();
    }

    @Test
    void aPriceListIsFilledAsAPriceList() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("manual-abcd1234", ConnectionMode.MANUAL, true, true);
        connection.setLabel("Hurtownia");
        connection.setEnabled(false);

        // when
        SupplierSettingsForm form = SupplierSettingsForm.of(connection, Map.of(), null);

        // then
        assertThat(form.csv()).isTrue();
        assertThat(form.isEnabled()).isFalse();
    }

    @Test
    void theSelectionCarriesTheIdentityOfAnEditAndTheChosenMode() {
        // given
        SupplierSettingsForm form = acme("Acme 2");
        form.setIncludeInFulfilment(false);

        // when
        SupplierSelectionForm selection = form.toSelection("Acme-abcd1234");

        // then
        assertThat(selection.getIdentity()).isEqualTo("Acme-abcd1234");
        assertThat(selection.getSupplierName()).isEqualTo("Acme");
        assertThat(selection.getMode()).isEqualTo(ConnectionMode.OWN);
        assertThat(selection.isIncludeInPricing()).isTrue();
        assertThat(selection.isIncludeInFulfilment()).isFalse();
    }
}
