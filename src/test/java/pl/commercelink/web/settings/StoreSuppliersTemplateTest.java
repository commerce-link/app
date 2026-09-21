package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;
import pl.commercelink.web.dtos.SupplierAdminSettingsForm;
import pl.commercelink.web.dtos.SupplierSettingsForm;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSuppliersTemplateTest {

    private static final String PATH = "/dashboard/store/suppliers";
    private static final LocalDateTime FEED = LocalDateTime.of(2026, 9, 16, 21, 52);
    private static final List<ProviderField> ACME_FIELDS = List.of(
            new ProviderField("login", "Login", FieldType.TEXT, true, null),
            new ProviderField("password", "Hasło", FieldType.PASSWORD, true, null));

    private static SupplierConnectionView connection(String identity, String label, ConnectionMode mode, boolean enabled,
                                                     LocalDateTime feed, boolean known) {
        boolean manual = mode == ConnectionMode.MANUAL;
        return new SupplierConnectionView(identity, manual ? null : identity.split("-")[0], label, mode, true, true, enabled,
                feed, null, null, null, known);
    }

    private static String list(List<SupplierView> suppliers, SupplierAdminSettingsForm adminForm) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, PATH));
        variables.put("navigation", null);
        variables.put("suppliers", suppliers);
        Map<String, String> messages = new HashMap<>();
        suppliers.forEach(supplier -> messages.put(supplier.identity(), "Skutek usunięcia " + supplier.title()));
        variables.put("removeMessages", messages);
        variables.put("newSupplierHref", PATH + "/new");
        variables.put("adminForm", adminForm);
        variables.put("errors", Map.of());
        variables.put("failure", null);
        variables.put("adminFormAction", PATH);
        return SettingsTemplateRenderer.render("store-suppliers", variables);
    }

    private static Map<String, Object> subpage(SupplierSettingsForm form, boolean editing, List<String> types) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", Map.of());
        variables.put("errorLabels", Map.of());
        variables.put("failure", null);
        variables.put("editing", editing);
        variables.put("types", types);
        variables.put("csvOption", !editing);
        variables.put("providerKnown", form.csv() || (form.getProviderName() != null && types.contains(form.getProviderName())));
        Map<String, List<ProviderField>> fields = new LinkedHashMap<>();
        types.forEach(type -> fields.put(type, ACME_FIELDS));
        variables.put("fields", fields);
        variables.put("storedSecretIds", Set.of());
        variables.put("canChooseMode", true);
        variables.put("warnLeavingOwn", false);
        variables.put("globalNotAllowed", false);
        variables.put("hasFeed", false);
        variables.put("scheduleMinIntervalMinutes", 15);
        variables.put("formAction", PATH + "/new");
        variables.put("suppliersHref", PATH);
        variables.put("backLabel", "Dostawcy");
        variables.put("pageTitle", editing ? "Acme" : "Nowy dostawca");
        return variables;
    }

    /** The nine-column table and its modals are gone: one list of integrations and price lists, two actions a row. */
    @Test
    void theListDescribesEachSupplierInWordsWithEditAndRemoveInTheRow() {
        // given
        List<SupplierView> suppliers = List.of(
                SupplierView.of(connection("Acme", "Acme", ConnectionMode.GLOBAL, true, FEED, true), false, PATH),
                SupplierView.of(connection("Kowalski-abcd1234", "Hurtownia Kowalski", ConnectionMode.MANUAL, true, FEED, true),
                        false, PATH));

        // when
        String html = list(suppliers, null);

        // then
        assertThat(html).contains("Acme · konfiguracja globalna").contains("Ceny i realizacja zamówień")
                .contains("Oferta z 16.09.2026 21:52").contains("Cennik CSV wgrywany ręcznie")
                .contains("Plik wgrany 16.09.2026 21:52");
        assertThat(html).contains("aria-label=\"Edytuj: Acme\"").contains("href=\"/dashboard/store/suppliers/Acme\"")
                .contains("href=\"/dashboard/store/suppliers/Acme/disconnect\"").contains(">Odłącz<")
                .contains("href=\"/dashboard/store/suppliers/Kowalski-abcd1234/delete\"").contains(">Usuń<")
                .contains("data-cl-confirm-title=\"Usunąć dostawcę Hurtownia Kowalski?\"")
                .contains("data-cl-confirm-message=\"Skutek usunięcia Hurtownia Kowalski\"");
        assertThat(html).contains("href=\"/dashboard/store/suppliers/new\"").contains(">Dodaj dostawcę<");
        assertThat(html).doesNotContain("<table").doesNotContain("supplierModal").doesNotContain("fulfilmentSettingsModal").doesNotContain("is-primary is-small")
                .doesNotContain("supplier-admin-form").doesNotContain("??");
    }

    @Test
    void whatStoppedASupplierIsFlaggedInItsRow() {
        // given
        List<SupplierView> suppliers = List.of(
                SupplierView.of(connection("Gone", "Gone", ConnectionMode.OWN, true, null, false), false, PATH),
                SupplierView.of(connection("AcmeB", "AcmeB", ConnectionMode.OWN, true, null, true), true, PATH),
                SupplierView.of(connection("manual-abcd1234", "Cennik", ConnectionMode.MANUAL, false, null, true), false, PATH));

        // when
        String html = list(suppliers, null);

        // then
        assertThat(html).contains(">Integracja niedostępna<").contains("Możesz go tylko odłączyć.")
                .doesNotContain("href=\"/dashboard/store/suppliers/Gone\"");
        assertThat(html).contains(">Niekompletny<").contains("Brakuje danych dostępu").contains(">Uzupełnij<");
        assertThat(html).contains(">Wyłączony<").contains("Brak pliku — wgraj cennik, aby włączyć dostawcę");
    }

    @Test
    void anEmptyListSaysWhatTheStoreIsLeftWith() {
        // when
        String html = list(List.of(), null);

        // then
        assertThat(html).contains("Nie masz jeszcze dostawców.").doesNotContain("class=\"cl-list\"");
    }

    @Test
    void theApplicationAdminGetsTheStoresSupplierSettingsBelowTheList() {
        // given
        SupplierAdminSettingsForm form = new SupplierAdminSettingsForm();
        form.setCanUseGlobalSuppliers(true);
        form.setInventoryCacheTtlMinutes("30");

        // when
        String html = list(List.of(), form);

        // then
        assertThat(html).contains("id=\"supplier-admin-form\"").contains("Ustawienia administratora aplikacji")
                .contains("Sklep może korzystać z globalnej konfiguracji dostawców")
                .contains("value=\"30\"").contains(">Zapisz zmiany<");
    }

    @Test
    void aNewSupplierChoosesBetweenIntegrationsAndAPriceListWithFieldsTiedToTheChoice() {
        // when
        String html = SettingsTemplateRenderer.render("store-supplier",
                subpage(SupplierSettingsForm.newSupplier(null), false, List.of("Acme")));

        // then
        assertThat(html).contains("data-cl-variant-select=\"supplier\"").contains(">Wybierz…<")
                .contains("<option value=\"Acme\">Acme</option>")
                .contains("<option value=\"manual\">Własny cennik CSV (bez integracji)</option>");
        assertThat(html).contains("data-cl-variant-group=\"supplier\" data-cl-variant=\"Acme\"")
                .contains("Dane dostępu · Acme").contains("name=\"settings[Acme.login]\"");
        assertThat(html).contains("data-cl-variant-when=\"supplier=manual\"").contains("name=\"file\"")
                .contains("enctype=\"multipart/form-data\"");
        assertThat(html).contains("name=\"mode\" data-cl-variant-select=\"mode\" value=\"GLOBAL\"")
                .contains("data-cl-variant-when=\"mode!=GLOBAL\"");
        // Before the supplier is chosen only the choice shows: the rest waits for it and is not sent.
        assertThat(html).contains("data-cl-variant-when=\"supplier!=|manual\"")
                .contains("data-cl-variant-when=\"supplier!= mode!=GLOBAL\"").contains("data-cl-variant-when=\"supplier!=\"");
        assertThat(html).contains("class=\"cl-span-4 cl-field\"").doesNotContain("class=\"cl-span-3 cl-field\"");
        assertThat(html).containsPattern("name=\"includeInPricing\" value=\"true\"[^>]*checked=\"checked\"")
                .contains(">Zapisz dostawcę<").contains("/js/variant-fields.js");
    }

    @Test
    void aPriceListIsEditedWithoutTheIntegrationParts() {
        // given
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier(SupplierSettingsForm.CSV);
        form.setLabel("Hurtownia Kowalski");
        Map<String, Object> variables = subpage(form, true, List.of());
        variables.put("canChooseMode", false);
        variables.put("hasFeed", true);

        // when
        String html = SettingsTemplateRenderer.render("store-supplier", variables);

        // then
        assertThat(html).contains("<input type=\"hidden\" name=\"providerName\" data-cl-variant-select=\"supplier\" value=\"manual\"")
                .contains("value=\"Hurtownia Kowalski\"").contains("Wgrany plik zostanie zastąpiony nowym.")
                .contains("Dostawca aktywny");
        assertThat(html).doesNotContain("name=\"mode\"").doesNotContain("Odświeżanie oferty")
                .doesNotContain("<select");
    }

    @Test
    void theFulfilmentPageIsOneFormWithTheAddressChangeUnderTheOrderPage() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/fulfilment"));
        variables.put("navigation", null);
        FulfilmentSettingsForm form = new FulfilmentSettingsForm();
        form.setOrderAssemblyDays("2");
        form.setOrderRealizationDays("5");
        form.setDefaultFulfilmentType("WarehouseFulfilment");
        form.setClientOrderPageEnabled(true);
        variables.put("form", form);
        variables.put("errors", Map.of("orderRealizationDays", "store.fulfilment.days.invalid"));
        variables.put("failure", null);
        variables.put("formAction", "/dashboard/store/fulfilment");

        // when
        String html = SettingsTemplateRenderer.render("store-fulfilment", variables);

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Realizacja zamówień</h1>")
                .contains("Kompletacja towaru (dni)").contains("value=\"2\"")
                .contains("Podaj liczbę dni od 0 do 60.").contains("aria-invalid=\"true\"");
        assertThat(html).containsPattern("value=\"WarehouseFulfilment\"[^>]*checked=\"checked\"").contains("Przez magazyn sklepu")
                .contains("Wysyłką od dostawcy do klienta").contains("Dropshipping, zmienisz w zamówieniu.")
                .doesNotContain(">WarehouseFulfilment<");
        assertThat(html).contains("Włącz automatyczną realizację");
        assertThat(html).contains("data-cl-reveal=\"fulfilment-address-change\"")
                .contains("id=\"fulfilment-address-change\"").contains("Pozwól klientowi zmienić adres dostawy");
        assertThat(html).doesNotContain("supplierModal").doesNotContain("fulfilmentSettingsModal").doesNotContain("is-primary is-small").doesNotContain("Dostawcy")
                .doesNotContain("Korzystaj z globalnych ustawień dostawców");
    }
}
