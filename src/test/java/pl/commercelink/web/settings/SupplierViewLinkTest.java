package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.stores.ConnectionMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Links to a supplier's page. A price list uploaded before identities became generated tokens is keyed
 * "manual:&lt;the name the operator typed&gt;", so the identity can carry anything a name can — a space, a Polish
 * letter, a hash that would otherwise start a fragment.
 */
class SupplierViewLinkTest {

    private static final String PATH = "/dashboard/store/suppliers";

    private static SupplierConnectionView priceList(String identity) {
        return new SupplierConnectionView(identity, null, "Cennik", ConnectionMode.MANUAL,
                true, true, true, null, null, null, null, true);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "manual:Cennik hurtowy | manual:Cennik%20hurtowy",
            "manual:Cennik #2      | manual:Cennik%20%232",
            "manual:Żywność        | manual:%C5%BBywno%C5%9B%C4%87"
    })
    void escapesALegacyPriceListIdentityInTheLinks(String identity, String escaped) {
        SupplierView view = SupplierView.of(priceList(identity), false, PATH);

        assertThat(view.editHref()).isEqualTo(PATH + "/" + escaped);
        assertThat(view.removeHref()).isEqualTo(PATH + "/" + escaped + "/delete");
    }

    @Test
    void leavesAGeneratedIdentityAsItIs() {
        SupplierView view = SupplierView.of(priceList("manual-k7f3a9c2"), false, PATH);

        assertThat(view.editHref()).isEqualTo(PATH + "/manual-k7f3a9c2");
    }
}
