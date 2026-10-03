package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.orders.FulfilmentStatus.*;

class WarehouseListQueryTest {

    private static LinkedMultiValueMap<String, String> params(String... pairs) {
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.add(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void noParametersMeanInStockOrOrderedWithExternalWms() {
        // given
        LinkedMultiValueMap<String, String> none = params();

        // when
        WarehouseListQuery own = WarehouseListQuery.parse(none, false);
        WarehouseListQuery external = WarehouseListQuery.parse(none, true);

        // then
        assertThat(own.statuses()).containsExactly(Delivered);
        assertThat(external.statuses()).containsExactly(Ordered);
        assertThat(own.href()).isEqualTo("/dashboard/warehouse");
        assertThat(own.isFiltered()).isFalse();
    }

    @Test
    void theStoreDecidesTheWmsFlagOneWayForEveryController() {
        // given
        Store withWms = mock(Store.class);
        when(withWms.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(true);
        Store withoutWms = mock(Store.class);

        // when / then
        assertThat(WarehouseListQuery.usesWms(withWms)).isTrue();
        assertThat(WarehouseListQuery.usesWms(withoutWms)).isFalse();
        assertThat(WarehouseListQuery.usesWms(null)).isFalse();
        assertThat(WarehouseListQuery.parse(params(), withWms)).isEqualTo(WarehouseListQuery.parse(params(), true));
        assertThat(WarehouseListQuery.parse(params(), withoutWms)).isEqualTo(WarehouseListQuery.parse(params(), false));
    }

    @Test
    void oldAddressesKeepWorking() {
        // when
        WarehouseListQuery reserved = WarehouseListQuery.parse(params("statuses", "Reserved"), false);
        WarehouseListQuery showAll = WarehouseListQuery.parse(params("showAll", "true"), false);
        WarehouseListQuery categories = WarehouseListQuery.parse(params("categories", "GPU", "categories", "CPU"), false);

        // then
        assertThat(reserved.statuses()).containsExactly(Reserved);
        assertThat(reserved.href()).isEqualTo("/dashboard/warehouse?statuses=Reserved");
        assertThat(showAll.isAllStatuses()).isTrue();
        assertThat(categories.categories()).containsExactly("GPU", "CPU");
    }

    @Test
    void allMeansEveryVisibleStatusInListOrder() {
        // when
        WarehouseListQuery all = WarehouseListQuery.parse(params("statuses", "all"), false);
        WarehouseListQuery allWithWms = WarehouseListQuery.parse(params("statuses", "all"), true);

        // then
        assertThat(all.statuses()).containsExactly(Delivered, Reserved, InRMA, InExternalService, New, Allocation, Ordered);
        assertThat(all.href()).isEqualTo("/dashboard/warehouse?statuses=all");
        assertThat(allWithWms.statuses()).containsExactly(New, Allocation, Ordered);
    }

    @Test
    void unknownValuesFallBackToDefaults() {
        // when
        WarehouseListQuery q = WarehouseListQuery.parse(params("statuses", "Foo", "statuses", "Destroyed",
                "page", "-3", "sort", "x", "dir", "sideways", "q", "   "), false);

        // then
        assertThat(q.statuses()).containsExactly(Delivered);
        assertThat(q.page()).isEqualTo(1);
        assertThat(q.effectiveSort()).isEqualTo(WarehouseListQuery.Sort.CATEGORY);
        assertThat(q.effectiveDir()).isEqualTo(WarehouseListQuery.Direction.ASC);
        assertThat(q.q()).isNull();
    }

    @Test
    void untickingTheLastStatusShowsAll() {
        // given
        WarehouseListQuery query = WarehouseListQuery.parse(params(), false);

        // when
        WarehouseListQuery q = query.toggleStatus(Delivered);

        // then
        assertThat(q.isAllStatuses()).isTrue();
    }

    @Test
    void togglingKeepsOtherFiltersAndResetsPage() {
        // given
        WarehouseListQuery query = WarehouseListQuery.parse(params("categories", "GPU", "q", "rtx", "page", "3"), false);

        // when
        WarehouseListQuery q = query.toggleStatus(Reserved);

        // then
        assertThat(q.statuses()).containsExactly(Delivered, Reserved);
        assertThat(q.categories()).containsExactly("GPU");
        assertThat(q.q()).isEqualTo("rtx");
        assertThat(q.page()).isEqualTo(1);
        assertThat(q.href()).isEqualTo("/dashboard/warehouse?statuses=Delivered&statuses=Reserved&categories=GPU&q=rtx");
    }

    @Test
    void tileReplacesEveryOtherNarrowing() {
        // given
        WarehouseListQuery query = WarehouseListQuery.parse(params("categories", "GPU", "q", "rtx"), false);

        // when
        WarehouseListQuery q = query.withStatuses(List.of(InRMA, InExternalService));

        // then
        assertThat(q.statuses()).containsExactly(InRMA, InExternalService);
        assertThat(q.categories()).isEmpty();
        assertThat(q.q()).isNull();
    }

    @Test
    void clearedGoesBackToTheDefaultView() {
        // given
        WarehouseListQuery query = WarehouseListQuery.parse(params("statuses", "all", "categories", "none", "q", "x"), false);

        // when
        WarehouseListQuery q = query.cleared();

        // then
        assertThat(q.href()).isEqualTo("/dashboard/warehouse");
    }

    @Test
    void categoryValuesAreEncodedAndSortToggles() {
        // given
        WarehouseListQuery q = WarehouseListQuery.parse(params(), false).toggleCategory("Płyty główne");

        // when
        WarehouseListQuery sorted = q.toggleSort(WarehouseListQuery.Sort.QTY);
        WarehouseListQuery sortedAgain = sorted.toggleSort(WarehouseListQuery.Sort.QTY);

        // then
        assertThat(q.href()).isEqualTo("/dashboard/warehouse?categories=P%C5%82yty+g%C5%82%C3%B3wne");
        assertThat(sorted.effectiveDir()).isEqualTo(WarehouseListQuery.Direction.ASC);
        assertThat(sortedAgain.effectiveDir()).isEqualTo(WarehouseListQuery.Direction.DESC);
    }

    @Test
    void statusHelpers() {
        // when / then
        assertThat(WarehouseStatuses.tone(Delivered)).isEqualTo("is-ok");
        assertThat(WarehouseStatuses.tone(InRMA)).isEqualTo("is-warn");
        assertThat(WarehouseStatuses.tone(New)).isEqualTo("is-neutral");
        assertThat(WarehouseStatuses.tone(Ordered)).isEqualTo("is-info");
        assertThat(WarehouseStatuses.selectable(Ordered)).isFalse();
        assertThat(WarehouseStatuses.selectable(Allocation)).isFalse();
        assertThat(WarehouseStatuses.selectable(New)).isTrue();
        assertThat(WarehouseStatuses.labelKey(InExternalService)).isEqualTo("warehouse.status.InExternalService");
    }

    @Test
    void untickingTheLastStatusInTheMenuMeansAllStatuses() {
        // given
        LinkedMultiValueMap<String, String> menuWithNothingTicked = params("statusesMenu", "1", "categories", "GPU");

        // when
        WarehouseListQuery own = WarehouseListQuery.parse(menuWithNothingTicked, false);
        WarehouseListQuery external = WarehouseListQuery.parse(menuWithNothingTicked, true);

        // then
        assertThat(own.isAllStatuses()).isTrue();
        assertThat(external.statuses()).containsExactly(New, Allocation, Ordered);
        assertThat(own.href()).isEqualTo("/dashboard/warehouse?statuses=all&categories=GPU");
    }

    @Test
    void statusMenuWithTickedStatusesKeepsThemAndTheMarkerStaysOutOfLinks() {
        // when
        WarehouseListQuery query = WarehouseListQuery.parse(params("statusesMenu", "1", "statuses", "Reserved"), false);

        // then
        assertThat(query.statuses()).containsExactly(Reserved);
        assertThat(query.href()).isEqualTo("/dashboard/warehouse?statuses=Reserved");
        assertThat(query.toggleStatus(InRMA).href()).doesNotContain("statusesMenu");
    }

    @Test
    void oldUncategorizedBookmarkMeansNoCategory() {
        // when
        WarehouseListQuery query = WarehouseListQuery.parse(params("categories", "Uncategorized", "categories", "none", "categories", "GPU"), false);

        // then
        assertThat(query.categories()).containsExactly("none", "GPU");
        assertThat(query.href()).isEqualTo("/dashboard/warehouse?categories=none&categories=GPU");
    }

    @Test
    void afterAnActionOnlyTheStatusChangesAndTheListStartsOnItsFirstPage() {
        // given
        WarehouseListQuery query = WarehouseListQuery.parse(params("statuses", "Delivered", "categories", "GPU", "q", "rtx",
                "sort", "qty", "dir", "desc", "page", "3"), false);

        // when
        WarehouseListQuery after = query.afterAction(Reserved);

        // then
        assertThat(after.href()).isEqualTo("/dashboard/warehouse?statuses=Reserved&categories=GPU&q=rtx&sort=qty&dir=desc");
    }
}
