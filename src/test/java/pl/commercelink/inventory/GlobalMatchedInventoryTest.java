package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalMatchedInventoryTest {

    private final GlobalMatchedInventory inventory = new GlobalMatchedInventory();

    private InventoryItem item(String supplier) {
        return new InventoryItem("4711111111111", "MFN", 10.0, "PLN", 1, 1, supplier, true, true, false);
    }

    private MatchedInventory matchedWith(InventoryItem... items) {
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.getInventoryItems()).thenReturn(List.of(items));
        return matched;
    }

    private MatchedInventory keyed(String ean, String mfn) {
        return new MatchedInventory(new InventoryKey(ean, mfn), null, null);
    }

    @Test
    void startsEmpty() {
        // then
        assertEquals(0, inventory.size());
        assertTrue(inventory.all().isEmpty());
        assertTrue(inventory.allItems().isEmpty());
    }

    @Test
    void replaceSwapsState() {
        // when
        inventory.replace(List.of(matchedWith(item("Action"))));

        // then
        assertEquals(1, inventory.size());
        assertEquals(1, inventory.allItems().size());
    }

    @Test
    void allItemsFlattensEveryMatchedEntry() {
        // given
        inventory.replace(List.of(
                matchedWith(item("Action"), item("Wortmann")),
                matchedWith(item("Elko"))));

        // when / then
        assertEquals(3, inventory.allItems().size());
    }

    @Test
    void indexStartsEmpty() {
        // when / then
        assertTrue(inventory.index().all().isEmpty());
    }

    @Test
    void indexReflectsReplacedData() {
        // given
        MatchedInventory group = keyed("5901234567890", "MFN-1");
        inventory.replace(List.of(group));

        // when / then
        assertThat(inventory.index().findMatching(new InventoryKey("5901234567890", "OTHER"))).containsExactly(group);
    }

    @Test
    void indexIsRebuiltAfterReplace() {
        // given
        inventory.replace(List.of(keyed("5901234567890", "MFN-1")));
        inventory.index();
        MatchedInventory replacement = keyed("4000000000009", "MFN-2");
        inventory.replace(List.of(replacement));

        // when / then
        assertThat(inventory.index().all()).containsExactly(replacement);
    }

    @Test
    void generationCarriesTheVariantCodesOfItsReload() {
        // given
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(
                new InventoryItem("Nike", null, "5901234567801", "AIR1", "X", 10.0, "PLN", 1, 1, true, false, false),
                new InventoryItem("Nike", null, "5901234567802", "AIR1", "X", 10.0, "PLN", 1, 1, true, false, false)))
                .variantCodes();
        MatchedInventory group = keyed("5901234567890", "MFN-1");

        // when
        inventory.replace(List.of(group), variantCodes);

        // then
        GlobalMatchedInventory.Generation generation = inventory.generation();
        assertThat(generation.variantCodes()).isSameAs(variantCodes);
        assertThat(generation.all()).containsExactly(group);
    }

    @Test
    void replaceWithoutCodesMeansNone() {
        // given
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(
                new InventoryItem("Nike", null, "5901234567801", "AIR1", "X", 10.0, "PLN", 1, 1, true, false, false),
                new InventoryItem("Nike", null, "5901234567802", "AIR1", "X", 10.0, "PLN", 1, 1, true, false, false)))
                .variantCodes();
        inventory.replace(List.of(keyed("5901234567890", "MFN-1")), variantCodes);

        // when
        inventory.replace(List.of(keyed("4000000000009", "MFN-2")));

        // then
        assertThat(inventory.generation().variantCodes()).isSameAs(VariantCodes.none());
    }

    @Test
    void generationFlattensTheOffersOfItsGroups() {
        // given
        InventoryItem action = item("Action");
        InventoryItem elko = item("Elko");
        inventory.replace(List.of(
                new MatchedInventory(new InventoryKey("4711111111111", "MFN"), List.of(action), null, null),
                new MatchedInventory(new InventoryKey("4711111111112", "MFN-2"), List.of(elko), null, null)));

        // when / then
        assertThat(inventory.generation().allItems()).containsExactlyInAnyOrder(action, elko);
    }

    @Test
    void takingAGenerationDoesNotBuildTheIndex() {
        // given
        MatchedInventory group = mock(MatchedInventory.class);
        inventory.replace(List.of(group));

        // when
        inventory.generation();

        // then: building the index reads every group's key
        verify(group, never()).getInventoryKey();
    }

    @Test
    void currentGenerationUsesTheSharedIndex() {
        // given
        inventory.replace(List.of(keyed("5901234567890", "MFN-1")));
        GlobalMatchedInventory.Generation generation = inventory.generation();

        // when
        InventoryIndex index = generation.index();

        // then
        assertThat(index).isSameAs(inventory.index());
        assertThat(generation.index()).isSameAs(index);
    }

    @Test
    void olderGenerationKeepsAnIndexOfItsOwnGroups() {
        // given
        MatchedInventory older = keyed("5901234567890", "MFN-1");
        MatchedInventory newer = keyed("4000000000009", "MFN-2");
        inventory.replace(List.of(older));
        GlobalMatchedInventory.Generation generation = inventory.generation();

        // when
        inventory.replace(List.of(newer));

        // then
        assertThat(generation.index().all()).containsExactly(older);
        assertThat(inventory.index().all()).containsExactly(newer);
    }

    @Test
    void generationsOfOneReloadShareTheIndexAfterTheNextReload() {
        // given
        inventory.replace(List.of(keyed("5901234567890", "MFN-1")));
        GlobalMatchedInventory.Generation first = inventory.generation();
        GlobalMatchedInventory.Generation second = inventory.generation();

        // when
        inventory.replace(List.of(keyed("4000000000009", "MFN-2")));

        // then
        assertThat(first.index()).isSameAs(second.index());
        assertThat(first.index()).isNotSameAs(inventory.index());
    }

    @Test
    void supersededIndexIsBuiltWithoutHoldingTheInventoryLock() throws Exception {
        // given: building the old index blocks inside the first group's key until released
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MatchedInventory blocking = mock(MatchedInventory.class);
        InventoryKey key = new InventoryKey("5901234567890", "MFN-1");
        when(blocking.getInventoryKey()).thenAnswer(inv -> {
            building.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return key;
        });
        inventory.replace(List.of(blocking));
        GlobalMatchedInventory.Generation superseded = inventory.generation();
        inventory.replace(List.of(keyed("4000000000009", "MFN-2")));
        CompletableFuture<InventoryIndex> oldIndex = CompletableFuture.supplyAsync(superseded::index);
        assertTrue(building.await(5, TimeUnit.SECONDS));

        // when: a reload, a generation and the current index go through while the old index is being built
        CompletableFuture.runAsync(() -> {
            inventory.replace(List.of(keyed("4000000000016", "MFN-3")));
            inventory.generation().index();
            inventory.index();
        }).get(5, TimeUnit.SECONDS);
        release.countDown();

        // then
        assertThat(oldIndex.get(5, TimeUnit.SECONDS).all()).containsExactly(blocking);
    }

    @Test
    void generationWithoutAnIndexFailsAtConstruction() {
        // when / then
        assertThatThrownBy(() -> new GlobalMatchedInventory.Generation(1, List.of(), (InventoryIndex) null, VariantCodes.none()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("index");
    }

    @Test
    void indexSourceReturningNothingFailsTheSameWayOnEveryCall() {
        // given
        GlobalMatchedInventory.Generation generation = new GlobalMatchedInventory.Generation(1, List.of(), () -> null, VariantCodes.none());

        // when / then
        assertThatThrownBy(generation::index).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(generation::index).isInstanceOf(IllegalStateException.class);
    }
}
