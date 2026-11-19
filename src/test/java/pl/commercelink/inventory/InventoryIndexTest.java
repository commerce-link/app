package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class InventoryIndexTest {

    @Mock
    private SupplierRegistry supplierRegistry;

    private MatchedInventory group(InventoryKey key) {
        return new MatchedInventory(key, supplierRegistry);
    }

    @Test
    void findsGroupByEan() {
        // given
        MatchedInventory matched = group(new InventoryKey("5901234567890", "MFN-1"));
        InventoryIndex index = InventoryIndex.of(List.of(matched));

        // when / then
        assertThat(index.findMatching(new InventoryKey("5901234567890", "OTHER"))).containsExactly(matched);
    }

    @Test
    void keyWithEanFindsNoGroupByCode() {
        // given
        InventoryIndex index = InventoryIndex.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(index.findMatching(new InventoryKey("0000000000000", "MFN-1"))).isEmpty();
    }

    @Test
    void containsIgnoresTheCodeOfAKeyWithEan() {
        // given
        InventoryIndex index = InventoryIndex.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(index.contains(new InventoryKey("0000000000000", "MFN-1"))).isFalse();
    }

    @Test
    void findsGroupByPimId() {
        // given
        MatchedInventory matched = group(new InventoryKey("PIM-1"));
        InventoryIndex index = InventoryIndex.of(List.of(matched));

        // when / then
        assertThat(index.findMatching(new InventoryKey("PIM-1"))).containsExactly(matched);
    }

    @Test
    void returnsEachMatchingGroupOnce() {
        // given
        InventoryKey key = new InventoryKey("5901234567890", "MFN-1");
        MatchedInventory matched = group(key);
        InventoryIndex index = InventoryIndex.of(List.of(matched));

        // when / then
        assertThat(index.findMatching(new InventoryKey("5901234567890", "MFN-1"))).containsExactly(matched);
    }

    @Test
    void returnsAllGroupsSharingTheLookupEan() {
        // given
        MatchedInventory first = group(new InventoryKey("5901234567890", "MFN-1"));
        MatchedInventory second = group(new InventoryKey("5901234567890", "MFN-2"));
        InventoryIndex index = InventoryIndex.of(List.of(first, second));

        // when / then
        assertThat(index.findMatching(InventoryKey.fromEan("5901234567890"))).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        // given
        InventoryIndex index = InventoryIndex.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(index.findMatching(new InventoryKey("0000000000000", "OTHER"))).isEmpty();
    }

    @Test
    void exposesIndexedGroupsInOrder() {
        // given
        MatchedInventory first = group(new InventoryKey("5901234567890", "MFN-1"));
        MatchedInventory second = group(new InventoryKey("4000000000009", "MFN-2"));
        InventoryIndex index = InventoryIndex.of(List.of(first, second));

        // when / then
        assertThat(index.all()).containsExactly(first, second);
    }

    @Test
    void containsReflectsWhetherAnyGroupMatches() {
        // given
        InventoryIndex index = InventoryIndex.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(index.contains(new InventoryKey("5901234567890", "OTHER"))).isTrue();
        assertThat(index.contains(new InventoryKey("0000000000000", "OTHER"))).isFalse();
    }

    private static final String GLOBAL_EAN = "8720689010825";
    private static final String OWN_EAN = "8720689021180";

    private MatchedInventory philipsGlobal() {
        return group(new InventoryKey(GLOBAL_EAN, "QP1324/20"));
    }

    private MatchedInventory philipsOwn() {
        return group(new InventoryKey(OWN_EAN, "QP1324/20"));
    }

    @Test
    void linkedOwnGroupIsFoundByTheGlobalKey() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();
        InventoryIndex ownIndex = InventoryIndex.of(List.of(own));

        // when
        ownIndex.link(own, global.getInventoryKey().getProductEans());

        // then
        assertThat(ownIndex.findMatching(global.getInventoryKey())).containsExactly(own);
        assertThat(ownIndex.contains(global.getInventoryKey())).isTrue();
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(GLOBAL_EAN);
    }

    @Test
    void globalIndexCoversALinkedOwnKey() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();
        InventoryIndex globalIndex = InventoryIndex.of(List.of(global));
        InventoryIndex ownIndex = InventoryIndex.of(List.of(own));
        assertThat(globalIndex.covers(own.getInventoryKey(), id -> false)).isFalse();

        // when
        ownIndex.link(own, global.getInventoryKey().getProductEans());

        // then
        assertThat(globalIndex.covers(own.getInventoryKey(), id -> false)).isTrue();
        assertThat(globalIndex.contains(own.getInventoryKey())).isTrue();
        assertThat(globalIndex.findMatching(own.getInventoryKey())).containsExactly(global);
    }

    @Test
    void matchesByEanComeBeforeLinkedOnes() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory linked = philipsOwn();
        MatchedInventory sameEan = group(new InventoryKey(GLOBAL_EAN, "OTHER"));
        InventoryIndex ownIndex = InventoryIndex.of(List.of(linked, sameEan));
        ownIndex.link(linked, global.getInventoryKey().getProductEans());

        // when / then
        assertThat(ownIndex.findMatching(global.getInventoryKey())).containsExactly(sameEan, linked);
    }

    @Test
    void indexBuiltFromKeysThatAlreadyCarryALinkFindsThem() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();
        InventoryIndex.of(List.of(own)).link(own, global.getInventoryKey().getProductEans());

        // when
        InventoryIndex rebuilt = InventoryIndex.of(List.of(own));

        // then
        assertThat(rebuilt.findMatching(global.getInventoryKey())).containsExactly(own);
        assertThat(rebuilt.contains(InventoryKey.fromEan(GLOBAL_EAN))).isTrue();
    }

    @Test
    void withoutLinksLookupsAreUnchanged() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();
        InventoryIndex ownIndex = InventoryIndex.of(List.of(own));
        InventoryIndex globalIndex = InventoryIndex.of(List.of(global));

        // when / then
        assertThat(ownIndex.findMatching(global.getInventoryKey())).isEmpty();
        assertThat(ownIndex.contains(global.getInventoryKey())).isFalse();
        assertThat(globalIndex.findMatching(own.getInventoryKey())).isEmpty();
        assertThat(globalIndex.covers(own.getInventoryKey(), id -> false)).isFalse();
        assertThat(ownIndex.findMatching(own.getInventoryKey())).containsExactly(own);
    }

    @Test
    void linkingAGroupOfAnotherIndexIsRejected() {
        // given
        InventoryIndex ownIndex = InventoryIndex.of(List.of(philipsOwn()));

        // when / then
        assertThatThrownBy(() -> ownIndex.link(philipsOwn(), List.of(GLOBAL_EAN)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
