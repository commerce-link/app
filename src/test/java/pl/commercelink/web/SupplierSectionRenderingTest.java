package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.stores.ConnectionMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders fragments/supplier-section.html through a real Thymeleaf engine (same technique as
 * CategoryPickerFragmentTest) instead of only asserting on the raw file text. This proves the
 * fragment's own markup compiles and renders as expected for the exact argument shapes
 * SupplierSectionModel populates on the model -- nothing more.
 *
 * <p>It does <b>not</b> prove that the controller's returned view name resolves: every render
 * here goes through {@code th:replace}, which (unlike a view name handled by Spring's
 * {@code ThymeleafView}) happily accepts positional fragment parameters. A controller returning
 * {@code "fragments/supplier-section :: supplierSection(${a}, false, ...)"} as a view name fails
 * at runtime with {@code IllegalArgumentException: Parameters in a view specification must be
 * named (non-synthetic)} even though the exact same selector renders fine through th:replace, as
 * it does in this test. SupplierSectionModel avoids that trap by returning the no-argument
 * {@code externalSection}/{@code manualSection} selectors instead (see the two tests below); the
 * two tests that still call {@code supplierSection(...)} directly with explicit arguments below
 * are only pinning that fragment's own markup, the same way store-fulfilment.html's initial
 * page render does via th:replace, not the controller's view-name path.
 */
class SupplierSectionRenderingTest {

    private static final String SECTION = "<div th:replace=\"~{fragments/supplier-section :: supplierSection(%s)}\"></div>";

    private TemplateEngine templateEngine() {
        return EnglishFragmentTemplateEngine.create();
    }

    @Test
    void rendersTheExternalSectionExactlyAsStoreFulfilmentSupplierControllerBuildsIt() {
        // given -- the same argument shape SupplierSectionModel.renderExternalSection returns
        SupplierConnectionView elko = new SupplierConnectionView(
                "Elko", "Elko", "Elko", ConnectionMode.OWN, true, true, true, null, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(elko));
        context.setVariable("sectionShowMode", true);
        context.setVariable("sectionAvailableSuppliers", List.of("Acme"));
        context.setVariable("sectionSuccessMessage", "Supplier Elko saved.");
        context.setVariable("sectionSuppliersWithStoredConfig", "Elko");

        String args = "${sectionRows}, false, ${sectionShowMode}, "
                + "'store.supplier.section.title', 'supplier-add-button', 'store.supplier.add.button', "
                + "${sectionAvailableSuppliers.isEmpty()}, 'store.supplier.add.none', ${sectionSuccessMessage}, "
                + "${sectionSuppliersWithStoredConfig}";

        // when
        String html = templateEngine().process(SECTION.formatted(args), context);

        // then
        assertThat(html).doesNotContain("??store.supplier");
        assertThat(html).contains("Suppliers"); // store.supplier.section.title
        assertThat(html).contains("id=\"supplier-add-button\"");
        assertThat(html).contains("Elko");
        assertThat(html).contains("data-success-message=\"Supplier Elko saved.\"");
        assertThat(html).contains("data-suppliers-with-stored-config=\"Elko\"");
        // Acme is the only unconnected supplier, but the Add button reflects a non-empty
        // availableSuppliers list, so it must not be disabled
        assertThat(html).doesNotContain("disabled=\"disabled\"");
    }

    @Test
    void theStoredConfigListOnTheFragmentRootChangesBetweenTwoRendersInsteadOfStayingFixed() {
        // Pins the actual outcome the JS fix depends on: swapping in this fragment after a save
        // must hand the page a root whose data-suppliers-with-stored-config reflects THIS render's
        // caller-supplied value, not a value stuck at some earlier state. A template that hardcodes
        // the attribute, drops it, or ignores the variable would render identically both times below
        // and fail the second assertion, exactly the class of bug that let a stale `required`
        // survive a same-session save in the real page.
        SupplierConnectionView elko = new SupplierConnectionView(
                "Elko", "Elko", "Elko", ConnectionMode.OWN, true, true, true, null, null, true);
        String args = "${sectionRows}, false, ${sectionShowMode}, "
                + "'store.supplier.section.title', 'supplier-add-button', 'store.supplier.add.button', "
                + "${sectionAvailableSuppliers.isEmpty()}, 'store.supplier.add.none', ${sectionSuccessMessage}, "
                + "${sectionSuppliersWithStoredConfig}";

        // when -- before Elko's credentials were ever saved
        Context before = new Context();
        before.setVariable("sectionRows", List.of(elko));
        before.setVariable("sectionShowMode", true);
        before.setVariable("sectionAvailableSuppliers", List.of("Acme"));
        before.setVariable("sectionSuccessMessage", null);
        before.setVariable("sectionSuppliersWithStoredConfig", "");
        String htmlBefore = templateEngine().process(SECTION.formatted(args), before);

        // ... and the very next render in the same page session, right after connecting Elko
        Context after = new Context();
        after.setVariable("sectionRows", List.of(elko));
        after.setVariable("sectionShowMode", true);
        after.setVariable("sectionAvailableSuppliers", List.of("Acme"));
        after.setVariable("sectionSuccessMessage", "Supplier Elko saved.");
        after.setVariable("sectionSuppliersWithStoredConfig", "Elko");
        String htmlAfter = templateEngine().process(SECTION.formatted(args), after);

        // then
        assertThat(htmlBefore).doesNotContain("data-suppliers-with-stored-config=\"Elko\"");
        assertThat(htmlAfter).contains("data-suppliers-with-stored-config=\"Elko\"");
    }

    @Test
    void rendersTheManualSectionExactlyAsManualSupplierControllerBuildsIt() {
        // given -- the same argument shape SupplierSectionModel.renderManualSection returns
        SupplierConnectionView manual = new SupplierConnectionView(
                "manual:Hurtownia X", null, "Hurtownia X", ConnectionMode.MANUAL, false, false, false, null, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(manual));
        context.setVariable("sectionSuccessMessage", null);

        String args = "${sectionRows}, true, false, 'store.manual.section.title', "
                + "'manual-add-button', 'store.manual.add.button', false, null, ${sectionSuccessMessage}, ''";

        // when
        String html = templateEngine().process(SECTION.formatted(args), context);

        // then
        assertThat(html).doesNotContain("??store.manual");
        assertThat(html).contains("Manual suppliers"); // store.manual.section.title
        assertThat(html).contains("id=\"manual-add-button\"");
        assertThat(html).contains("Hurtownia X");
        // no successMessage was supplied, so the toast attribute must be absent, not "null"
        assertThat(html).doesNotContain("data-success-message");
    }

    @Test
    void theExternalSectionWrapperRendersTheSameMarkupAsTheParameterizedFragment() {
        // given -- exactly the model attributes SupplierSectionModel.renderExternalSection sets,
        // and the no-argument selector it now returns as the view name
        SupplierConnectionView elko = new SupplierConnectionView(
                "Elko", "Elko", "Elko", ConnectionMode.OWN, true, true, true, null, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(elko));
        context.setVariable("sectionShowMode", true);
        context.setVariable("sectionAvailableSuppliers", List.of("Acme"));
        context.setVariable("sectionSuccessMessage", "Supplier Elko saved.");
        context.setVariable("sectionSuppliersWithStoredConfig", "Elko");

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: externalSection}\"></div>", context);

        // then -- proves the wrapper actually forwards to supplierSection with the right rows/
        // showMode/availableSuppliers/successMessage/storedConfigSuppliers, not just that it parses
        assertThat(html).doesNotContain("??store.supplier");
        assertThat(html).contains("Suppliers");
        assertThat(html).contains("id=\"supplier-add-button\"");
        assertThat(html).contains("Elko");
        assertThat(html).contains("data-success-message=\"Supplier Elko saved.\"");
        assertThat(html).contains("data-suppliers-with-stored-config=\"Elko\"");
        assertThat(html).doesNotContain("disabled=\"disabled\"");
    }

    @Test
    void theManualSectionWrapperRendersTheSameMarkupAsTheParameterizedFragment() {
        // given -- exactly the model attributes SupplierSectionModel.renderManualSection sets
        SupplierConnectionView manual = new SupplierConnectionView(
                "manual:Hurtownia X", null, "Hurtownia X", ConnectionMode.MANUAL, false, false, false, null, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(manual));
        context.setVariable("sectionSuccessMessage", null);

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: manualSection}\"></div>", context);

        // then
        assertThat(html).doesNotContain("??store.manual");
        assertThat(html).contains("Manual suppliers");
        assertThat(html).contains("id=\"manual-add-button\"");
        assertThat(html).contains("Hurtownia X");
        assertThat(html).doesNotContain("data-success-message");
    }

    @Test
    void theScheduleColumnSummarisesAnOwnConnectionAndCarriesTheExpressionForTheModal() {
        // given
        SupplierConnectionView elko = new SupplierConnectionView(
                "Elko", "Elko", "Elko", ConnectionMode.OWN, true, true, true, null, "0 5,17 * * ? *", true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(elko));
        context.setVariable("sectionShowMode", true);
        context.setVariable("sectionAvailableSuppliers", List.of());
        context.setVariable("sectionSuccessMessage", null);
        context.setVariable("sectionSuppliersWithStoredConfig", "");

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: externalSection}\"></div>", context);

        // then
        assertThat(html).doesNotContain("??store.supplier");
        assertThat(html).contains("Schedule");
        assertThat(html).contains("Daily at 05:00, 17:00");
        // the modal reads the expression itself straight off the row, so it has to survive a swap
        assertThat(html).contains("data-feed-schedule=\"0 5,17 * * ? *\"");
    }

    @Test
    void theScheduleColumnSaysNothingForAGlobalConnection() {
        // given -- a global connection rides the platform-wide feed, which this store does not schedule
        SupplierConnectionView acme = new SupplierConnectionView(
                "Acme", "Acme", "Acme", ConnectionMode.GLOBAL, true, true, true, null, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(acme));
        context.setVariable("sectionShowMode", true);
        context.setVariable("sectionAvailableSuppliers", List.of());
        context.setVariable("sectionSuccessMessage", null);
        context.setVariable("sectionSuppliersWithStoredConfig", "");

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: externalSection}\"></div>", context);

        // then
        assertThat(html).contains("&mdash;");
        assertThat(html).doesNotContain("Default — once a night");
        assertThat(html).doesNotContain("data-feed-schedule");
    }

    @Test
    void theManualSectionHasNoScheduleColumn() {
        // given -- manual feeds are uploaded by hand, so there is nothing to schedule
        SupplierConnectionView manual = new SupplierConnectionView(
                "manual:Hurtownia X", null, "Hurtownia X", ConnectionMode.MANUAL, false, false, false, null, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(manual));
        context.setVariable("sectionSuccessMessage", null);

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: manualSection}\"></div>", context);

        // then
        assertThat(html).doesNotContain("Schedule");
    }

    @Test
    void rendersTheSmallErrorFragmentWithTheErrorMessageModelAttribute() {
        // given
        Context context = new Context();
        context.setVariable("errorMessage", "Supplier Elko requires field Login.");

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: sectionError}\"></div>", context);

        // then
        assertThat(html).contains("notification is-danger");
        assertThat(html).contains("Supplier Elko requires field Login.");
    }
}
