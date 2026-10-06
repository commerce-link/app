package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pl.commercelink.inventory.BrowseCriteria;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BrowseQueryTest {

    @Test
    void parseReadsEveryParameterAndHrefWritesItBack() {
        // given
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("cat", "11");
        params.add("supplier", "AB");
        params.add("supplier", "Action");
        params.add("stock", "in-stock");
        params.add("catalog", "out");
        params.add("q2", "  rtx 4060 ");
        params.add("sort", "cost");
        params.add("dir", "desc");
        params.add("page", "3");

        // when
        BrowseQuery query = BrowseQuery.parse(params);

        // then
        assertThat(query.category()).isEqualTo("11");
        assertThat(query.suppliers()).containsExactly("AB", "Action");
        assertThat(query.stock()).isEqualTo(BrowseCriteria.Stock.IN_STOCK);
        assertThat(query.catalog()).isEqualTo(BrowseQuery.CatalogFilter.OUT);
        assertThat(query.q2()).isEqualTo("rtx 4060");
        assertThat(query.sort()).isEqualTo(BrowseCriteria.Sort.COST);
        assertThat(query.descending()).isTrue();
        assertThat(query.page()).isEqualTo(3);
        assertThat(query.href()).isEqualTo("/dashboard/inventory?cat=11&supplier=AB&supplier=Action"
                + "&stock=in-stock&catalog=out&q2=rtx+4060&sort=cost&dir=desc&page=3");
    }

    @Test
    void unknownValuesFallBackToDefaults() {
        // given
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("stock", "lots");
        params.add("catalog", "maybe");
        params.add("sort", "price");
        params.add("dir", "sideways");
        params.add("page", "-4");
        params.add("cat", " ");

        // when
        BrowseQuery query = BrowseQuery.parse(params);

        // then
        assertThat(query).isEqualTo(BrowseQuery.start());
        assertThat(query.href()).isEqualTo("/dashboard/inventory");
        assertThat(query.isStart()).isTrue();
    }

    @Test
    void withersResetThePageButWithPageDoesNot() {
        // given
        BrowseQuery query = BrowseQuery.start().withCategory("11").withPage(4);

        // when / then
        assertThat(query.page()).isEqualTo(4);
        assertThat(query.withStock(BrowseCriteria.Stock.IN_STOCK).page()).isEqualTo(1);
        assertThat(query.toggleSort(BrowseCriteria.Sort.COST).page()).isEqualTo(1);
        assertThat(query.withCategory("12").page()).isEqualTo(1);
    }

    @Test
    void toggleSortFlipsTheDirectionOfTheCurrentColumnOnly() {
        // given
        BrowseQuery byCost = BrowseQuery.start().toggleSort(BrowseCriteria.Sort.COST);

        // when / then
        assertThat(byCost.descending()).isFalse();
        assertThat(byCost.toggleSort(BrowseCriteria.Sort.COST).descending()).isTrue();
        assertThat(byCost.toggleSort(BrowseCriteria.Sort.QTY).descending()).isFalse();
    }

    @Test
    void clearedKeepsTheCategoryAndTheSort() {
        // given
        BrowseQuery query = BrowseQuery.start().withCategory("11").toggleSort(BrowseCriteria.Sort.QTY)
                .withStock(BrowseCriteria.Stock.ON_ORDER).withCatalog(BrowseQuery.CatalogFilter.IN);

        // when
        BrowseQuery cleared = query.cleared();

        // then
        assertThat(cleared.category()).isEqualTo("11");
        assertThat(cleared.sort()).isEqualTo(BrowseCriteria.Sort.QTY);
        assertThat(cleared.stock()).isEqualTo(BrowseCriteria.Stock.ALL);
        assertThat(cleared.catalog()).isEqualTo(BrowseQuery.CatalogFilter.ALL);
        assertThat(cleared.suppliers()).isEmpty();
        assertThat(cleared.q2()).isNull();
    }

    @Test
    void textShorterThanThreeCharactersIsNotSearched() {
        // given
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q2", "ab");

        // when
        BrowseQuery query = BrowseQuery.parse(params);

        // then
        assertThat(query.textTooShort()).isTrue();
        assertThat(query.toCriteria(null).text()).isNull();
    }

    @Test
    void toCriteriaTranslatesThePageToAnOffset() {
        // given
        BrowseQuery query = BrowseQuery.start().withCategory("11").withPage(3);

        // when
        BrowseCriteria criteria = query.toCriteria(Set.of("11"));

        // then
        assertThat(criteria.offset()).isEqualTo(100);
        assertThat(criteria.limit()).isEqualTo(50);
        assertThat(criteria.categoryIds()).containsExactly("11");
    }

    @Test
    void textIsCutToOneHundredCharacters() {
        // given
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q2", "x".repeat(140));

        // when / then
        assertThat(BrowseQuery.parse(params).q2()).hasSize(100);
        assertThat(BrowseQuery.parse(new LinkedMultiValueMap<>(java.util.Map.of("supplier", List.of(" ", "AB")))).suppliers())
                .containsExactly("AB");
    }

    @Test
    void paramsLeaveOutTheNamedOneAndThePage() {
        // given
        BrowseQuery query = BrowseQuery.start().withCategory("11").withStock(BrowseCriteria.Stock.IN_STOCK).withPage(2);

        // when
        List<BrowseQuery.Param> params = query.params("stock");

        // then
        assertThat(params).extracting(BrowseQuery.Param::name).containsExactly("cat");
    }

    @Test
    void firstPageWithoutFiltersIsTheBarePathAndLaterPagesStartTheQuery() {
        // given
        BrowseQuery start = BrowseQuery.start();

        // when / then
        assertThat(start.href()).isEqualTo("/dashboard/inventory");
        assertThat(start.withPage(2).href()).isEqualTo("/dashboard/inventory?page=2");
        assertThat(start.hrefWith("open=add&ean=1")).isEqualTo("/dashboard/inventory?open=add&ean=1");
        assertThat(start.withCategory("11").hrefWith("open=add")).isEqualTo("/dashboard/inventory?cat=11&open=add");
    }
}
