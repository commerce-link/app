package pl.commercelink.web.fulfilment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkippedGroupsTest {

    @Test
    void nothingSkippedHasNoGroupsAndNoWayBack() {
        // when
        SkippedGroups skipped = SkippedGroups.from(List.of(), null);

        // then
        assertThat(skipped.count()).isZero();
        assertThat(skipped.orderCount()).isZero();
        assertThat(skipped.backHref()).isNull();
    }

    @Test
    void sizesSplitTheOrdersIntoGroupsInSkipOrder() {
        // when
        SkippedGroups skipped = SkippedGroups.from(List.of("w1", "w2", "w3", "d1"), "3,1");

        // then
        assertThat(skipped.count()).isEqualTo(2);
        assertThat(skipped.orderCount()).isEqualTo(4);
        assertThat(skipped.sizesParam()).isEqualTo("3,1");
    }

    @Test
    void goingBackDropsTheLastSkippedGroup() {
        // given
        SkippedGroups skipped = SkippedGroups.from(List.of("w1", "w2", "w3", "d1", "d2"), "3,1,1");

        // when
        String back = skipped.backHref();

        // then
        assertThat(back).isEqualTo("/dashboard/fulfilment/queue?orderIds=w1&orderIds=w2&orderIds=w3&orderIds=d1&skippedGroups=3,1");
    }

    @Test
    void goingBackFromOneSkippedGroupReturnsToTheBarePage() {
        // when
        SkippedGroups skipped = SkippedGroups.from(List.of("w1", "w2"), "2");

        // then
        assertThat(skipped.backHref()).isEqualTo("/dashboard/fulfilment/queue");
    }

    @Test
    void skippingAGroupAppendsOnlyOrdersNotSkippedYet() {
        // given
        SkippedGroups skipped = SkippedGroups.from(List.of("w1", "w2"), "2");

        // when
        SkippedGroups next = skipped.plus(List.of("w2", "d1"));

        // then
        assertThat(next.orderIds()).containsExactly("w1", "w2", "d1");
        assertThat(next.sizesParam()).isEqualTo("2,1");
    }

    @Test
    void aGroupWithNothingNewAddsNoEmptyGroup() {
        // given
        SkippedGroups skipped = SkippedGroups.from(List.of("w1"), "1");

        // when
        SkippedGroups next = skipped.plus(List.of("w1"));

        // then
        assertThat(next.sizesParam()).isEqualTo("1");
    }

    @Test
    void anAddressWithoutValidSizesCountsAllSkippedOrdersAsOneGroup() {
        // when
        SkippedGroups missing = SkippedGroups.from(List.of("a", "b", "c"), null);
        SkippedGroups wrongSum = SkippedGroups.from(List.of("a", "b", "c"), "1,1");
        SkippedGroups garbage = SkippedGroups.from(List.of("a", "b", "c"), "x,0");

        // then
        assertThat(List.of(missing, wrongSum, garbage)).allSatisfy(skipped -> {
            assertThat(skipped.count()).isEqualTo(1);
            assertThat(skipped.sizesParam()).isEqualTo("3");
            assertThat(skipped.backHref()).isEqualTo("/dashboard/fulfilment/queue");
        });
    }

    @Test
    void repeatedOrderIdsAreCountedOnce() {
        // when
        SkippedGroups skipped = SkippedGroups.from(List.of("a", "b", "a"), null);

        // then
        assertThat(skipped.orderIds()).containsExactly("a", "b");
        assertThat(skipped.sizesParam()).isEqualTo("2");
    }

    @Test
    void theQueueAddressCarriesTheWholeSkipState() {
        // given
        SkippedGroups skipped = SkippedGroups.from(List.of("w1", "w2", "d1"), "2,1");

        // when
        String href = skipped.queueHref();

        // then
        assertThat(href).isEqualTo("/dashboard/fulfilment/queue?orderIds=w1&orderIds=w2&orderIds=d1&skippedGroups=2,1");
    }

    @Test
    void withNothingSkippedTheQueueAddressIsTheBarePath() {
        // when
        String href = SkippedGroups.from(List.of(), null).queueHref();

        // then
        assertThat(href).isEqualTo("/dashboard/fulfilment/queue");
    }
}
