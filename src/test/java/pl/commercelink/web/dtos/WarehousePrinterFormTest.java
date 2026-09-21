package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.WarehouseConfiguration;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehousePrinterFormTest {

    private static final List<ProviderField> ZEBRA = List.of(
            new ProviderField("deviceId", "ID drukarki", FieldType.TEXT, true, ""),
            new ProviderField("dpi", "DPI", FieldType.NUMBER, false, "203"),
            new ProviderField("apiKey", "Klucz API", FieldType.PASSWORD, true, ""),
            new ProviderField("endpoint", "Adres", FieldType.URL, false, ""));

    private WarehousePrinterForm form(String name, String type, Map<String, String> settings) {
        WarehousePrinterForm form = new WarehousePrinterForm();
        form.setName(name);
        form.setType(type);
        Map<String, String> keyed = new HashMap<>();
        settings.forEach((key, value) -> keyed.put(type + "." + key, value));
        form.setSettings(keyed);
        return form;
    }

    private Printer stored(String id, String name, Map<String, String> settings) {
        Printer printer = new Printer();
        printer.setId(id);
        printer.setName(name);
        printer.setProviderName("zebra");
        printer.setSettings(new HashMap<>(settings));
        return printer;
    }

    private WarehouseConfiguration configurationWith(Printer... printers) {
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        configuration.setPrinters(new LinkedList<>(List.of(printers)));
        return configuration;
    }

    @Test
    void acceptsAPrinterWithItsRequiredSettings() {
        // when
        Map<String, String> errors = form("Zebra pakowanie", "zebra", Map.of("deviceId", "ZD-1", "apiKey", "secret", "dpi", "203"))
                .validate(ZEBRA, configurationWith(), null);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void requiresANameATypeAndTheRequiredSettingsOfThatType() {
        // when
        Map<String, String> errors = form(" ", "zebra", Map.of()).validate(ZEBRA, configurationWith(), null);

        // then
        assertThat(errors).containsExactly(
                Map.entry("name", "store.warehouse.printer.name.required"),
                Map.entry("setting-zebra-deviceId", "store.warehouse.printer.setting.required"),
                Map.entry("setting-zebra-apiKey", "store.warehouse.printer.setting.required"));
    }

    @Test
    void anUnknownTypeIsRejected() {
        // when
        Map<String, String> errors = form("Zebra", "laser", Map.of()).validate(null, configurationWith(), null);

        // then
        assertThat(errors).containsExactly(Map.entry("type", "store.warehouse.printer.type.required"));
    }

    @Test
    void theSummaryListsTheTypeBeforeTheNameAsTheyStandOnThePage() {
        // when
        Map<String, String> errors = form(" ", "laser", Map.of()).validate(null, configurationWith(), null);

        // then
        assertThat(errors).containsExactly(
                Map.entry("type", "store.warehouse.printer.type.required"),
                Map.entry("name", "store.warehouse.printer.name.required"));
    }

    @Test
    void aNameAlreadyUsedByAnotherPrinterIsRejected() {
        // given
        WarehouseConfiguration configuration = configurationWith(stored("p-1", "Zebra pakowanie", Map.of()));

        // when / then
        assertThat(form("zebra PAKOWANIE", "zebra", Map.of("deviceId", "1", "apiKey", "x")).validate(ZEBRA, configuration, null))
                .containsEntry("name", "store.warehouse.printer.name.taken");
        assertThat(form("Zebra pakowanie", "zebra", Map.of("deviceId", "1", "apiKey", "x")).validate(ZEBRA, configuration, configuration.getPrinters().getFirst()))
                .isEmpty();
    }

    @Test
    void checksNumbersAndWebAddresses() {
        // when
        Map<String, String> errors = form("Zebra", "zebra", Map.of("deviceId", "1", "apiKey", "x", "dpi", "dużo", "endpoint", "ftp://x"))
                .validate(ZEBRA, configurationWith(), null);

        // then
        assertThat(errors).containsEntry("setting-zebra-dpi", "store.warehouse.printer.setting.number")
                .containsEntry("setting-zebra-endpoint", "store.warehouse.printer.setting.url");
    }

    @Test
    void anEmptySecretOnEditKeepsTheStoredOne() {
        // given
        Printer existing = stored("p-1", "Zebra", Map.of("deviceId", "ZD-1", "apiKey", "stored-secret"));
        WarehousePrinterForm form = form("Zebra", "zebra", Map.of("deviceId", "ZD-2", "apiKey", ""));

        // when
        Map<String, String> errors = form.validate(ZEBRA, configurationWith(existing), existing);
        form.applyTo(existing, ZEBRA);

        // then
        assertThat(errors).isEmpty();
        assertThat(existing.getSettings()).containsEntry("apiKey", "stored-secret").containsEntry("deviceId", "ZD-2");
    }

    @Test
    void savesOnlyTheSettingsOfTheChosenTypeTrimmed() {
        // given
        WarehousePrinterForm form = form("  Zebra  ", "zebra", Map.of("deviceId", " ZD-1 ", "apiKey", "x", "dpi", ""));
        form.getSettings().put("laser.toner", "black");
        form.getSettings().put("zebra.unknown", "value");
        Printer printer = new Printer();

        // when
        form.applyTo(printer, ZEBRA);

        // then
        assertThat(printer.getName()).isEqualTo("Zebra");
        assertThat(printer.getProviderName()).isEqualTo("zebra");
        assertThat(printer.getSettings()).containsOnly(Map.entry("deviceId", "ZD-1"), Map.entry("apiKey", "x"));
    }

    @Test
    void theEditFormNeverCarriesAStoredSecret() {
        // given
        Printer existing = stored("p-1", "Zebra", Map.of("deviceId", "ZD-1", "apiKey", "stored-secret"));

        // when
        WarehousePrinterForm form = WarehousePrinterForm.from(existing, ZEBRA);

        // then
        assertThat(form.getSettings()).containsEntry("zebra.deviceId", "ZD-1").doesNotContainKey("zebra.apiKey");
    }
}
