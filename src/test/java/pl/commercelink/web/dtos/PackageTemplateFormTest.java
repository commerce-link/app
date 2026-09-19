package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackageTemplateFormTest {

    private static PackageTemplateForm.ParcelRow row(String width, String depth, String height, String weight, String description) {
        PackageTemplateForm.ParcelRow row = new PackageTemplateForm.ParcelRow();
        row.setWidth(width);
        row.setDepth(depth);
        row.setHeight(height);
        row.setWeight(weight);
        row.setDescription(description);
        return row;
    }

    private static PackageTemplateForm form(PackageTemplateForm.ParcelRow... rows) {
        PackageTemplateForm form = new PackageTemplateForm();
        form.setName("Karton M");
        form.setParcels(new ArrayList<>(List.of(rows)));
        return form;
    }

    @Test
    void anIncompleteParcelIsReportedAtItsFieldsInsteadOfDropped() {
        // given
        PackageTemplateForm form = form(row("40", "30", "20", "5", "Karton"), row("30", "", "abc", "0", " "));

        // when / then
        assertThat(form.validate()).containsOnlyKeys("parcel-1-depth", "parcel-1-height", "parcel-1-weight", "parcel-1-description");
    }

    @Test
    void aBlankParcelIsTheUnusedSpareRowAndIsSkipped() {
        // given
        PackageTemplateForm form = form(row("40", "30", "20", "5", "Karton"), row("", " ", null, "", ""));
        PackageTemplate template = new PackageTemplate();

        // when
        form.applyTo(template);

        // then
        assertThat(form.validate()).isEmpty();
        assertThat(template.getParcels()).singleElement().satisfies(parcel -> {
            assertThat(parcel.getWidth()).isEqualTo(40);
            assertThat(parcel.getDepth()).isEqualTo(30);
            assertThat(parcel.getHeight()).isEqualTo(20);
            assertThat(parcel.getWeight()).isEqualTo(5);
            assertThat(parcel.getDescription()).isEqualTo("Karton");
        });
    }

    @Test
    void aTemplateNeedsANameAndAtLeastOneParcel() {
        // given
        PackageTemplateForm form = form(row("", "", "", "", ""));
        form.setName(" ");

        // when / then
        assertThat(form.validate()).containsOnlyKeys("name", "parcels");
    }

    @Test
    void numbersAreWholeAndWithinRange() {
        // given
        PackageTemplateForm form = form(row("1000", "2.5", "-1", "999", "Karton"));

        // when / then
        assertThat(form.validate()).containsOnlyKeys("parcel-0-width", "parcel-0-depth", "parcel-0-height");
    }

    @Test
    void anExistingTemplateFillsTheFormAndShowsNoZeros() {
        // given
        PackageTemplate template = new PackageTemplate("Karton", new ArrayList<>(List.of(new Parcel(40, 30, 20, 5, 900, "Karton"),
                new Parcel(0, 0, 0, 0, 0, ""))));

        // when
        PackageTemplateForm form = PackageTemplateForm.from(template);

        // then
        assertThat(form.getParcels()).hasSize(2);
        assertThat(form.getParcels().get(0).getWidth()).isEqualTo("40");
        assertThat(form.getParcels().get(1).getWidth()).isNull();
    }

    @Test
    void theErrorSummaryNamesTheParcelOfEachField() {
        // given
        PackageTemplateForm form = form(row("", "", "", "", ""), row("", "", "", "", ""));

        // when / then
        assertThat(form.errorLabels((number, field) -> number + ":" + field))
                .containsEntry("parcel-1-weight", "2:weight")
                .hasSize(10);
    }

    /**
     * Nothing reads the insured value today, but the release before this one drops a parcel with value 0 on its next
     * save (Parcel.isComplete), so a rollback must find values above zero.
     */
    @Test
    void editingKeepsTheStoredInsuredValueAndANewParcelGetsOne() {
        // given
        PackageTemplate template = new PackageTemplate();
        template.setParcels(new java.util.ArrayList<>(java.util.List.of(new Parcel(40, 30, 20, 5, 250, "Karton"))));
        PackageTemplateForm form = form(row("41", "30", "20", "5", "Karton"), row("10", "10", "10", "1", "Koperta"));

        // when
        form.applyTo(template);

        // then
        assertThat(template.getParcels()).extracting(Parcel::getValue).containsExactly(250, 1);
        assertThat(template.getParcels()).extracting(Parcel::getWidth).containsExactly(41, 10);
    }
}
