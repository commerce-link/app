package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.pim.ScopedPimCatalog.cloneEntry;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;
import static pl.commercelink.pim.ScopedPimCatalog.storeEntry;

class StorePimAliasesTest {

    private static final String STORE = "s1";
    private static final String OTHER_STORE = "s2";

    private final PimCatalog catalog = ScopedPimCatalog.of(
            globalEntry("S", "5900000000011", "M1", "cat"),
            globalEntry("G", "5900000000022", null, "cat"),
            cloneEntry("C", STORE, "S", "5900000000011", "M1", "cat"),
            storeEntry("X", STORE, "5900000000033", null, "cat"));
    private final StorePimAliases aliases = new StorePimAliases(catalog);

    @Test
    void cloneAndSourceResolveBothWays() {
        // when / then
        assertThat(aliases.cloneOf(STORE, "S")).contains("C");
        assertThat(aliases.sourceOf(STORE, "C")).contains("S");
        assertThat(aliases.aliasesOf(STORE, "C")).containsExactlyInAnyOrder("C", "S");
        assertThat(aliases.aliasesOf(STORE, "S")).containsExactlyInAnyOrder("S", "C");
        assertThat(aliases.aliasesOf(STORE, "G")).containsExactly("G");
        assertThat(aliases.sourceOf(STORE, "X")).isEmpty();
        assertThat(aliases.cloneOf(STORE, "G")).isEmpty();
    }

    @Test
    void otherStoreSeesNoAlias() {
        // when / then
        assertThat(aliases.cloneOf(OTHER_STORE, "S")).isEmpty();
        assertThat(aliases.sourceOf(OTHER_STORE, "C")).isEmpty();
        assertThat(aliases.aliasesOf(OTHER_STORE, "S")).containsExactly("S");
        assertThat(aliases.offerIdOf(OTHER_STORE, "C")).isEqualTo("C");
        assertThat(aliases.effectiveEntry(OTHER_STORE, "S")).map(PimEntry::pimId).contains("S");
    }

    @Test
    void offerIdOfACloneIsTheSource() {
        // when / then
        assertThat(aliases.offerIdOf(STORE, "C")).isEqualTo("S");
        assertThat(aliases.offerIdOf(STORE, "S")).isEqualTo("S");
        assertThat(aliases.offerIdOf(STORE, "X")).isEqualTo("X");
    }

    @Test
    void effectiveEntryPrefersTheStoresClone() {
        // when / then
        assertThat(aliases.effectiveEntry(STORE, "S")).map(PimEntry::pimId).contains("C");
        assertThat(aliases.effectiveEntry(STORE, "C")).map(PimEntry::pimId).contains("C");
        assertThat(aliases.effectiveEntry(STORE, "G")).map(PimEntry::pimId).contains("G");
        assertThat(aliases.effectiveEntry(STORE, "missing")).isEmpty();
    }

    @Test
    void blankStoreOrIdGivesNoAlias() {
        // when / then
        assertThat(aliases.cloneOf(" ", "S")).isEmpty();
        assertThat(aliases.cloneOf(STORE, null)).isEmpty();
        assertThat(aliases.sourceOf(null, "C")).isEmpty();
        assertThat(aliases.sourceOf(STORE, "")).isEmpty();
        assertThat(aliases.aliasesOf(null, "C")).containsExactly("C");
        assertThat(aliases.aliasesOf(STORE, " ")).isEmpty();
        assertThat(aliases.offerIdOf(null, "C")).isEqualTo("C");
        assertThat(aliases.offerIdOf(STORE, null)).isNull();
        assertThat(aliases.effectiveEntry("", "S")).isEmpty();
        assertThat(aliases.effectiveEntry(STORE, null)).isEmpty();
    }
}
