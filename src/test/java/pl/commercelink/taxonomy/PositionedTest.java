package pl.commercelink.taxonomy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PositionedTest {

    @Test
    @DisplayName("reindex assigns list indexes overwriting existing positions")
    void reindexAssignsListIndexesOverwritingExistingPositions() {
        // given
        TestItem first = item(null);
        TestItem second = item(7);
        List<TestItem> items = List.of(first, second);

        // when
        Positioned.reindex(items);

        // then
        assertThat(items).extracting(Positioned::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("fillMissing assigns list index only to items without a position and reports a change")
    void fillMissingAssignsListIndexOnlyToItemsWithoutPositionAndReportsChange() {
        // given
        List<TestItem> items = List.of(item(null), item(9), item(null));

        // when
        boolean changed = Positioned.fillMissing(items);

        // then
        assertThat(changed).isTrue();
        assertThat(items).extracting(Positioned::getPosition).containsExactly(0, 9, 2);
    }

    @Test
    @DisplayName("fillMissing reports no change when every item already has a position")
    void fillMissingReportsNoChangeWhenEveryItemAlreadyHasPosition() {
        // given
        List<TestItem> items = List.of(item(2), item(0));

        // when
        boolean changed = Positioned.fillMissing(items);

        // then
        assertThat(changed).isFalse();
        assertThat(items).extracting(Positioned::getPosition).containsExactly(2, 0);
    }

    @Test
    @DisplayName("next returns highest existing position plus one ignoring items without position")
    void nextReturnsHighestExistingPositionPlusOne() {
        // when
        int next = Positioned.next(List.of(item(0), item(5), item(null)));

        // then
        assertThat(next).isEqualTo(6);
    }

    @Test
    @DisplayName("next falls back to list size when no item has a position")
    void nextFallsBackToListSizeWhenNoItemHasPosition() {
        // when
        int next = Positioned.next(List.of(item(null), item(null)));

        // then
        assertThat(next).isEqualTo(2);
    }

    @Test
    @DisplayName("next returns zero for an empty list")
    void nextReturnsZeroForEmptyList() {
        // when
        int next = Positioned.next(List.of());

        // then
        assertThat(next).isEqualTo(0);
    }

    private TestItem item(Integer position) {
        TestItem item = new TestItem();
        item.setPosition(position);
        return item;
    }

    private static class TestItem implements Positioned {

        private Integer position;

        @Override
        public Integer getPosition() {
            return position;
        }

        @Override
        public void setPosition(Integer position) {
            this.position = position;
        }
    }
}
