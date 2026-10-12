package pl.commercelink.warehouse.builtin;

import pl.commercelink.warehouse.api.ItemCondition;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DestroyedListQueryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private static MultiValueMap<String, String> params(String... kv) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < kv.length; i += 2) map.add(kv[i], kv[i + 1]);
        return map;
    }

    @Test
    void bareAddressShowsEverythingNewestFirst() {
        // when
        DestroyedListQuery query = DestroyedListQuery.parse(params());

        // then
        assertThat(query.period()).isEqualTo(DestroyedListQuery.Period.ALL);
        assertThat(query.effectiveSort()).isEqualTo(DestroyedListQuery.Sort.DATE);
        assertThat(query.effectiveDir()).isEqualTo(WarehouseListQuery.Direction.DESC);
        assertThat(query.isFiltered()).isFalse();
        assertThat(query.href()).isEqualTo("/dashboard/warehouse/items/destroyed");
    }

    @Test
    void periodsCoverTheirDays() {
        // given
        DestroyedListQuery month = DestroyedListQuery.parse(params("period", "month"));
        DestroyedListQuery previous = DestroyedListQuery.parse(params("period", "previous"));
        DestroyedListQuery year = DestroyedListQuery.parse(params("period", "year"));

        // when / then
        assertThat(month.includes(LocalDate.of(2026, 10, 1), TODAY)).isTrue();
        assertThat(month.includes(LocalDate.of(2026, 9, 30), TODAY)).isFalse();
        assertThat(previous.includes(LocalDate.of(2026, 9, 1), TODAY)).isTrue();
        assertThat(previous.includes(LocalDate.of(2026, 9, 30), TODAY)).isTrue();
        assertThat(previous.includes(LocalDate.of(2026, 10, 1), TODAY)).isFalse();
        assertThat(year.includes(LocalDate.of(2025, 10, 13), TODAY)).isTrue();
        assertThat(year.includes(LocalDate.of(2025, 10, 12), TODAY)).isFalse();
        assertThat(month.includes(null, TODAY)).isFalse();
        assertThat(DestroyedListQuery.parse(params()).includes(null, TODAY)).isTrue();
    }

    @Test
    void customRangeWinsOverThePeriodAndIsInclusive() {
        // when
        DestroyedListQuery query = DestroyedListQuery.parse(params("period", "month", "from", "2026-09-10", "to", "2026-09-20"));

        // then
        assertThat(query.includes(LocalDate.of(2026, 9, 10), TODAY)).isTrue();
        assertThat(query.includes(LocalDate.of(2026, 9, 20), TODAY)).isTrue();
        assertThat(query.includes(LocalDate.of(2026, 10, 5), TODAY)).isFalse();
    }

    @Test
    void invalidDatesAreIgnoredAndReversedOnesSwapped() {
        // when / then
        assertThat(DestroyedListQuery.parse(params("from", "2026-13-40")).from()).isNull();
        DestroyedListQuery swapped = DestroyedListQuery.parse(params("from", "2026-09-20", "to", "2026-09-10"));
        assertThat(swapped.from()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(swapped.to()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void hrefRoundTripsEveryNarrowing() {
        // given
        DestroyedListQuery query = DestroyedListQuery.parse(params("period", "year", "categories", "Dyski SSD",
                "categories", "Uncategorized", "q", " lenovo ", "sort", "cost", "dir", "asc", "page", "2"));

        // when
        DestroyedListQuery again = DestroyedListQuery.parse(fromHref(query.href()));

        // then
        assertThat(again).isEqualTo(query);
        assertThat(query.categories()).containsExactly("Dyski SSD", "none");
        assertThat(query.q()).isEqualTo("lenovo");
    }

    private static MultiValueMap<String, String> fromHref(String href) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        if (!href.contains("?")) return map;
        for (String pair : href.substring(href.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            map.add(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        return map;
    }

    @Test
    void tileAndMenuChangesGoBackToPageOne() {
        // given
        DestroyedListQuery query = DestroyedListQuery.parse(params("page", "3", "from", "2026-09-01"));

        // when / then
        assertThat(query.withPeriod(DestroyedListQuery.Period.MONTH).page()).isEqualTo(1);
        assertThat(query.withPeriod(DestroyedListQuery.Period.MONTH).from()).isNull();
        assertThat(query.toggleCategory("SSD").page()).isEqualTo(1);
        assertThat(query.withQ("x").page()).isEqualTo(1);
        assertThat(query.cleared().isFiltered()).isFalse();
    }

    @Test
    void sortingByDateStartsNewestFirstOtherColumnsAscending() {
        // given
        DestroyedListQuery query = DestroyedListQuery.parse(params());

        // when / then
        assertThat(query.toggleSort(DestroyedListQuery.Sort.DATE).effectiveDir()).isEqualTo(WarehouseListQuery.Direction.ASC);
        assertThat(query.toggleSort(DestroyedListQuery.Sort.NAME).effectiveDir()).isEqualTo(WarehouseListQuery.Direction.ASC);
        assertThat(query.toggleSort(DestroyedListQuery.Sort.NAME).toggleSort(DestroyedListQuery.Sort.DATE).effectiveDir())
                .isEqualTo(WarehouseListQuery.Direction.DESC);
    }

    @Test
    void conditionsComeFromTheAddressInTheirOwnOrderAndUnknownOnesAreIgnored() {
        // when
        DestroyedListQuery query = DestroyedListQuery.parse(params("conditions", "Damaged", "conditions", "Broken", "conditions", "OpenBox",
                "conditions", "Damaged", "categories", "SSD"));

        // then
        assertThat(query.conditions()).containsExactly(ItemCondition.OpenBox, ItemCondition.Damaged);
        assertThat(query.isFiltered()).isTrue();
        assertThat(query.href()).isEqualTo("/dashboard/warehouse/items/destroyed?categories=SSD&conditions=OpenBox&conditions=Damaged");
    }

    @Test
    void conditionsAreToggledRemovedClearedAndKeptByOtherChanges() {
        // given
        DestroyedListQuery query = DestroyedListQuery.parse(params("conditions", "Damaged", "page", "3"));

        // when / then
        assertThat(query.toggleCondition(ItemCondition.Sealed).conditions()).containsExactly(ItemCondition.Sealed, ItemCondition.Damaged);
        assertThat(query.toggleCondition(ItemCondition.Damaged).conditions()).isEmpty();
        assertThat(query.withoutCondition(ItemCondition.Damaged).href()).isEqualTo("/dashboard/warehouse/items/destroyed");
        assertThat(query.toggleCondition(ItemCondition.Sealed).page()).isEqualTo(1);
        assertThat(query.withPeriod(DestroyedListQuery.Period.MONTH).conditions()).containsExactly(ItemCondition.Damaged);
        assertThat(query.withQ("x").conditions()).containsExactly(ItemCondition.Damaged);
        assertThat(query.toggleSort(DestroyedListQuery.Sort.NAME).conditions()).containsExactly(ItemCondition.Damaged);
        assertThat(query.cleared().conditions()).isEmpty();
    }

    @Test
    void anItemWithoutAConditionCountsAsSealed() {
        // given
        DestroyedListQuery sealed = DestroyedListQuery.parse(params("conditions", "Sealed"));

        // when / then
        assertThat(sealed.includesCondition(null)).isTrue();
        assertThat(sealed.includesCondition(ItemCondition.Sealed)).isTrue();
        assertThat(sealed.includesCondition(ItemCondition.Damaged)).isFalse();
        assertThat(DestroyedListQuery.parse(params()).includesCondition(ItemCondition.Damaged)).isTrue();
    }
}
