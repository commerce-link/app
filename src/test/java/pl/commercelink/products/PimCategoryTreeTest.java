package pl.commercelink.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.util.List;

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
}
