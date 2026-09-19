package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.printing.api.PrintProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.Printer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehousePrinterViewTest {

    private PrintProviderDescriptor zebra(ProviderField... fields) {
        PrintProviderDescriptor descriptor = mock(PrintProviderDescriptor.class);
        when(descriptor.displayName()).thenReturn("Zebra ZPL");
        when(descriptor.configurationFields()).thenReturn(List.of(fields));
        return descriptor;
    }

    private Printer printer(Map<String, String> settings) {
        Printer printer = new Printer();
        printer.setId("p-1");
        printer.setName("Zebra pakowanie");
        printer.setProviderName("zebra");
        printer.setSettings(new HashMap<>(settings));
        return printer;
    }

    @Test
    void summarisesAPrinterByTheValueOfItsFirstSettingWithoutFieldLabels() {
        // given
        PrintProviderDescriptor zebra = zebra(
                new ProviderField("deviceId", "ID drukarki (Browser Print)", FieldType.TEXT, true, ""),
                new ProviderField("labelWidthMm", "Szerokość etykiety (mm)", FieldType.NUMBER, true, "100"));

        // when
        WarehousePrinterView view = WarehousePrinterView.of(printer(Map.of("deviceId", "ZD-PACK-01", "labelWidthMm", "100")),
                zebra, "/dashboard/store/warehouse/printers");

        // then
        assertThat(view.typeName()).isEqualTo("Zebra ZPL");
        assertThat(view.summary()).isEqualTo("ZD-PACK-01");
        assertThat(view.editHref()).isEqualTo("/dashboard/store/warehouse/printers/p-1");
    }

    @Test
    void skipsSecretAndEmptySettings() {
        // given
        PrintProviderDescriptor zebra = zebra(
                new ProviderField("apiKey", "Klucz API", FieldType.PASSWORD, true, ""),
                new ProviderField("deviceId", "ID drukarki", FieldType.TEXT, false, ""),
                new ProviderField("labelWidthMm", "Szerokość etykiety (mm)", FieldType.NUMBER, true, "100"));

        // when
        WarehousePrinterView view = WarehousePrinterView.of(printer(Map.of("apiKey", "secret", "deviceId", " ", "labelWidthMm", "100")),
                zebra, "/dashboard/store/warehouse/printers");

        // then
        assertThat(view.summary()).isEqualTo("100");
    }

    @Test
    void aPrinterWhoseAdapterIsGoneShowsItsStoredTypeAlone() {
        // when
        WarehousePrinterView view = WarehousePrinterView.of(printer(Map.of("deviceId", "ZD-1")), null, "/dashboard/store/warehouse/printers");

        // then
        assertThat(view.typeName()).isEqualTo("zebra");
        assertThat(view.summary()).isNull();
    }
}
