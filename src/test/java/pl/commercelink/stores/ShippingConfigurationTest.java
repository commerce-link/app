package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingConfigurationTest {

    private static ShippingDetails address(String id) {
        ShippingDetails details = new ShippingDetails();
        details.setId(id);
        details.setCompanyName("Magazyn " + id);
        return details;
    }

    private static PackageTemplate template(String id) {
        PackageTemplate template = new PackageTemplate();
        template.setId(id);
        template.setName("Karton " + id);
        return template;
    }

    @Test
    void theFirstPickupAddressBecomesTheDefaultAndALaterOneOnlyWhenAsked() {
        // given
        ShippingConfiguration configuration = new ShippingConfiguration();

        // when
        configuration.addPickUpAddress(address("a"), false);
        configuration.addPickUpAddress(address("b"), false);
        configuration.addPickUpAddress(address("c"), true);

        // then
        assertThat(configuration.getPickUpAddresses()).extracting(ShippingDetails::is_default).containsExactly(false, false, true);
    }

    @Test
    void removingTheDefaultPickupAddressHandsTheFlagToTheFirstRemainingOne() {
        // given
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.addPickUpAddress(address("a"), false);
        configuration.addPickUpAddress(address("b"), false);

        // when
        boolean removed = configuration.removePickUpAddress("a");

        // then
        assertThat(removed).isTrue();
        assertThat(configuration.getDefaultPickUpAddress()).map(ShippingDetails::getId).contains("b");
        assertThat(configuration.removePickUpAddress("missing")).isFalse();
    }

    @Test
    void anUnknownAddressCannotBecomeTheDefault() {
        // given
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.addPickUpAddress(address("a"), false);

        // when / then
        assertThat(configuration.makeDefaultPickUpAddress("missing")).isFalse();
        assertThat(configuration.getDefaultPickUpAddress()).map(ShippingDetails::getId).contains("a");
    }

    @Test
    void theLabelSenderIsASingleDefaultEntryOrNoneForThePickupAddress() {
        // given
        ShippingConfiguration configuration = new ShippingConfiguration();
        ShippingDetails unused = address("old");
        configuration.setSenderAddresses(new LinkedList<>(List.of(unused, address("older"))));

        // when
        configuration.setLabelSender(address("head-office"));

        // then
        assertThat(configuration.getSenderAddresses()).singleElement().satisfies(sender -> {
            assertThat(sender.getId()).isEqualTo("head-office");
            assertThat(sender.is_default()).isTrue();
        });
        assertThat(configuration.getLabelSender().getId()).isEqualTo("head-office");

        // when
        configuration.setLabelSender(null);

        // then
        assertThat(configuration.getSenderAddresses()).isEmpty();
        assertThat(configuration.getLabelSender()).isNull();
    }

    @Test
    void sendersWithoutADefaultPrintThePickupAddress() {
        // given: an old store kept sender addresses but marked none as default, so ShippingService never read them
        ShippingConfiguration configuration = new ShippingConfiguration();
        ShippingDetails notDefault = address("old");
        notDefault.set_default(false);
        configuration.setSenderAddresses(new LinkedList<>(List.of(notDefault)));

        // when / then
        assertThat(configuration.getLabelSender()).isNull();
    }

    @Test
    void templatesFollowTheSameDefaultRulesAsAddresses() {
        // given
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.addPackageTemplate(template("m"), false);
        configuration.addPackageTemplate(template("s"), false);

        // when
        configuration.makeDefaultPackageTemplate("s");
        configuration.removePackageTemplate("s");

        // then
        assertThat(configuration.getPackageTemplates()).singleElement().satisfies(template -> {
            assertThat(template.getId()).isEqualTo("m");
            assertThat(template.isDefault()).isTrue();
        });
    }

    @Test
    void recordsWithoutAnIdGetOneSoTheyCanBeAddressed() {
        // given
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.getPickUpAddresses().add(address(null));
        configuration.getPackageTemplates().add(template(null));

        // when
        boolean changed = configuration.assignMissingIds();

        // then
        assertThat(changed).isTrue();
        assertThat(configuration.getPickUpAddresses().getFirst().getId()).isNotBlank();
        assertThat(configuration.getPackageTemplates().getFirst().getId()).isNotBlank();
        assertThat(configuration.assignMissingIds()).isFalse();
    }
}
