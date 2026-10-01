package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancesViewTest {

    private static Order order(double total, double paid) {
        Order order = new Order("store-1");
        order.setTotalPrice(total);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", paid));
        return order;
    }

    @Test
    void anOverpaymentReadsAsOverpaymentNotANegativeAmountDue() {
        // when
        FinancesView overpaid = FinancesView.of(order(199, 1000), List.of());
        FinancesView due = FinancesView.of(order(199, 100), List.of());
        FinancesView settled = FinancesView.of(order(199, 199), List.of());

        // then: the payments card says "Nadpłata: 801,00" for the same order
        assertThat(overpaid.overpaid()).isTrue();
        assertThat(overpaid.unpaid()).isEqualTo("801,00");
        assertThat(overpaid.unpaidDue()).isFalse();
        assertThat(due.overpaid()).isFalse();
        assertThat(due.unpaid()).isEqualTo("99,00");
        assertThat(due.unpaidDue()).isTrue();
        assertThat(settled.overpaid()).isFalse();
        assertThat(settled.unpaid()).isEqualTo("0,00");
    }
}
