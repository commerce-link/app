package pl.commercelink.web.deliveries.pending;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PendingDeliveriesQueryTest {

    private static final String PATH = "/dashboard/deliveries/preview";

    private static PendingDeliveriesQuery parse(String... pairs) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            params.add(pairs[i], pairs[i + 1]);
        }
        return PendingDeliveriesQuery.parse(params);
    }

    @Test
    void parsesEveryParameterAndIgnoresUnknownValues() {
        // when
        PendingDeliveriesQuery query = parse("kind", "dropship", "provider", "Acme", "provider", " ",
                "provider", "Acme", "q", "  zając  ");
        PendingDeliveriesQuery unknown = parse("kind", "rma");

        // then
        assertThat(query).isEqualTo(new PendingDeliveriesQuery(Kind.DROPSHIP, List.of("Acme"), "zając"));
        assertThat(unknown).isEqualTo(new PendingDeliveriesQuery(null, List.of(), null));
        assertThat(unknown.isFiltered()).isFalse();
    }

    @Test
    void searchIsTrimmedAndCut() {
        // when
        PendingDeliveriesQuery query = parse("q", "x".repeat(150));

        // then
        assertThat(query.q()).hasSize(PendingDeliveriesQuery.MAX_Q);
    }

    @Test
    void hrefWritesTheQueryBackInAStableOrder() {
        // given
        PendingDeliveriesQuery query = new PendingDeliveriesQuery(Kind.WAREHOUSE, List.of("Acme B#2"), "a&b");

        // when / then
        assertThat(query.href(PATH)).isEqualTo(PATH + "?kind=warehouse&provider=Acme+B%232&q=a%26b");
        assertThat(new PendingDeliveriesQuery(null, List.of(), null).href(PATH)).isEqualTo(PATH);
    }

    @Test
    void narrowingDropsTheTabAndWideningKeepsIt() {
        // given
        PendingDeliveriesQuery query = new PendingDeliveriesQuery(Kind.DROPSHIP, List.of("Acme"), "nowak");

        // when / then
        assertThat(query.toggleProvider("AcmeB").kind()).isNull();
        assertThat(query.withQ("kowalski").kind()).isNull();
        assertThat(query.withoutProvider("Acme").kind()).isEqualTo(Kind.DROPSHIP);
        assertThat(query.withoutQ().kind()).isEqualTo(Kind.DROPSHIP);
        assertThat(query.cleared()).isEqualTo(new PendingDeliveriesQuery(Kind.DROPSHIP, List.of(), null));
        assertThat(query.withKind(Kind.WAREHOUSE)).isEqualTo(new PendingDeliveriesQuery(Kind.WAREHOUSE, List.of("Acme"), "nowak"));
        assertThat(query.toggleProvider("Acme").providers()).isEmpty();
        assertThat(query.activeFilterCount()).isEqualTo(2);
    }

    @Test
    void aFocusParameterFromAnOldLinkIsIgnored() {
        // when
        PendingDeliveriesQuery old = parse("focus", "overdue");

        // then
        assertThat(old).isEqualTo(parse());
        assertThat(old.isFiltered()).isFalse();
        assertThat(parse("kind", "dropship", "focus", "approval", "q", "nowak"))
                .isEqualTo(new PendingDeliveriesQuery(Kind.DROPSHIP, List.of(), "nowak"));
        assertThat(old.href(PATH)).isEqualTo(PATH);
    }
}
