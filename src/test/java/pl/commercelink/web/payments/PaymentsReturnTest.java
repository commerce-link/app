package pl.commercelink.web.payments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentsReturnTest {

    @Test
    void returnToOutsidePaymentsIsIgnored() {
        // given / when / then
        assertThat(PaymentsReturn.target("/dashboard/payments?side=receivables&focus=overdue"))
                .contains("/dashboard/payments?side=receivables&focus=overdue");
        assertThat(PaymentsReturn.target("/dashboard/payments")).contains("/dashboard/payments");
        assertThat(PaymentsReturn.target(null)).isEmpty();
        assertThat(PaymentsReturn.target("https://evil.example/dashboard/payments")).isEmpty();
        assertThat(PaymentsReturn.target("//evil.example")).isEmpty();
        assertThat(PaymentsReturn.target("/dashboard/paymentsX")).isEmpty();
        assertThat(PaymentsReturn.target("/dashboard/payments?x=1\r\nSet-Cookie: a=b")).isEmpty();
    }
}
