package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.dtos.PackageTemplateForm;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreShippingTemplateTemplateTest {

    private String rendered(PackageTemplateForm form, Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("errorLabels", Map.of());
        variables.put("formAction", "/dashboard/store/shipping/templates/new");
        variables.put("pageTitle", "Nowy szablon paczki");
        variables.put("alreadyDefault", false);
        variables.put("firstTemplate", false);
        variables.put("returnPrefix", "RMA - ");
        variables.put("shippingHref", "/dashboard/store/shipping");
        variables.put("backLabel", "Wysyłka");
        return SettingsTemplateRenderer.render("store-shipping-template", variables);
    }

    private static PackageTemplateForm twoParcels() {
        PackageTemplateForm form = PackageTemplateForm.empty();
        form.getParcels().add(new PackageTemplateForm.ParcelRow());
        return form;
    }

    /** Two identical "Remove parcel" buttons tell a screen reader nothing; the number says which one goes. */
    @Test
    void eachRemoveButtonNamesItsParcelAndTheRemovalIsAnnounced() {
        // when
        String html = rendered(twoParcels(), Map.of());

        // then
        assertThat(html).contains("aria-label=\"Usuń paczkę 1\"").contains("aria-label=\"Usuń paczkę 2\"")
                .contains("data-cl-repeat-label=\"Usuń paczkę @N@\"");
        assertThat(html).contains("role=\"status\" data-cl-repeat-status data-template=\"Usunięto paczkę @N@.\"");
    }

    @Test
    void aTemplateWithoutParcelsIsReportedAtTheListNotAtTheFirstWidth() {
        // when
        String html = rendered(PackageTemplateForm.empty(), Map.of("parcels", "store.shipping.template.parcels.required"));

        // then
        assertThat(html).contains("href=\"#parcels\"").contains("id=\"parcels\"").contains("id=\"parcels-error\"");
        assertThat(html).doesNotContain("id=\"parcel-0-width-error\"");
    }
}
