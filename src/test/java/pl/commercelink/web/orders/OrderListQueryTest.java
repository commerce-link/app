package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.OrderAttention;
import pl.commercelink.orders.OrderStatus;

import static org.assertj.core.api.Assertions.assertThat;

class OrderListQueryTest {

    private static MultiValueMap<String, String> params(String... keyValues) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.add(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void noParamsIsTheOpenViewSortedByDue() {
        OrderListQuery query = OrderListQuery.parse(params());
        assertThat(query.isOpen()).isTrue();
        assertThat(query.filterId()).isNull();
        assertThat(query.effectiveSort()).isEqualTo(OrderListQuery.Sort.DUE);
        assertThat(query.effectiveDir()).isEqualTo(OrderListQuery.Direction.ASC);
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.href()).isEqualTo("/dashboard/orders");
    }

    @Test
    void unknownValuesFallBackToDefaultsAndPageIsClampedToOne() {
        // focus (the clickable tiles) and sort=ordered (the history) are gone: old bookmarks open the plain list
        OrderListQuery query = OrderListQuery.parse(params("status", "Bogus", "focus", "overdue", "sort", "ordered", "dir", "z", "page", "abc"));
        assertThat(query.statuses()).isEmpty();
        assertThat(query.href()).isEqualTo("/dashboard/orders");
        assertThat(query.sort()).isNull();
        assertThat(query.page()).isEqualTo(1);
        assertThat(OrderListQuery.parse(params("page", "0")).page()).isEqualTo(1);
        assertThat(OrderListQuery.parse(params("page", "-4")).page()).isEqualTo(1);
    }

    @Test
    void closedStatusesInTheAddressAreIgnored() {
        // Completed and Cancelled are not part of the list: old bookmarks open the open list, due first
        OrderListQuery query = OrderListQuery.parse(params("status", "completed"));
        assertThat(query.statuses()).isEmpty();
        assertThat(query.isOpen()).isTrue();
        assertThat(query.effectiveSort()).isEqualTo(OrderListQuery.Sort.DUE);
        assertThat(OrderListQuery.parse(params("status", "Completed", "status", "New")).statuses()).containsExactly(OrderStatus.New);
    }

    @Test
    void emptyFilterIdMeansNoFilter() {
        // the start filter's "filterId=" marker is gone; an old link with it opens the clean list
        OrderListQuery query = OrderListQuery.parse(params("filterId", ""));
        assertThat(query.hasFilter()).isFalse();
        assertThat(query.href()).isEqualTo("/dashboard/orders");
    }

    @Test
    void hrefKeepsEveryParameterAndResetsPageOnChange() {
        OrderListQuery query = OrderListQuery.parse(params("status", "New", "filterId", "f1",
                "q", "kowalski", "sort", "amount", "dir", "desc", "page", "3"));
        assertThat(query.href()).isEqualTo("/dashboard/orders?status=New&filterId=f1&q=kowalski&sort=amount&dir=desc&page=3");
        assertThat(query.withStatus(OrderStatus.Blocked).href()).startsWith("/dashboard/orders?status=Blocked&filterId=f1&q=kowalski&sort=amount&dir=desc").doesNotContain("page=");
        assertThat(query.withStatus(null).href()).doesNotContain("status=");
        assertThat(query.withQ(null).href()).doesNotContain("q=");
        assertThat(query.withFilterId(null).href()).doesNotContain("filterId=");
        assertThat(query.withPage(2).href()).endsWith("page=2");
    }

    @Test
    void toggleSortFlipsDirectionOnTheSameColumnAndStartsAscendingOnAnother() {
        OrderListQuery query = OrderListQuery.parse(params("sort", "amount", "dir", "asc"));
        assertThat(query.toggleSort(OrderListQuery.Sort.AMOUNT).effectiveDir()).isEqualTo(OrderListQuery.Direction.DESC);
        assertThat(query.toggleSort(OrderListQuery.Sort.NUMBER).effectiveDir()).isEqualTo(OrderListQuery.Direction.ASC);
        assertThat(query.toggleSort(OrderListQuery.Sort.NUMBER).sort()).isEqualTo(OrderListQuery.Sort.NUMBER);
        // default DUE ASC on the open view toggles to DESC
        assertThat(OrderListQuery.parse(params()).toggleSort(OrderListQuery.Sort.DUE).effectiveDir()).isEqualTo(OrderListQuery.Direction.DESC);
    }

    @Test
    void searchTextIsTrimmedCappedAndUrlEncoded() {
        String longText = "a".repeat(150);
        OrderListQuery query = OrderListQuery.parse(params("q", "  Jan (Kowalski) <b>&x  "));
        assertThat(query.q()).isEqualTo("Jan (Kowalski) <b>&x");
        assertThat(query.href()).isEqualTo("/dashboard/orders?q=Jan+%28Kowalski%29+%3Cb%3E%26x");
        assertThat(OrderListQuery.parse(params("q", longText)).q()).hasSize(100);
        assertThat(OrderListQuery.parse(params("q", "   ")).q()).isNull();
    }

    @Test
    void returnToFallsBackToThePathWhenTooLong() {
        OrderListQuery query = OrderListQuery.parse(params("q", "x".repeat(100), "filterId", "f".repeat(100), "status", "New"));
        assertThat(query.href().length()).isGreaterThan(200);
        assertThat(query.returnTo()).isEqualTo(query.href());
        OrderListQuery longer = query.withFilterId("f".repeat(250));
        assertThat(longer.returnTo()).isEqualTo("/dashboard/orders");
    }

    @Test
    void legacyStatusesAndShowAllRedirect() {
        assertThat(OrderListQuery.legacyRedirect(params("statuses", "Blocked", "statuses", "New")))
                .contains("/dashboard/orders?status=New&status=Blocked");
        assertThat(OrderListQuery.legacyRedirect(params("showAll", "true"))).contains("/dashboard/orders");
        assertThat(OrderListQuery.legacyRedirect(params("showAll", "true", "filterId", "f1"))).contains("/dashboard/orders?filterId=f1");
        assertThat(OrderListQuery.legacyRedirect(params("statuses", "Bogus"))).contains("/dashboard/orders");
        assertThat(OrderListQuery.legacyRedirect(params("status", "New"))).isEmpty();
    }

    @Test
    void severalStatusesAreTickedTogetherAndKeptInEnumOrder() {
        OrderListQuery query = OrderListQuery.parse(params("status", "Blocked", "status", "New", "status", "new", "status", "Bogus"));
        assertThat(query.statuses()).containsExactly(OrderStatus.New, OrderStatus.Blocked);
        assertThat(query.href()).isEqualTo("/dashboard/orders?status=New&status=Blocked");
        assertThat(query.single()).isEmpty();
        assertThat(query.isOpen()).isFalse();
        assertThat(OrderListQuery.parse(params("status", "New,Blocked")).statuses()).containsExactly(OrderStatus.New, OrderStatus.Blocked);
        assertThat(query.toggleStatus(OrderStatus.Blocked).statuses()).containsExactly(OrderStatus.New);
        assertThat(query.toggleStatus(OrderStatus.Assembly).statuses()).containsExactly(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly);
        assertThat(query.withStatus(OrderStatus.Assembled).single()).contains(OrderStatus.Assembled);
    }
}
