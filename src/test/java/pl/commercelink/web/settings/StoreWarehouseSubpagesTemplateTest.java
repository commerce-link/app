package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.web.StoreWarehousePrinterController.PrinterType;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.dtos.WarehouseAddressForm;
import pl.commercelink.web.dtos.WarehousePrinterForm;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoreWarehouseSubpagesTemplateTest {

    private static final List<ProviderField> ZEBRA = List.of(
            new ProviderField("deviceId", "ID drukarki (Browser Print)", FieldType.TEXT, true, ""),
            new ProviderField("dpi", "DPI", FieldType.NUMBER, false, "203"),
            new ProviderField("apiKey", "Klucz API", FieldType.PASSWORD, false, ""));

    private Map<String, Object> addressPage(WarehouseAddressForm form, Map<String, String> errors, boolean alreadyDefault) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("countries", CountryOptions.forPicker(form.getCountry(), Locale.forLanguageTag("pl")));
        variables.put("formAction", "/dashboard/store/warehouse/addresses/new");
        variables.put("pageTitle", "Nowy adres przyjęcia towaru");
        variables.put("alreadyDefault", alreadyDefault);
        variables.put("warehouseHref", "/dashboard/store/warehouse");
        variables.put("backLabel", "Magazyn");
        return variables;
    }

    private Map<String, Object> printerPage(WarehousePrinterForm form, Map<String, String> errors, Set<String> storedSecretIds) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("errorLabels", form.errorLabels(ZEBRA));
        variables.put("types", List.of(new PrinterType("zebra", "Zebra ZPL", ZEBRA)));
        variables.put("knownType", "zebra".equals(form.getType()));
        variables.put("formAction", "/dashboard/store/warehouse/printers/p-1");
        variables.put("storedSecretIds", storedSecretIds);
        variables.put("pageTitle", "Drukarka etykiet");
        variables.put("editing", true);
        variables.put("warehouseHref", "/dashboard/store/warehouse");
        variables.put("backLabel", "Magazyn");
        return variables;
    }

    @Test
    void theAddressFormLeadsBackToTheWarehouseAndUsesAddressHints() {
        // when
        String html = SettingsTemplateRenderer.render("store-warehouse-address", addressPage(WarehouseAddressForm.empty(), Map.of(), false));

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Nowy adres przyjęcia towaru</h1>");
        assertThat(html).contains("class=\"cl-back\" href=\"/dashboard/store/warehouse\"");
        assertThat(html).contains("autocomplete=\"postal-code\"").contains("type=\"tel\"").contains("inputmode=\"tel\"")
                .contains("<option value=\"PL\" selected=\"selected\">Polska</option>");
        assertThat(html).contains("name=\"makeDefault\"").contains(">Zapisz adres<").contains(">Anuluj<");
        assertThat(html).containsPattern("<span>Email</span>\\s*<span class=\"cl-optional\">")
                .containsPattern("<span>Telefon</span>\\s*<span class=\"cl-optional\">");
        assertThat(html).contains("id=\"email\" name=\"email\" type=\"email\" value=\"\" autocomplete=\"email\" inputmode=\"email\"/>")
                .contains("id=\"city\" name=\"city\" type=\"text\" value=\"\" autocomplete=\"address-level2\" required=\"required\"/>");
        assertThat(html).doesNotContain("??").doesNotContain("pattern=");
    }

    @Test
    void theDefaultAddressCannotBeUnmarkedFromItsOwnForm() {
        // when
        String html = SettingsTemplateRenderer.render("store-warehouse-address", addressPage(WarehouseAddressForm.empty(), Map.of(), true));

        // then
        assertThat(html).doesNotContain("name=\"makeDefault\"").contains("To jest adres domyślny.");
    }

    @Test
    void aSavedAddressFormTellsTheScriptWhereToReturn() {
        // given
        Map<String, Object> variables = addressPage(WarehouseAddressForm.empty(), Map.of(), false);
        variables.put("redirectTo", "/dashboard/store/warehouse");

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{store-warehouse-address :: addressForm}\"></div>", variables);

        // then
        assertThat(html).startsWith("<form").contains("data-cl-redirect=\"/dashboard/store/warehouse\"");
    }

    @Test
    void printerSettingsArePostedPerTypeWithInputsMatchingTheirKind() {
        // given
        WarehousePrinterForm form = WarehousePrinterForm.empty("zebra");
        form.setName("Zebra pakowanie");
        form.getSettings().put("zebra.deviceId", "ZD-1");

        // when
        String html = SettingsTemplateRenderer.render("store-warehouse-printer", printerPage(form, Map.of(), Set.of()));

        // then
        assertThat(html).contains("data-cl-variant-select=\"printer-type\"").contains("data-cl-variant=\"zebra\"");
        assertThat(html).contains(">Ustawienia: Zebra ZPL</legend>");
        assertThat(html).containsPattern("id=\"setting-zebra-deviceId\" name=\"settings\\[zebra.deviceId]\"\\s+type=\"text\"\\s+value=\"ZD-1\"");
        assertThat(html).contains("id=\"setting-zebra-dpi\"").contains("inputmode=\"decimal\"").contains("Przykład: 203");
        assertThat(html).contains("id=\"setting-zebra-apiKey\"").contains("type=\"password\"").contains("autocomplete=\"new-password\"");
        assertThat(html).containsPattern("<option value=\"zebra\"\\s+selected=\"selected\">Zebra ZPL</option>");
        // The type decides which settings the page shows, so it is asked before the name the store invents.
        assertThat(html.indexOf("id=\"type\"")).isLessThan(html.indexOf("id=\"name\""));
        assertThat(html).containsPattern("<span>DPI</span>\\s*<span class=\"cl-optional\">opcjonalne</span>");
        assertThat(html).doesNotContain("??").doesNotContain("(opcjonalne)");
    }

    @Test
    void aStoredSecretIsAnnouncedButNeverRendered() {
        // given
        WarehousePrinterForm form = WarehousePrinterForm.empty("zebra");
        form.setName("Zebra");

        // when
        String html = SettingsTemplateRenderer.render("store-warehouse-printer",
                printerPage(form, Map.of(), Set.of("setting-zebra-apiKey")));

        // then
        assertThat(html).containsPattern("id=\"setting-zebra-apiKey\"[^>]*placeholder=\"Zapisane — wpisz, aby zmienić\"");
        assertThat(html).doesNotContainPattern("id=\"setting-zebra-apiKey\"[^>]*value=\"[^\"]+\"");
    }

    @Test
    void aSettingErrorIsSummarisedWithTheAdapterLabel() {
        // given
        WarehousePrinterForm form = WarehousePrinterForm.empty("zebra");

        // when
        String html = SettingsTemplateRenderer.render("store-warehouse-printer", printerPage(form,
                Map.of("setting-zebra-deviceId", "store.warehouse.printer.setting.required"), Set.of()));

        // then
        assertThat(html).contains("href=\"#setting-zebra-deviceId\">ID drukarki (Browser Print): Podaj wartość.</a>");
        assertThat(html).contains("aria-describedby=\"setting-zebra-deviceId-error\"");
    }

    @Test
    void aPrinterOfAnUninstalledTypeMustPickATypeInsteadOfSilentlyTakingTheFirst() {
        // given
        WarehousePrinterForm form = WarehousePrinterForm.empty("laser");
        form.setName("Stara drukarka");

        // when
        String html = SettingsTemplateRenderer.render("store-warehouse-printer", printerPage(form, Map.of(), Set.of()));

        // then
        assertThat(html).contains("<option value=\"\">Wybierz typ</option>").doesNotContain("selected=\"selected\"");
    }
}
