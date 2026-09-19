package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseConfigurationTest {

    private Printer printer(String id, String name) {
        Printer printer = new Printer();
        printer.setId(id);
        printer.setName(name);
        printer.setProviderName("zebra");
        printer.setSettings(Map.of("deviceId", "ZD-1"));
        return printer;
    }

    @Test
    void findsAPrinterByItsId() {
        // given
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        configuration.setPrinters(new java.util.LinkedList<>(List.of(printer("p-1", "Packing"), printer("p-2", "Receiving"))));

        // when / then
        assertThat(configuration.findPrinter("p-2")).map(Printer::getName).contains("Receiving");
        assertThat(configuration.findPrinter("missing")).isEmpty();
    }

    @Test
    void removesOnlyThePrinterWithTheGivenIdEvenWhenNamesRepeat() {
        // given
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        configuration.setPrinters(new java.util.LinkedList<>(List.of(printer("p-1", "Zebra"), printer("p-2", "Zebra"))));

        // when
        configuration.removePrinter("p-1");

        // then
        assertThat(configuration.getPrinters()).extracting(Printer::getId).containsExactly("p-2");
    }

    @Test
    void recognisesATakenPrinterNameIgnoringCaseAndTheEditedPrinter() {
        // given
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        configuration.setPrinters(new java.util.LinkedList<>(List.of(printer("p-1", "Zebra pakowanie"))));

        // when / then
        assertThat(configuration.isPrinterNameTaken(" zebra PAKOWANIE ", null)).isTrue();
        assertThat(configuration.isPrinterNameTaken("Zebra pakowanie", "p-1")).isFalse();
        assertThat(configuration.isPrinterNameTaken("Zebra przyjęcia", null)).isFalse();
    }

    @Test
    void givesAnIdToEveryPrinterWithoutOneAndReportsWhetherAnythingChanged() {
        // given
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        configuration.setPrinters(new java.util.LinkedList<>(List.of(printer(null, "Old"), printer("p-2", "New"))));

        // when
        boolean changed = configuration.assignMissingPrinterIds();

        // then
        assertThat(changed).isTrue();
        assertThat(configuration.getPrinters()).allSatisfy(p -> assertThat(p.getId()).isNotBlank());
        assertThat(configuration.getPrinters().get(1).getId()).isEqualTo("p-2");
        assertThat(configuration.assignMissingPrinterIds()).isFalse();
    }
}
