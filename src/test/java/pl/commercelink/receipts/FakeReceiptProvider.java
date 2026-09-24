package pl.commercelink.receipts;

import pl.commercelink.receipts.api.Receipt;
import pl.commercelink.receipts.api.ReceiptProvider;
import pl.commercelink.receipts.api.ReceiptRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Scripted provider: each issue call takes the next scripted answer (a Receipt or a thrown exception). */
public class FakeReceiptProvider implements ReceiptProvider {

    public final List<ReceiptRequest> issued = new ArrayList<>();
    public final AtomicInteger issueCalls = new AtomicInteger();
    public final AtomicInteger fetchCalls = new AtomicInteger();
    public final Deque<Function<ReceiptRequest, Receipt>> issueScript = new ArrayDeque<>();
    public final Deque<Function<String, Receipt>> fetchScript = new ArrayDeque<>();
    public final Map<String, Receipt> findResults = new HashMap<>();
    public boolean requiresEmail = true;
    public int lineNameLength = 40;
    /** When set, issue blocks until released, to pin concurrent calls. */
    public volatile CountDownLatch issueGate;
    public final CountDownLatch issueEntered = new CountDownLatch(1);

    public FakeReceiptProvider answerIssue(Function<ReceiptRequest, Receipt> answer) {
        issueScript.add(answer);
        return this;
    }

    public FakeReceiptProvider answerFetch(Function<String, Receipt> answer) {
        fetchScript.add(answer);
        return this;
    }

    @Override
    public synchronized Receipt issue(ReceiptRequest request) {
        issueCalls.incrementAndGet();
        issued.add(request);
        issueEntered.countDown();
        CountDownLatch gate = issueGate;
        if (gate != null) {
            try {
                gate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Function<ReceiptRequest, Receipt> answer = issueScript.poll();
        if (answer == null) {
            return Receipt.pending(request.receiptKey(), "fake-" + request.receiptKey());
        }
        return answer.apply(request);
    }

    @Override
    public Optional<Receipt> find(String receiptKey) {
        return Optional.ofNullable(findResults.get(receiptKey));
    }

    @Override
    public Receipt fetch(String providerReceiptId) {
        fetchCalls.incrementAndGet();
        Function<String, Receipt> answer = fetchScript.poll();
        if (answer == null) {
            return Receipt.pending(null, providerReceiptId);
        }
        return answer.apply(providerReceiptId);
    }

    @Override
    public int maxLineNameLength() {
        return lineNameLength;
    }

    @Override
    public boolean requiresBuyerEmail() {
        return requiresEmail;
    }
}
