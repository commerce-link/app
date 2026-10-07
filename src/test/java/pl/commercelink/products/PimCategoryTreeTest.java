package pl.commercelink.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PimCategoryTreeTest {

    private PimCatalog pimCatalog;
    private PimCategoryTree tree;

    @BeforeEach
    void setUp() {
        pimCatalog = mock(PimCatalog.class);
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Komponenty komputerowe", "pl"),
                new PimCategory("11", "10", "Karty graficzne", "pl"),
                new PimCategory("12", "10", "Dyski SSD", "pl"),
                new PimCategory("20", null, "Akcesoria", "pl"),
                new PimCategory("10", null, "Computer components", "en")));
        tree = new PimCategoryTree(pimCatalog);
    }

    @Test
    void pathNamesRunFromTopLevelToTheNodeInPolish() {
        // when
        List<String> path = tree.pathNames("11");

        // then
        assertThat(path).containsExactly("Komponenty komputerowe", "Karty graficzne");
    }

    @Test
    void unknownIdHasNoPathNoChildrenAndNoSiblings() {
        // when / then
        assertThat(tree.pathNames("999")).isEmpty();
        assertThat(tree.childrenOf("999")).isEmpty();
        assertThat(tree.siblingsOf("999")).isEmpty();
        assertThat(tree.selfAndDescendants("999")).isEmpty();
    }

    @Test
    void childrenAndTopLevelsAreSortedByPolishName() {
        // when / then
        assertThat(tree.childrenOf("10")).extracting(PimCategory::name).containsExactly("Dyski SSD", "Karty graficzne");
        assertThat(tree.topLevels()).extracting(PimCategory::name).containsExactly("Akcesoria", "Komponenty komputerowe");
    }

    @Test
    void siblingsOfLeafAreChildrenOfItsParentAndOfTopLevelAreTopLevels() {
        // when / then
        assertThat(tree.siblingsOf("11")).extracting(PimCategory::id).containsExactly("12", "11");
        assertThat(tree.siblingsOf("10")).extracting(PimCategory::id).containsExactly("20", "10");
    }

    @Test
    void selfAndDescendantsIncludesTheWholeSubtree() {
        // when / then
        assertThat(tree.selfAndDescendants("10")).containsExactlyInAnyOrder("10", "11", "12");
        assertThat(tree.selfAndDescendants("11")).containsExactly("11");
    }

    @Test
    void treeIsBuiltOnceAndReusedWithinTheCacheWindow() {
        // when
        tree.pathNames("11");
        tree.childrenOf("10");

        // then
        verify(pimCatalog, times(1)).allCategories();
    }

    @Test
    void categoryWhoseParentIsMissingIsUnknownWithItsSubtree() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Komponenty komputerowe", "pl"),
                new PimCategory("30", "99", "Sierota", "pl"),
                new PimCategory("31", "30", "Dziecko sieroty", "pl"),
                new PimCategory("40", "41", "Cykl A", "pl"),
                new PimCategory("41", "40", "Cykl B", "pl")));
        PimCategoryTree orphans = new PimCategoryTree(pimCatalog);

        // when / then
        assertThat(orphans.find("30")).isEmpty();
        assertThat(orphans.find("31")).isEmpty();
        assertThat(orphans.find("40")).isEmpty();
        assertThat(orphans.childrenOf("30")).isEmpty();
        assertThat(orphans.selfAndDescendants("30")).isEmpty();
        assertThat(orphans.find("10")).isPresent();
    }

    @Test
    void emptyTreeIsAskedForAgainAfterSecondsWhileAFullOneIsKeptForMinutes() {
        // given
        AtomicLong nanos = new AtomicLong();
        when(pimCatalog.allCategories()).thenReturn(List.of())
                .thenReturn(List.of(new PimCategory("10", null, "Komponenty komputerowe", "pl")));
        PimCategoryTree ticking = new PimCategoryTree(pimCatalog, nanos::get);

        // when
        boolean emptyFirst = ticking.topLevels().isEmpty();
        nanos.addAndGet(Duration.ofSeconds(11).toNanos());
        List<PimCategory> afterRetry = ticking.topLevels();
        nanos.addAndGet(Duration.ofMinutes(5).toNanos());
        ticking.topLevels();

        // then
        assertThat(emptyFirst).isTrue();
        assertThat(afterRetry).extracting(PimCategory::id).containsExactly("10");
        verify(pimCatalog, times(2)).allCategories();
    }
}
