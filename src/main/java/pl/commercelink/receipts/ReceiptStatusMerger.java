package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import pl.commercelink.receipts.api.Receipt;

import java.time.Instant;
import java.util.Objects;

/**
 * Applies a provider's view of a receipt to an attempt, only ever forwards: FISCALISED and FAILED are final, and
 * PENDING moves an ISSUING attempt on only when it comes from issue itself. A PENDING seen by a webhook or a poll
 * does not prove fiscalisation was ordered (a receipt created without an answer is PENDING until issue is retried),
 * so it must not take the retry away.
 */
@Slf4j
public final class ReceiptStatusMerger {

    public enum Source { ISSUE, POLL, PUSH }

    private ReceiptStatusMerger() {
    }

    public static boolean merge(ReceiptAttempt attempt, Receipt receipt, Source source, Instant now) {
        ReceiptAttemptState state = attempt.getState();
        if (state == ReceiptAttemptState.FAILED || state == ReceiptAttemptState.BLOCKED
                || state == ReceiptAttemptState.CLOSED_MANUALLY) {
            if (receipt.state() == pl.commercelink.receipts.api.ReceiptState.FISCALISED) {
                log.error("Provider reports receipt {} fiscalised although the attempt is {}", attempt.getReceiptKey(), state);
            }
            return false;
        }
        if (attempt.getProviderReceiptId() != null
                && !Objects.equals(attempt.getProviderReceiptId(), receipt.providerReceiptId())) {
            log.warn("Receipt {} is {} at the provider, ignoring a result for {}", attempt.getReceiptKey(),
                    attempt.getProviderReceiptId(), receipt.providerReceiptId());
            return false;
        }
        boolean changed = false;
        if (attempt.getProviderReceiptId() == null) {
            attempt.setProviderReceiptId(receipt.providerReceiptId());
            changed = true;
        }
        switch (receipt.state()) {
            case PENDING -> {
                if (state == ReceiptAttemptState.ISSUING && source == Source.ISSUE) {
                    attempt.setState(ReceiptAttemptState.PENDING);
                    attempt.setPollCount(0);
                    clearError(attempt);
                    changed = true;
                }
            }
            case FISCALISED -> {
                if (state != ReceiptAttemptState.FISCALISED) {
                    attempt.setState(ReceiptAttemptState.FISCALISED);
                    attempt.setFiscalisedAt(receipt.fiscal().fiscalisedAt());
                    attempt.setReceiptNumber(receipt.fiscal().receiptNumber());
                    attempt.setCashRegisterUniqueNumber(receipt.fiscal().cashRegisterUniqueNumber());
                    attempt.setPollCount(0);
                    clearError(attempt);
                    changed = true;
                }
                if (attempt.getDocumentUrl() == null && receipt.documentUrl() != null) {
                    attempt.setDocumentUrl(receipt.documentUrl());
                    changed = true;
                }
            }
            case FAILED -> {
                if (state == ReceiptAttemptState.ISSUING || state == ReceiptAttemptState.PENDING) {
                    attempt.setState(ReceiptAttemptState.FAILED);
                    attempt.setFailureCode(receipt.failure().code());
                    attempt.setFailureMessage(receipt.failure().message());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static void clearError(ReceiptAttempt attempt) {
        attempt.setLastError(null);
        attempt.setLastErrorAt(null);
        attempt.setInvalidAfterSend(false);
    }
}
