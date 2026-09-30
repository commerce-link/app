package pl.commercelink.web.deliveries;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pl.commercelink.inventory.deliveries.DeliveryListState;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryListQueryTest {

    private static MultiValueMap<String, String> params(String... pairs) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.add(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void bareAddressIsTheDeliveriesOnTheirWayByPlannedDate() {
        // when
        DeliveryListQuery query = DeliveryListQuery.parse(params());

        // then
        assertThat(query.scope()).isEqualTo(DeliveryListQuery.Scope.TRANSIT);
        assertThat(query.effectiveSort()).isEqualTo(DeliveryListQuery.Sort.DUE);
        assertThat(query.effectiveDir()).isEqualTo(DeliveryListQuery.Direction.ASC);
        assertThat(query.href()).isEqualTo("/dashboard/deliveries");
    }

    @Test
    void reversedDateRangeIsSwapped() {
        // when
        DeliveryListQuery query = DeliveryListQuery.parse(params("from", "2026-09-30", "to", "2026-09-01"));

        // then
        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(query.to()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void everyParameterSurvivesARoundTrip() {
        // given
        String href = "/dashboard/deliveries?scope=received&state=received&state=shippedToCustomer&provider=Acme"
                + "&provider=Hurtownia+Kowalski&settle=noInvoice&from=2026-09-01&to=2026-09-30&q=7a31&sort=cost&dir=asc&page=2";

        // when
        DeliveryListQuery query = DeliveryListQuery.parse(params("scope", "received", "state", "received",
                "state", "shippedToCustomer", "provider", "Acme", "provider", "Hurtownia Kowalski", "settle", "noInvoice",
                "from", "2026-09-01", "to", "2026-09-30", "q", "7a31", "sort", "cost", "dir", "asc", "page", "2"));

        // then
        assertThat(query.href()).isEqualTo(href);
        assertThat(query.states()).containsExactly(DeliveryListState.RECEIVED, DeliveryListState.SHIPPED_TO_CUSTOMER);
    }

    @Test
    void aTileClearsEveryOtherNarrowingButKeepsTheSort() {
        // given
        DeliveryListQuery query = DeliveryListQuery.parse(params("state", "failed", "q", "x", "sort", "cost"));

        // when
        DeliveryListQuery tile = query.withFocus(DeliveryAttention.INVOICE);

        // then
        assertThat(tile.href()).isEqualTo("/dashboard/deliveries?scope=received&focus=invoice&sort=cost");
        assertThat(tile.withoutFocus().href()).isEqualTo("/dashboard/deliveries?scope=received&sort=cost");
    }

    @Test
    void unknownValuesAreIgnoredNotRejected() {
        // when
        DeliveryListQuery query = DeliveryListQuery.parse(params("scope", "nope", "state", "nope", "focus", "nope",
                "from", "30.09.2026", "page", "-3", "sort", "nope"));

        // then
        assertThat(query.href()).isEqualTo("/dashboard/deliveries");
        assertThat(query.page()).isEqualTo(1);
    }

    @Test
    void legacyParametersRedirectToTheNewAddress() {
        assertThat(DeliveryListQuery.legacyRedirect(params("showArchived", "true", "showWithoutInvoice", "true")))
                .contains("/dashboard/deliveries?scope=all&settle=noInvoice&period=all");
        assertThat(DeliveryListQuery.legacyRedirect(params("deliveryId", "7a31c0e2-1111-2222-3333-444455556666")))
                .contains("/dashboard/deliveries?scope=all&period=all&q=7a31c0e2-1111-2222-3333-444455556666");
        assertThat(DeliveryListQuery.legacyRedirect(params("provider", "__custom__", "providerCustom", " Kowalski ",
                "orderedAtStart", "2026-09-01", "showAwaitingApproval", "true", "showWithoutSync", "false")))
                .contains("/dashboard/deliveries?state=awaitingApproval&provider=Kowalski&from=2026-09-01");
        assertThat(DeliveryListQuery.legacyRedirect(params("scope", "all"))).isEmpty();
        assertThat(DeliveryListQuery.legacyRedirect(params("provider", "Acme"))).isEmpty();
    }

    @Test
    void historyOpensOnTheLastNinetyDaysUnlessTheWholeHistoryIsAsked() {
        // given
        LocalDate today = LocalDate.of(2026, 9, 30);

        // then
        assertThat(DeliveryListQuery.parse(params("scope", "received")).historyFrom(today)).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(DeliveryListQuery.parse(params("scope", "received", "period", "all")).historyFrom(today)).isNull();
        assertThat(DeliveryListQuery.parse(params("scope", "received", "from", "2026-01-01")).historyFrom(today))
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(DeliveryListQuery.parse(params("scope", "received")).withFocus(DeliveryAttention.INVOICE).historyFrom(today))
                .isNull();
        assertThat(DeliveryListQuery.parse(params("scope", "received", "to", "2026-09-30")).historyFrom(today))
                .isNull();
    }

    @Test
    void sortingTheSameColumnFlipsItAndAnotherColumnStartsAscending() {
        // given
        DeliveryListQuery query = DeliveryListQuery.parse(params());

        // then
        assertThat(query.toggleSort(DeliveryListQuery.Sort.DUE).href()).isEqualTo("/dashboard/deliveries?sort=due&dir=desc");
        assertThat(query.toggleSort(DeliveryListQuery.Sort.COST).href()).isEqualTo("/dashboard/deliveries?sort=cost&dir=asc");
    }

    @Test
    void withScopeDropsAFocusWhoseScopeDiffers() {
        // when
        DeliveryListQuery query = DeliveryListQuery.parse(params()).withFocus(DeliveryAttention.INVOICE).withScope(DeliveryListQuery.Scope.TRANSIT);

        // then
        assertThat(query.focus()).isNull();
        assertThat(query.href()).isEqualTo("/dashboard/deliveries");
    }
}
