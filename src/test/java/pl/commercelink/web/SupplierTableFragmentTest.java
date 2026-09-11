package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierTableFragmentTest {

    private static final Path FRAGMENT = Path.of("src/main/resources/templates/fragments/supplier-table.html");

    private String fragment() throws Exception {
        return Files.readString(FRAGMENT, StandardCharsets.UTF_8);
    }

    @Test
    void declaresTheSupplierTableFragmentWithTheExpectedSignature() throws Exception {
        assertThat(fragment()).contains("th:fragment=\"supplierTable(rows, manual, showMode, basePath)\"");
    }

    @Test
    void rendersTheModeColumnOnlyWhenAskedTo() throws Exception {
        // the mode column is noise for stores without global suppliers and for the manual table
        assertThat(fragment()).contains("th:if=\"${showMode}\"");
    }

    @Test
    void rendersTheEnabledColumnOnlyForManualSuppliers() throws Exception {
        // presence in the list is what enables an own or global connection; only manual ones carry a flag
        assertThat(fragment()).contains("th:if=\"${manual}\"");
    }

    @Test
    void showsTheFeedTimestampAndDistinguishesGlobalConnectionsFromMissingFiles() throws Exception {
        String html = fragment();
        assertThat(html).contains("#{store.supplier.feed.global}");
        assertThat(html).contains("#{store.supplier.feed.missing}");
        // the record accessor is called as a method, and the value must be run through the
        // temporals formatter rather than bound raw, or a future edit could render a
        // LocalDateTime#toString() instead of a formatted timestamp
        assertThat(html).contains("row.feedLastModified()");
        assertThat(html).contains("#temporals.format(row.feedLastModified(), 'dd.MM.yyyy HH:mm')");
    }

    @Test
    void neverAccessesRecordAccessorsPropertyStyle() throws Exception {
        // SupplierConnectionView is a record; property-style access happens to resolve for
        // canonical components like feedLastModified but not for derived accessors such as
        // hasFeed() or isGlobal() - calling everything as a method avoids that trap
        assertThat(fragment()).doesNotContain("row.feedLastModified}");
    }

    @Test
    void offersConfigureAndRemoveActionsPerRow() throws Exception {
        String html = fragment();
        assertThat(html).contains("data-configure-supplier");
        assertThat(html).contains("confirmDelete(this)");
    }

    @Test
    void marksRowsWhoseProviderIsNoLongerRegistered() throws Exception {
        assertThat(fragment()).contains("${!row.knownProvider()}");
    }

    @Test
    void manualRowRendersDeleteControlAsAButtonNotAForm() throws Exception {
        String html = fragment();

        // manual rows carry the identity on a plain button; store-fulfilment.html's own script
        // wires it up with a fetch call, instead of a <form> posting straight to the
        // @ResponseBody delete endpoint, which used to strand the operator on a raw JSON page
        assertThat(html).contains("<button type=\"button\" th:if=\"${manual}\"");
        assertThat(html).contains("class=\"button is-small is-danger is-outlined manual-delete-button\"");
        assertThat(html).contains(
                "data-identity=${row.identity()},data-confirm-message=#{store.manual.delete.confirm}");

        // guards against the old shape reappearing: a <form> conditioned on manual whose action
        // posts straight to the JSON delete endpoint, submitted via the callback-less confirmDelete(this)
        assertThat(html).doesNotContain("<form th:if=\"${manual}\"");
        assertThat(html).doesNotContain("'/manual-supplier/' + ${row.identity()} + '/delete'");
        assertThat(html).doesNotContain(
                "${manual} ? #{store.supplier.action.delete} : #{store.supplier.action.disconnect}");
    }

    @Test
    void externalRowKeepsFormAndConfirmDeleteThisShape() throws Exception {
        String html = fragment();

        // the external/global disconnect endpoint redirects, so this row may legitimately stay a
        // plain form submitted through the callback-less confirmDelete(this) -- a future change
        // must not quietly convert it to the manual row's data-attribute/button style too
        assertThat(html).contains("<form th:unless=\"${manual}\"");
        assertThat(html).contains("'/fulfilment/supplier/' + ${row.identity()} + '/disconnect'");
        assertThat(html).contains("onclick=\"confirmDelete(this)\"");
    }

    @Test
    void bothDestructiveControlsCarryAConfirmationMessageAttribute() throws Exception {
        String html = fragment();

        // each destructive action names exactly what it destroys, instead of sharing the modal's
        // generic default text
        assertThat(html).contains("data-confirm-message=#{store.manual.delete.confirm}");
        assertThat(html).contains("data-confirm-message=#{store.supplier.disconnect.confirm}");
    }
}
