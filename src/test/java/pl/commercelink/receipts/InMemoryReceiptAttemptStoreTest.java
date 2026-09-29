package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryReceiptAttemptStoreTest {

    private final InMemoryReceiptAttemptStore store = new InMemoryReceiptAttemptStore();

    @Test
    void findByProviderReceiptIdLooksOnlyInTheGivenStore() {
        // given
        store.create(attempt("s1", "o1:R1", "dev-rcpt-1"));
        store.create(attempt("s1", "o2:R1", null));
        store.create(attempt("s2", "o9:R1", "dev-rcpt-9"));

        // when / then
        assertThat(store.findByProviderReceiptId("s1", "dev-rcpt-1")).map(ReceiptAttempt::getReceiptKey).contains("o1:R1");
        assertThat(store.findByProviderReceiptId("s1", "dev-rcpt-9")).isEmpty();
        assertThat(store.findByProviderReceiptId("s1", "dev-rcpt-x")).isEmpty();
    }

    private static ReceiptAttempt attempt(String storeId, String receiptKey, String providerReceiptId) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId(storeId);
        attempt.setReceiptKey(receiptKey);
        attempt.setProviderReceiptId(providerReceiptId);
        return attempt;
    }
}
