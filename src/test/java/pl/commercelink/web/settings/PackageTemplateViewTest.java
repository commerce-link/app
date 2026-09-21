package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackageTemplateViewTest {

    private static final String PATH = "/dashboard/store/shipping/templates";

    private static PackageTemplate template(String name, Parcel... parcels) {
        PackageTemplate template = new PackageTemplate(name, new ArrayList<>(List.of(parcels)));
        template.setId("t1");
        return template;
    }

    @Test
    void eachParcelIsOneLineOfDimensionsWeightAndContents() {
        // when
        PackageTemplateView view = PackageTemplateView.of(template("Karton M", new Parcel(40, 30, 20, 5, 0, "Karton")), PATH);

        // then
        assertThat(view.parcels()).containsExactly("40 × 30 × 20 cm · 5 kg · Karton");
        assertThat(view.complete()).isTrue();
        assertThat(view.forReturns()).isFalse();
        assertThat(view.defaultHref()).isEqualTo(PATH + "/t1/default");
    }

    @Test
    void aTemplateTheShipmentFormWouldEmptyIsIncomplete() {
        // when / then
        assertThat(PackageTemplateView.of(template("Pusty"), PATH).complete()).isFalse();
        assertThat(PackageTemplateView.of(template("Bez opisu", new Parcel(40, 30, 20, 5, 0, "")), PATH).complete()).isFalse();
    }

    @Test
    void aTemplateNamedForReturnsIsMarked() {
        // when / then
        assertThat(PackageTemplateView.of(template("RMA - Karton S", new Parcel(25, 15, 10, 1, 0, "Zwrot")), PATH).forReturns())
                .isTrue();
    }
}
