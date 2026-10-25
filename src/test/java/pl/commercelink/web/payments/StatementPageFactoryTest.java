package pl.commercelink.web.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.payments.statements.BankOperation;
import pl.commercelink.stores.PayoutChannel;
import pl.commercelink.payments.statements.Allocation;
import pl.commercelink.payments.statements.StatementBooking;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.payments.StatementPageFixtures.*;

@ExtendWith(MockitoExtension.class)
public class StatementPageFactoryTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock OrdersRepository ordersRepository;

    private StatementPageFactory factory;

    @BeforeEach
    void setUp() {
        factory = new StatementPageFactory(ordersRepository, json(), messages());
        stub(ordersRepository, openOrders());
    }

    @Test
    void groupsTheOperationsByHowSureTheMatchIs() {
        // when
        StatementPage page = factory.page(store(), statement(), Map.of(), List.of(), PL);

        // then
        assertThat(page.groups()).extracting(StatementPage.Group::id)
                .containsExactly("matched", "check", "payout", "unmatched");
        assertThat(page.groups().get(0).rows()).extracting(StatementPage.Row::number).containsExactly("WB-JAN", "WB-BUDMAX");
        assertThat(page.selectedCount()).isEqualTo(2);
        assertThat(page.selectedOrders()).isEqualTo(2);
        assertThat(page.selectedTotal()).isEqualTo("6\u00a0169,00\u00a0PLN");
        assertThat(page.tiles()).extracting(StatementPage.Tile::count).containsExactly(2, 1, 1, 1);
    }

    @Test
    void showsTheTypeAndReferenceNumberOfThePaymentTheOperationFills() {
        // when
        StatementPage page = factory.page(store(), statement(), Map.of(), List.of(), PL);

        // then
        StatementPage.Row budmax = row(page, "WB-BUDMAX");
        assertThat(budmax.paymentType()).isEqualTo("Przelew bankowy");
        assertThat(budmax.paymentReference()).isEqualTo("nr ref.: PRO 0391/10/2026");
        assertThat(row(page, "WB-JAN").paymentReference()).isEqualTo("nr ref.: brak");
        StatementPage.Row payout = row(page, "WB-ALLEGRO");
        assertThat(payout.paymentType()).isEqualTo("Płatność online");
        assertThat(payout.rule().label()).isEqualTo("Wypłata zbiorcza · ALLEGRO");
        assertThat(payout.payout()).isTrue();
        assertThat(payout.channels()).isEqualTo("[0]");
        assertThat(payout.selected()).isFalse();
        assertThat(payout.selectable()).isTrue();
    }

    @Test
    void marksTheNumberTheMatchWasFoundByInTheTitle() {
        // when
        StatementPage page = factory.page(store(), statement(), Map.of(), List.of(), PL);

        // then
        assertThat(row(page, "WB-ANNA").title()).containsExactly(
                new StatementPage.TitlePart("Platnosc za zamowienie ", false),
                new StatementPage.TitlePart("c42d91f0", true),
                new StatementPage.TitlePart(" <b>", false));
        assertThat(row(page, "WB-ANNA").warnings()).extracting(StatementPage.Pill::label).containsExactly("Niedopłata 10,00\u00a0PLN");
    }

    @Test
    void carriesTheProposalsForThePageScript() {
        // when
        StatementPage page = factory.page(store(), statement(), Map.of(), List.of(), PL);

        // then
        Map<String, List<Allocation>> proposals = json().allocations(page.allocationsJson());
        assertThat(proposals).containsOnlyKeys("WB-JAN", "WB-ANNA", "WB-BUDMAX", "WB-ALLEGRO");
        assertThat(proposals.get("WB-ALLEGRO")).extracting(Allocation::orderId).containsExactly(ALLEGRO_1, ALLEGRO_2);
        assertThat(page.receivablesJson()).contains(JAN).contains("\"channels\":[0]");
    }

    @Test
    void showsWhatWasBookedFromTheOutcomeAndKeepsTheOperatorsOtherChoices() {
        // given
        StatementBooking.Outcome booked = new StatementBooking.Outcome(statement().incoming().get(0), null,
                List.of(new StatementBooking.OrderOutcome(JAN, StatementBooking.Result.BOOKED, null)));
        StatementBooking.Outcome failed = new StatementBooking.Outcome(statement().incoming().get(1), null,
                List.of(new StatementBooking.OrderOutcome(ANNA, StatementBooking.Result.FAILED, "statement.book.order.closed")));
        Map<String, List<Allocation>> chosen = Map.of("WB-LOAN",
                List.of(new Allocation(ANNA, new BigDecimal("500.00"), BigDecimal.ZERO)));

        // when
        StatementPage page = factory.page(store(), statement(), chosen, List.of(booked, failed), PL);

        // then
        assertThat(row(page, "WB-JAN").group()).isEqualTo("BOOKED");
        assertThat(page.notices()).extracting(StatementPage.Notice::tone).containsExactly("is-ok", "is-bad");
        assertThat(page.notices().get(1).details()).singleElement().asString()
                .contains("c42d91f0").contains("Zamówienie jest zakończone albo anulowane.");
        assertThat(row(page, "WB-LOAN").selectable()).isTrue();
        assertThat(row(page, "WB-LOAN").selected()).isFalse();
        // nothing was found for the loan; assigned by hand, it is checked like a proposal
        assertThat(row(page, "WB-LOAN").group()).isEqualTo("CHECK");
        assertThat(page.selectedCount()).isZero();
    }

    @Test
    void anOperationTheOperatorLeftWithoutOrdersWaitsAmongTheUnrecognisedOnesAfterABooking() {
        // given
        StatementBooking.Outcome booked = new StatementBooking.Outcome(statement().incoming().get(0), null,
                List.of(new StatementBooking.OrderOutcome(JAN, StatementBooking.Result.BOOKED, null)));
        Map<String, List<Allocation>> chosen = Map.of("WB-BUDMAX", List.of());

        // when
        StatementPage page = factory.page(store(), statement(), chosen, List.of(booked), PL);

        // then
        StatementPage.Row budmax = row(page, "WB-BUDMAX");
        assertThat(budmax.group()).isEqualTo("UNMATCHED");
        assertThat(page.tiles()).extracting(StatementPage.Tile::count).containsExactly(0, 1, 1, 2);
        assertThat(page.groups()).extracting(StatementPage.Group::id)
                .containsExactly("matched", "check", "payout", "unmatched", "booked");
        assertThat(budmax.orders()).isEmpty();
        assertThat(budmax.ordersNote()).isEqualTo("Bez przypisania nie zostanie zaksięgowana");
        assertThat(budmax.paymentType()).isEqualTo("—");
        assertThat(budmax.selectable()).isFalse();
        assertThat(budmax.actionLabel()).isEqualTo("Przypisz");
        assertThat(page.allocationsJson()).contains("\"WB-BUDMAX\":[]");
    }

    @Test
    void aPayoutNamingItsPaymentIsShownWithItsOrderAndFeeAndTicked() {
        // given
        Store store = store();
        store.getBankAccounts().get(0).getStatementFormat().getPayoutChannels()
                .add(new PayoutChannel("PAYNOW", PayoutChannel.SHOP, PaymentSource.OnlinePayment, true));
        Order paid = new Order("store-1");
        paid.setOrderId("e04b3d19-0000-4000-8000-000000000009");
        paid.setTotalPrice(15600.00);
        Payment pending = new Payment(PaymentSource.OnlinePayment);
        pending.setName("PayNow");
        pending.setReferenceNo("P53V-ON8-ZAS-ESB");
        paid.setPayments(new ArrayList<>(List.of(pending)));
        List<Order> orders = new ArrayList<>(openOrders());
        orders.add(paid);
        stub(ordersRepository, orders);
        BankOperation transfer = operation(20, "WB-PAYNOW", "15564.85", "MBANK S.A. PAYNOW, PLATNOSC NR: P53V-ON8-ZAS-ESB", "");
        StatementPageFactory.Statement statement = new StatementPageFactory.Statement(store.getBankAccounts().get(0),
                "paynow.csv", List.of(transfer), 0, 0, List.of());

        // when
        StatementPage page = factory.page(store, statement, Map.of(), List.of(), PL);

        // then
        StatementPage.Row row = row(page, "WB-PAYNOW");
        assertThat(row.group()).isEqualTo("PAYOUT");
        assertThat(row.selected()).isTrue();
        assertThat(row.rule().tone()).isEqualTo("is-ok");
        assertThat(row.orders()).singleElement().satisfies(ref -> {
            assertThat(ref.shortId()).isEqualTo("e04b3d19");
            assertThat(ref.detail()).isEqualTo("do zapłaty 15\u00a0600,00\u00a0PLN · prowizja 35,15\u00a0PLN");
        });
        assertThat(row.paymentType()).isEqualTo("Płatność online · PayNow");
    }

    @Test
    void cutsTheTitleAroundEveryHit() {
        // when
        List<StatementPage.TitlePart> parts = StatementPageFactory.titleParts("FV 1, fv 2 i FV 1", List.of("FV 1", "FV 2"));

        // then
        assertThat(parts).extracting(StatementPage.TitlePart::text).containsExactly("FV 1", ", ", "fv 2", " i ", "FV 1");
        assertThat(parts).extracting(StatementPage.TitlePart::hit).containsExactly(true, false, true, false, true);
    }

    public static StatementPageFactory.Statement statement() {
        Store store = store();
        return new StatementPageFactory.Statement(store.getBankAccounts().get(0), "lista_operacji.csv", incoming(), 25, 1,
                List.of());
    }

    private static StatementPage.Row row(StatementPage page, String number) {
        return page.groups().stream().flatMap(group -> group.rows().stream())
                .filter(row -> row.number().equals(number)).findFirst().orElseThrow();
    }
}
