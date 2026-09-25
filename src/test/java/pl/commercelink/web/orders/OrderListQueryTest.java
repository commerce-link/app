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
        OrderListQuery query = OrderListQuery.parse(params("status", "Bogus", "focus", "x", "sort", "y", "dir", "z", "page", "abc"));
        assertThat(query.status()).isNull();
        assertThat(query.focus()).isNull();
        assertThat(query.sort()).isNull();
        assertThat(query.page()).isEqualTo(1);
        assertThat(OrderListQuery.parse(params("page", "0")).page()).isEqualTo(1);
        assertThat(OrderListQuery.parse(params("page", "-4")).page()).isEqualTo(1);
    }

    @Test
    void historyDefaultsToNewestFirst() {
        OrderListQuery query = OrderListQuery.parse(params("status", "completed"));
        assertThat(query.status()).isEqualTo(OrderStatus.Completed);
        assertThat(query.isHistory()).isTrue();
        assertThat(query.effectiveSort()).isEqualTo(OrderListQuery.Sort.ORDERED);
        assertThat(query.effectiveDir()).isEqualTo(OrderListQuery.Direction.DESC);
    }

    @Test
    void emptyFilterIdIsAnExplicitNoFilter() {
        OrderListQuery query = OrderListQuery.parse(params("filterId", ""));
        assertThat(query.hasExplicitNoFilter()).isTrue();
        assertThat(query.hasFilter()).isFalse();
        assertThat(query.href()).isEqualTo("/dashboard/orders?filterId=");
        assertThat(OrderListQuery.parse(params()).hasExplicitNoFilter()).isFalse();
    }

    @Test
    void hrefKeepsEveryParameterAndResetsPageOnChange() {
        OrderListQuery query = OrderListQuery.parse(params("status", "New", "filterId", "f1", "focus", "overdue",
                "q", "kowalski", "sort", "amount", "dir", "desc", "page", "3"));
        assertThat(query.href()).isEqualTo("/dashboard/orders?status=New&filterId=f1&focus=overdue&q=kowalski&sort=amount&dir=desc&page=3");
        assertThat(query.withStatus(OrderStatus.Blocked).href()).startsWith("/dashboard/orders?status=Blocked&filterId=f1&focus=overdue&q=kowalski&sort=amount&dir=desc").doesNotContain("page=");
        assertThat(query.withStatus(null).href()).doesNotContain("status=");
        assertThat(query.withFocus(null).href()).doesNotContain("focus=");
        assertThat(query.withQ(null).href()).doesNotContain("q=");
        assertThat(query.withFilterId("").href()).contains("filterId=&");
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
                .contains("/dashboard/orders?status=Blocked");
        assertThat(OrderListQuery.legacyRedirect(params("showAll", "true"))).contains("/dashboard/orders");
        assertThat(OrderListQuery.legacyRedirect(params("showAll", "true", "filterId", "f1"))).contains("/dashboard/orders?filterId=f1");
        assertThat(OrderListQuery.legacyRedirect(params("statuses", "Bogus"))).contains("/dashboard/orders");
        assertThat(OrderListQuery.legacyRedirect(params("status", "New"))).isEmpty();
    }
}
