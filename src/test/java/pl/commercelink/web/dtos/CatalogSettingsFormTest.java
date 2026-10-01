package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.ProductCatalog;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSettingsFormTest {

    @Test
    void nameIsRequiredAndTrimmed() {
        // given
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("   ");

        // when
        Map<String, String> errors = form.validate(5);

        // then
        assertThat(errors).containsEntry("name", "catalog.name.required");
    }

    @Test
    void tooFrequentScheduleIsReportedUnderTheScheduleField() {
        // given
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("Parts");
        form.setPricelistSchedule("0/1 * * * ? *");

        // when
        Map<String, String> errors = form.validate(5);

        // then
        assertThat(errors).containsKey("pricelistSchedule");
        assertThat(errors.get("pricelistSchedule")).startsWith("catalog.pricelist.schedule.error.");
    }

    @Test
    void anUnparsableScheduleIsReportedUnderTheScheduleField() {
        // given
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("Parts");
        form.setPricelistSchedule("*/1 * * * *");

        // when
        Map<String, String> errors = form.validate(5);

        // then
        assertThat(errors).containsEntry("pricelistSchedule", "catalog.pricelist.schedule.error.invalid.field");
    }

    @Test
    void aNameLongerThanTheLimitIsRejected() {
        // given
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("P".repeat(121));

        // when
        Map<String, String> errors = form.validate(5);

        // then
        assertThat(errors).containsEntry("name", "catalog.name.tooLong");
    }

    @Test
    void aNameAtTheLimitPasses() {
        // given
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("P".repeat(120));

        // when / then
        assertThat(form.validate(5)).isEmpty();
    }

    @Test
    void toCatalogTrimsTheName() {
        // given
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("  Parts  ");

        // when / then
        assertThat(form.toCatalog("store", "c1").getName()).isEqualTo("Parts");
    }

    @Test
    void emptyScheduleMeansTheDefaultAndPasses() {
        // given
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("Parts");
        form.setPricelistSchedule("");

        // when / then
        assertThat(form.validate(5)).isEmpty();
        assertThat(form.toCatalog("store", "c1").getPricelistSchedule()).isNull();
    }

    @Test
    void fromCopiesTheCatalogAndToCatalogWritesItBack() {
        // given
        ProductCatalog catalog = new ProductCatalog("store", "Parts");
        catalog.setDeletionProtection(false);
        catalog.setPricelistSchedule("0/30 * * * ? *");

        // when
        CatalogSettingsForm form = CatalogSettingsForm.from(catalog);
        ProductCatalog copy = form.toCatalog("store", catalog.getCatalogId());

        // then
        assertThat(form.getName()).isEqualTo("Parts");
        assertThat(form.isDeletionProtection()).isFalse();
        assertThat(copy.getName()).isEqualTo("Parts");
        assertThat(copy.getPricelistSchedule()).isEqualTo("0/30 * * * ? *");
        assertThat(copy.getCatalogId()).isEqualTo(catalog.getCatalogId());
    }
}
