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
    void showsTheFeedTimestampForEveryModeAndTheMissingStateOtherwise() throws Exception {
        String html = fragment();
        // a global connection's feed lives in the platform-wide bucket rather than the store's
        // own namespace, but the row no longer distinguishes it from own/manual - hasFeed() is
        // the single source of truth for whether a timestamp exists at all
        assertThat(html).contains("#{store.supplier.feed.missing}");
        assertThat(html).doesNotContain("store.supplier.feed.global");
        assertThat(html).doesNotContain("row.isGlobal()");
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
    void offersEditAndRemoveActionsPerRow() throws Exception {
        String html = fragment();
        assertThat(html).contains("data-configure-supplier");
        // neither destructive action submits inline any more (a plain onclick="confirmDelete(this)"
        // cannot be intercepted for a fetch call) -- both carry their identity on a data attribute
        // instead, and store-fulfilment.html wires them up with confirmDelete(button, callback)
        assertThat(html).contains("data-identity=${row.identity()}");
        assertThat(html).doesNotContain("confirmDelete(this)");
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
    void externalRowRendersDisconnectControlAsAButtonNotAForm() throws Exception {
        String html = fragment();

        // disconnecting no longer redirects -- it returns the re-rendered section, like every
        // other mutation on this screen -- so a plain <form> submit would navigate the whole page
        // to raw fragment HTML; this row now carries its identity on a plain button, the same
        // shape the manual row already used, wired up by store-fulfilment.html with a fetch call
        assertThat(html).contains("<button type=\"button\" th:unless=\"${manual}\"");
        assertThat(html).contains("class=\"button is-small is-danger is-outlined external-disconnect-button\"");
        assertThat(html).contains(
                "data-identity=${row.identity()},data-confirm-message=#{store.supplier.disconnect.confirm}");

        // guards against the old shape reappearing: a <form> whose action posts straight to the
        // redirecting disconnect endpoint, submitted via the callback-less confirmDelete(this)
        assertThat(html).doesNotContain("<form th:unless=\"${manual}\"");
        assertThat(html).doesNotContain("'/fulfilment/supplier/' + ${row.identity()} + '/disconnect'");
        assertThat(html).doesNotContain("onclick=\"confirmDelete(this)\"");
    }

    @Test
    void bothDestructiveControlsCarryAConfirmationMessageAttribute() throws Exception {
        String html = fragment();

        // each destructive action names exactly what it destroys, instead of sharing the modal's
        // generic default text
        assertThat(html).contains("data-confirm-message=#{store.manual.delete.confirm}");
        assertThat(html).contains("data-confirm-message=#{store.supplier.disconnect.confirm}");
    }

    @Test
    void labelsTheRowActionEditRatherThanConfigure() throws Exception {
        // the button edits an existing connection, never both add and edit any more, so the key
        // (and its wording) was renamed to match
        String html = fragment();
        assertThat(html).contains("#{store.supplier.action.edit}");
        assertThat(html).doesNotContain("store.supplier.action.configure");
    }

    @Test
    void carriesEachRowsFieldsAsDataAttributesForTheModalScripts() throws Exception {
        // store-fulfilment.html's modal scripts used to read a JS array snapshotted at page load
        // (stale after any async swap); reading these straight off the (possibly just-swapped)
        // <tr> keeps the Edit/config modals correct without a second request
        String html = fragment();
        assertThat(html).contains("data-mode=${row.mode()}");
        assertThat(html).contains("data-include-pricing=${row.includeInPricing()}");
        assertThat(html).contains("data-include-fulfilment=${row.includeInFulfilment()}");
        assertThat(html).contains("data-label=${row.label()}");
        assertThat(html).contains("data-enabled=${row.enabled()}");
        assertThat(html).contains("data-feed-last-modified=${row.feedLastModified()}");
    }
}
