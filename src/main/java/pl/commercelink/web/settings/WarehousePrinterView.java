package pl.commercelink.web.settings;

import pl.commercelink.printing.api.PrintProviderDescriptor;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.Printer;

/**
 * A label printer summarised for the list on the warehouse page: its type and the value of its first setting, which names
 * the device (e.g. "Zebra ZPL · ZD-PACK-01"). Field labels come from the adapter and read like form labels, so the list
 * shows the value alone. Secret settings are never part of the summary.
 */
public record WarehousePrinterView(String id, String name, String typeName, String summary, String editHref,
                                   String deleteHref) {

    /** @param descriptor the printer's type, or null when no installed adapter serves it any more */
    public static WarehousePrinterView of(Printer printer, PrintProviderDescriptor descriptor, String printersPath) {
        String typeName = descriptor != null ? descriptor.displayName() : printer.getProviderName();
        String summary = descriptor == null || printer.getSettings() == null ? null : descriptor.configurationFields().stream()
                .filter(field -> field.type() != FieldType.PASSWORD)
                .map(field -> printer.getSettings().get(field.key()))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        String base = printersPath + "/" + printer.getId();
        return new WarehousePrinterView(printer.getId(), printer.getName(), typeName, summary, base, base + "/delete");
    }
}
