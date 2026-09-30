package pl.commercelink.web.deliveries.pending;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Focus;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;

import java.time.LocalDate;
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
        PendingDeliveriesQuery query = parse("kind", "dropship", "focus", "TODAY", "provider", "Acme", "provider", " ",
                "provider", "Acme", "q", "  zając  ");
        PendingDeliveriesQuery unknown = parse("kind", "rma", "focus", "problem");

        // then
        assertThat(query).isEqualTo(new PendingDeliveriesQuery(Kind.DROPSHIP, Focus.TODAY, List.of("Acme"), "zając"));
        assertThat(unknown).isEqualTo(new PendingDeliveriesQuery(null, null, List.of(), null));
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
        PendingDeliveriesQuery query = new PendingDeliveriesQuery(Kind.WAREHOUSE, Focus.OVERDUE, List.of("Acme B#2"), "a&b");

        // when / then
        assertThat(query.href(PATH)).isEqualTo(PATH + "?kind=warehouse&focus=overdue&provider=Acme+B%232&q=a%26b");
        assertThat(new PendingDeliveriesQuery(null, null, List.of(), null).href(PATH)).isEqualTo(PATH);
    }

    @Test
    void narrowingDropsTheTabAndWideningKeepsIt() {
        // given
        PendingDeliveriesQuery query = new PendingDeliveriesQuery(Kind.DROPSHIP, Focus.OVERDUE, List.of("Acme"), "nowak");

        // when / then
        assertThat(query.withFocus(Focus.TODAY).kind()).isNull();
        assertThat(query.toggleProvider("AcmeB").kind()).isNull();
        assertThat(query.withQ("kowalski").kind()).isNull();
        assertThat(query.withoutFocus().kind()).isEqualTo(Kind.DROPSHIP);
        assertThat(query.withoutProvider("Acme").kind()).isEqualTo(Kind.DROPSHIP);
        assertThat(query.withoutQ().kind()).isEqualTo(Kind.DROPSHIP);
        assertThat(query.cleared()).isEqualTo(new PendingDeliveriesQuery(Kind.DROPSHIP, null, List.of(), null));
        assertThat(query.withKind(Kind.WAREHOUSE)).isEqualTo(new PendingDeliveriesQuery(Kind.WAREHOUSE, Focus.OVERDUE, List.of("Acme"), "nowak"));
        assertThat(query.toggleProvider("Acme").providers()).isEmpty();
        assertThat(query.activeFilterCount()).isEqualTo(3);
    }

    @Test
    void focusMatchesTheDueDate() {
        // given
        LocalDate today = LocalDate.of(2026, 9, 30);

        // when / then
        assertThat(Focus.OVERDUE.matches(today.minusDays(1), today)).isTrue();
        assertThat(Focus.OVERDUE.matches(today, today)).isFalse();
        assertThat(Focus.TODAY.matches(today, today)).isTrue();
        assertThat(Focus.TODAY.matches(null, today)).isFalse();
        assertThat(Focus.OVERDUE.matches(null, today)).isFalse();
    }
}
