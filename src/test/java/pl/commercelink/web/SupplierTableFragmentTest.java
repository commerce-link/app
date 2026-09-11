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
        assertThat(html).contains("${row.feedLastModified}");
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
}
