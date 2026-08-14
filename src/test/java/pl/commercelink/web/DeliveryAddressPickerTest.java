package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService.DeliveryAddressChoices;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.web.dtos.DeliveryAddressPicker;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryAddressPickerTest {

    private static final SupplierDeliveryAddress ADDRESS =
            new SupplierDeliveryAddress("addr-1", "ACME, Prosta 1", "Warszawa", "00-001", "PL");

    @Test
    void optionalPickerLeadsWithTheAccountAddressChoice() {
        // given
        DeliveryAddressChoices choices = new DeliveryAddressChoices(false, List.of(ADDRESS));

        // when
        DeliveryAddressPicker picker = DeliveryAddressPicker.of(choices, "Supplier account address");

        // then
        assertEquals(2, picker.options().size());
        assertEquals("", picker.options().getFirst().value());
        assertEquals("Supplier account address", picker.options().getFirst().label());
        assertEquals("addr-1", picker.options().get(1).value());
    }

    @Test
    void requiredPickerOffersOnlyRealAddresses() {
        // given
        DeliveryAddressChoices choices = new DeliveryAddressChoices(true, List.of(ADDRESS));

        // when
        DeliveryAddressPicker picker = DeliveryAddressPicker.of(choices, "Supplier account address");

        // then
        assertEquals(1, picker.options().size());
        assertEquals("addr-1", picker.options().getFirst().value());
    }

    @Test
    void emptyChoicesYieldNoOptionsAtAll() {
        // given
        DeliveryAddressChoices choices = new DeliveryAddressChoices(false, List.of());

        // when
        DeliveryAddressPicker picker = DeliveryAddressPicker.of(choices, "Supplier account address");

        // then
        assertTrue(picker.options().isEmpty());
    }
}
