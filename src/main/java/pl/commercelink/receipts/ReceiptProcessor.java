package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.receipts.api.Receipt;
import pl.commercelink.receipts.api.ReceiptOutcomeUnknownException;
import pl.commercelink.receipts.api.ReceiptProvider;
import pl.commercelink.receipts.api.ReceiptRejectedException;
import pl.commercelink.receipts.api.ReceiptRequest;
import pl.commercelink.receipts.api.ReceiptValidationException;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Does the work an attempt is due for, under the attempt's lease, so no two workers ever call the provider for the
 * same key at once (a queue redelivery during a slow issue finds the lease taken and leaves). Provider failures are
 * recorded on the attempt and rescheduled, never thrown: the queue only redelivers crashes.
 *
 * <p>The lease (15 min) covers waiting for the call limiter (up to 2 min) and the longest issue (~3.5 min) with room
 * to spare; an expired lease — a crashed worker — is taken over by the next wake-up.
 */
@Slf4j
@Component
public class ReceiptProcessor {

    static final Duration LEASE = Duration.ofMinutes(15);
    private static final Duration EFFECTS_RETRY = Duration.ofMinutes(5);

    private final ReceiptAttemptStore attempts;
    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final ReceiptProviderFactory providerFactory;
    private final ReceiptEligibility eligibility;
    private final ReceiptEffects effects;
    private final ReceiptAlerts alerts;
    private final Clock clock;

    @Autowired
    public ReceiptProcessor(ReceiptAttemptStore attempts, StoresRepository storesRepository,
                            OrdersRepository ordersRepository, ReceiptProviderFactory providerFactory,
                            ReceiptEligibility eligibility, ReceiptEffects effects, ReceiptAlerts alerts) {
        this(attempts, storesRepository, ordersRepository, providerFactory, eligibility, effects, alerts,
                Clock.systemUTC());
    }

    ReceiptProcessor(ReceiptAttemptStore attempts, StoresRepository storesRepository,
                     OrdersRepository ordersRepository, ReceiptProviderFactory providerFactory,
                     ReceiptEligibility eligibility, ReceiptEffects effects, ReceiptAlerts alerts, Clock clock) {
        this.attempts = attempts;
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.providerFactory = providerFactory;
        this.eligibility = eligibility;
        this.effects = effects;
        this.alerts = alerts;
        this.clock = clock;
    }

    public void process(String storeId, String receiptKey) {
        String owner = UUID.randomUUID().toString();
        Optional<ReceiptAttempt> leased = acquire(storeId, receiptKey, owner);
        if (leased.isEmpty()) {
            return;
        }
        boolean effectsFailed = false;
        try {
            ReceiptAttempt attempt = leased.get();
            switch (attempt.getState()) {
                case ISSUING -> issue(attempt, owner);
                case PENDING -> poll(attempt);
                case FISCALISED -> {
                    if (attempt.getDocumentUrl() == null && attempt.getLinkGaveUpAt() == null) {
                        poll(attempt);
                    }
                }
                default -> {
                }
            }
            // issue()/poll() may have lost the lease mid-flight (stolen, or simply expired); re-read from the
            // store rather than trust anything decided earlier in this call — effects must never run for an
            // attempt this call no longer owns.
            if (holdsLease(storeId, receiptKey, owner)) {
                try {
                    effects.apply(storeId, receiptKey);
                } catch (RuntimeException e) {
                    effectsFailed = true;
                    log.error("Effects of fiscalised receipt {} failed; retrying later", receiptKey, e);
                }
            }
        } catch (RuntimeException e) {
            log.error("Receipt attempt {} could not be processed", receiptKey, e);
        } finally {
            finish(storeId, receiptKey, owner, effectsFailed);
        }
    }

    private boolean holdsLease(String storeId, String receiptKey, String owner) {
        return attempts.find(storeId, receiptKey).filter(a -> owner.equals(a.getLeaseOwner())).isPresent();
    }

    /**
     * Ownership after {@code update} is decided by the persisted {@code leaseOwner}, never by a flag set inside
     * the predicate: {@code update} retries the predicate on a version conflict, and a flag set on an earlier,
     * losing try would otherwise survive into the retry that correctly saw the lease taken and wrote nothing.
     */
    private Optional<ReceiptAttempt> acquire(String storeId, String receiptKey, String owner) {
        Instant now = clock.instant();
        Optional<ReceiptAttempt> attempt = attempts.update(storeId, receiptKey, a -> {
            if (a.isLeasedAt(now)) {
                return false;
            }
            a.setLeaseOwner(owner);
            a.setLeaseUntil(now.plus(LEASE));
            return true;
        });
        return attempt.filter(a -> owner.equals(a.getLeaseOwner()));
    }

    private void issue(ReceiptAttempt attempt, String owner) {
        String storeId = attempt.getStoreId();
        String key = attempt.getReceiptKey();
        if (attempt.getIssueCalls() == 0 && !stillQualifies(attempt)) {
            // stillQualifies() is exactly the slow window (an order lookup) in which the lease can be lost to
            // another owner who has since bumped issueCalls and may be inside provider.issue right now — blocking
            // unconditionally here would kill that attempt and its eventual result would be dropped by the merger
            // (BLOCKED ignores everything), losing a real sale. Guard and read back the outcome exactly as the
            // issueCalls guard below does: from the persisted attempt, never from a flag set inside the predicate.
            Instant blockNow = clock.instant();
            Optional<ReceiptAttempt> blocked = attempts.update(storeId, key, a -> {
                if (!owner.equals(a.getLeaseOwner()) || !a.isLeasedAt(blockNow)
                        || a.getState() != ReceiptAttemptState.ISSUING || a.getIssueCalls() != 0) {
                    return false;
                }
                a.setState(ReceiptAttemptState.BLOCKED);
                a.setBlockedReason(ReceiptBlockReason.NOT_ELIGIBLE.name());
                return true;
            });
            boolean blockedByUs = blocked.isPresent() && owner.equals(blocked.get().getLeaseOwner())
                    && blocked.get().getState() == ReceiptAttemptState.BLOCKED;
            if (!blockedByUs) {
                log.warn("Receipt attempt {} lost its lease before it could be blocked as not eligible; leaving it untouched", key);
            }
            return;
        }
        ReceiptProvider provider;
        ReceiptRequest request;
        try {
            provider = provider(attempt);
            if (provider == null) {
                // The adapter is not on the classpath (or the descriptor is otherwise gone): this is a load
                // failure exactly like a thrown exception below, never a reason to call issue() with a null target.
                throw new IllegalStateException("Receipt provider " + attempt.getProvider() + " is not available");
            }
            request = ReceiptSnapshotJson.read(attempt.getRequestSnapshot()).toRequest(key);
        } catch (RuntimeException e) {
            recordPreSendFailure(attempt, e);   // nothing was sent; issueCalls is never touched here
            return;
        }
        // Defence in depth: acquire() proved ownership at the start of process(), but a lot can happen between
        // then and here (a slow store/order lookup letting the lease expire under us, a sweep taking it over).
        // Re-check right before the call that actually reaches the provider; never call it without the lease.
        // Whether the guard held is read back from the persisted attempt update() hands back, never from a flag
        // set inside the predicate — update() retries that predicate on a version conflict, and a flag set on an
        // earlier, aborted try would otherwise survive into a retry that correctly found the lease gone.
        Instant guardNow = clock.instant();
        Optional<ReceiptAttempt> afterGuard = attempts.update(storeId, key, a -> {
            if (!owner.equals(a.getLeaseOwner()) || !a.isLeasedAt(guardNow) || a.getState() != ReceiptAttemptState.ISSUING) {
                return false;
            }
            a.setIssueCalls(a.getIssueCalls() + 1);   // recorded before the call: the count survives a crash
            a.setLeaseUntil(guardNow.plus(LEASE));    // renew: the call itself can take a while, close to the lease
            return true;
        });
        boolean stillOurs = afterGuard.isPresent() && owner.equals(afterGuard.get().getLeaseOwner())
                && afterGuard.get().isLeasedAt(guardNow) && afterGuard.get().getState() == ReceiptAttemptState.ISSUING;
        if (!stillOurs) {
            log.warn("Receipt attempt {} lost its lease before issue; not calling the provider", key);
            return;
        }
        try {
            Receipt receipt = provider.issue(request);
            Instant now = clock.instant();
            attempts.update(storeId, key, a -> ReceiptStatusMerger.merge(a, receipt, ReceiptStatusMerger.Source.ISSUE, now));
        } catch (ReceiptValidationException e) {
            attempts.update(storeId, key, a -> {
                if (a.getState() != ReceiptAttemptState.ISSUING) {
                    return false;
                }
                if (a.getIssueCalls() <= 1) {
                    a.setState(ReceiptAttemptState.BLOCKED);
                    a.setBlockedReason(ReceiptBlockReason.INVALID_REQUEST.name());
                    a.setBlockedDetail(e.getMessage());
                } else {
                    // an earlier call may have reached the provider: never let this attempt die
                    a.setInvalidAfterSend(true);
                    a.setLastError(e.getMessage());
                    a.setLastErrorAt(clock.instant());
                }
                return true;
            });
        } catch (ReceiptRejectedException e) {
            attempts.update(storeId, key, a -> {
                // Only an attempt still ISSUING is ours to fail: if it is already PENDING, something else (a push
                // webhook) established a newer, real outcome while this call was in flight, and a stale rejection
                // from this call must never overwrite it.
                if (a.getState() != ReceiptAttemptState.ISSUING) {
                    return false;
                }
                a.setState(ReceiptAttemptState.FAILED);
                a.setFailureCode(e.code());
                a.setFailureMessage(e.getMessage());
                return true;
            });
        } catch (ReceiptOutcomeUnknownException e) {
            recordError(attempt, e, true);
        } catch (RuntimeException e) {
            recordError(attempt, e, false);
        }
    }

    private void poll(ReceiptAttempt attempt) {
        try {
            Receipt receipt = provider(attempt).fetch(attempt.getProviderReceiptId());
            Instant now = clock.instant();
            attempts.update(attempt.getStoreId(), attempt.getReceiptKey(), a -> {
                ReceiptStatusMerger.merge(a, receipt, ReceiptStatusMerger.Source.POLL, now);
                a.setPollCount(a.getPollCount() + 1);
                return true;
            });
        } catch (RuntimeException e) {
            attempts.update(attempt.getStoreId(), attempt.getReceiptKey(), a -> {
                a.setPollCount(a.getPollCount() + 1);
                a.setLastError("fetch: " + e.getMessage());
                a.setLastErrorAt(clock.instant());
                return true;
            });
        }
    }

    private void recordError(ReceiptAttempt attempt, RuntimeException e, boolean diagnose) {
        String diagnosis = diagnose ? diagnose(attempt) : "";
        attempts.update(attempt.getStoreId(), attempt.getReceiptKey(), a -> {
            a.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage() + diagnosis);
            a.setLastErrorAt(clock.instant());
            return true;
        });
    }

    /**
     * A failure preparing the call (provider load, request snapshot) before issue() is ever reached: issueCalls
     * stays 0, so {@link #reschedule} would otherwise retry it every minute forever; {@code preSendFailures} gives
     * it its own growing backoff instead.
     */
    private void recordPreSendFailure(ReceiptAttempt attempt, RuntimeException e) {
        attempts.update(attempt.getStoreId(), attempt.getReceiptKey(), a -> {
            a.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
            a.setLastErrorAt(clock.instant());
            a.setPreSendFailures(a.getPreSendFailures() + 1);
            return true;
        });
    }

    /** find() only describes the situation for the operator; it never changes the attempt's state. */
    private String diagnose(ReceiptAttempt attempt) {
        try {
            return provider(attempt).find(attempt.getReceiptKey())
                    .map(r -> " [find: " + r.state() + " " + r.providerReceiptId() + "]")
                    .orElse(" [find: nothing under this key]");
        } catch (RuntimeException e) {
            return " [find failed: " + e.getMessage() + "]";
        }
    }

    /**
     * An attempt this call no longer owns (lease stolen or expired and re-taken) is left untouched: no reschedule,
     * no alert sync, no lease change — the real owner's {@code finish} is the only one allowed to decide those.
     */
    private void finish(String storeId, String receiptKey, String owner, boolean effectsFailed) {
        Instant now = clock.instant();
        attempts.update(storeId, receiptKey, a -> {
            if (!owner.equals(a.getLeaseOwner())) {
                log.error("Receipt attempt {} lost its lease while being processed", receiptKey);
                return false;
            }
            a.setLeaseOwner(null);
            a.setLeaseUntil(null);
            reschedule(a, now, effectsFailed);
            syncAlert(a, now);
            return true;
        });
    }

    /**
     * A notification-store failure here must never cost the lease release or the schedule write above: both are
     * already staged on {@code a} in this same predicate run, so swallowing the exception still lets {@code update}
     * save them. Nothing set by this method is read back outside the predicate, so there is no R3 risk in retrying.
     */
    private void syncAlert(ReceiptAttempt a, Instant now) {
        try {
            alerts.sync(a, ReceiptAttentionEvaluator.evaluate(a, now));
        } catch (RuntimeException e) {
            log.error("Receipt attempt {} attention sync failed; lease release and schedule are saved regardless",
                    a.getReceiptKey(), e);
        }
    }

    private void reschedule(ReceiptAttempt a, Instant now, boolean effectsFailed) {
        switch (a.getState()) {
            case ISSUING -> a.schedule(a.getIssueCalls() == 0
                    // Nothing has been sent yet: growing backoff on preSendFailures instead of retrying every
                    // minute forever (issueCalls stays 0 the whole time this branch applies).
                    ? ReceiptSchedule.nextIssue(Math.max(a.getPreSendFailures(), 1), now)
                    : ReceiptSchedule.nextIssue(a.getIssueCalls(), now));
            case PENDING -> a.schedule(ReceiptSchedule.nextPending(a, now));
            case FISCALISED -> {
                if (effectsFailed || ReceiptEffects.pending(a)) {
                    a.schedule(now.plus(EFFECTS_RETRY));
                } else if (a.getDocumentUrl() == null && a.getLinkGaveUpAt() == null) {
                    Instant next = ReceiptSchedule.nextLink(a, now);
                    if (next == null) {
                        a.setLinkGaveUpAt(now);
                        a.unschedule();
                    } else {
                        a.schedule(next);
                    }
                } else {
                    a.unschedule();
                }
            }
            default -> a.unschedule();
        }
    }

    private boolean stillQualifies(ReceiptAttempt attempt) {
        Order order = ordersRepository.findById(attempt.getStoreId(), attempt.getOrderId());
        return order != null && eligibility.orderQualifies(order);
    }

    private ReceiptProvider provider(ReceiptAttempt attempt) {
        Store store = storesRepository.findById(attempt.getStoreId());
        if (store == null) {
            throw new IllegalStateException("Store " + attempt.getStoreId() + " not found");
        }
        return providerFactory.get(store, attempt.getProvider());
    }
}
