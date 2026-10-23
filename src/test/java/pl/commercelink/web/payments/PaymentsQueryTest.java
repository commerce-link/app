package pl.commercelink.web.payments;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.orders.PaymentSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentsQueryTest {

    private static PaymentsQuery parse(String... pairs) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) params.add(pairs[i], pairs[i + 1]);
        return PaymentsQuery.parse(params);
    }

    @Test
    void emptyAddressIsThePlainPage() {
        // when
        PaymentsQuery query = parse();

        // then
        assertThat(query.side()).isNull();
        assertThat(query.isFiltered()).isFalse();
        assertThat(query.href()).isEqualTo("/dashboard/payments");
    }

    @Test
    void knownValuesRoundTripAndUnknownOnesAreIgnored() {
        // when
        PaymentsQuery query = parse("side", "receivables", "focus", "overdue", "method", "CashOnDelivery",
                "method", "Bitcoin", "focus", "x", "q", "  nowak  ");

        // then
        assertThat(query.side()).isEqualTo(PaymentSide.RECEIVABLES);
        assertThat(query.focus()).isEqualTo(PaymentFocus.OVERDUE);
        assertThat(query.methods()).containsExactly(PaymentSource.CashOnDelivery);
        assertThat(query.q()).isEqualTo("nowak");
        assertThat(query.href()).isEqualTo("/dashboard/payments?side=receivables&focus=overdue&method=CashOnDelivery&q=nowak");
    }

    @Test
    void searchIsCutToOneHundredCharacters() {
        // when
        PaymentsQuery query = parse("q", "a".repeat(150));

        // then
        assertThat(query.q()).hasSize(PaymentsQuery.MAX_Q);
    }

    @Test
    void tileLinkDropsSideAndEveryOtherFilter() {
        // given
        PaymentsQuery query = parse("side", "payables", "provider", "Acme", "q", "zs");

        // when
        PaymentsQuery tile = query.withFocus(PaymentFocus.REFUND);

        // then: the page opens on the side that has results (spec §4.2)
        assertThat(tile.href()).isEqualTo("/dashboard/payments?focus=refund");
    }

    @Test
    void switchingSideDropsTheOtherSidesMenu() {
        // given
        PaymentsQuery query = parse("side", "payables", "provider", "Acme", "focus", "today", "q", "x");

        // when
        PaymentsQuery receivables = query.withSide(PaymentSide.RECEIVABLES);

        // then
        assertThat(receivables.providers()).isEmpty();
        assertThat(receivables.href()).isEqualTo("/dashboard/payments?side=receivables&focus=today&q=x");
    }

    @Test
    void clearingKeepsTheSide() {
        // given
        PaymentsQuery query = parse("side", "receivables", "method", "Raty", "method", "Installments", "focus", "refund");

        // when / then
        assertThat(query.cleared().href()).isEqualTo("/dashboard/payments?side=receivables");
        assertThat(query.withoutFocus().href()).isEqualTo("/dashboard/payments?side=receivables&method=Installments");
        assertThat(query.toggleMethod(PaymentSource.Installments).methods()).isEmpty();
    }

    @Test
    void providerIsEncodedInTheAddress() {
        // when
        PaymentsQuery query = parse("provider", "Inny: Hurtownia & Syn");

        // then
        assertThat(query.href()).isEqualTo("/dashboard/payments?provider=Inny%3A+Hurtownia+%26+Syn");
        assertThat(query.withoutProvider("Inny: Hurtownia & Syn").providers()).isEmpty();
    }
}
