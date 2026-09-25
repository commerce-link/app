package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.provider.ProviderCallRejectedException;
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
    private static final Duration EFFECTS_RETRY_CAP = Duration.ofHours(6);

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
            // attempt this call no longer owns, nor under a lease that has already expired (an operator's resend
            // is allowed on an unleased attempt and would race the e-mail step).
            if (renewLease(storeId, receiptKey, owner)) {
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

    /**
     * Whether this call still holds a live lease, renewing it for the effects pass that follows: the issue or poll
     * before it may have taken most of the lease, and effects must finish under a lease no resend can slip past.
     */
    private boolean renewLease(String storeId, String receiptKey, String owner) {
        Instant now = clock.instant();
        return attempts.updateWritten(storeId, receiptKey, a -> {
            if (!owner.equals(a.getLeaseOwner()) || !a.isLeasedAt(now)) {
                return false;
            }
            a.setLeaseUntil(now.plus(LEASE));
            return true;
        }).isPresent();
    }

    private Optional<ReceiptAttempt> acquire(String storeId, String receiptKey, String owner) {
        Instant now = clock.instant();
        return attempts.updateWritten(storeId, receiptKey, a -> {
            if (a.isLeasedAt(now)) {
                return false;
            }
            a.setLeaseOwner(owner);
            a.setLeaseUntil(now.plus(LEASE));
            return true;
        });
    }

    private void issue(ReceiptAttempt attempt, String owner) {
        String storeId = attempt.getStoreId();
        String key = attempt.getReceiptKey();
        if (attempt.getIssueCalls() == 0 && !stillQualifies(attempt)) {
            // stillQualifies() is exactly the slow window (an order lookup) in which the lease can be lost to
            // another owner who has since bumped issueCalls and may be inside provider.issue right now — blocking
            // unconditionally here would kill that attempt and its eventual result would be dropped by the merger
            // (BLOCKED ignores everything), losing a real sale. Block only while the lease is still ours.
            Instant blockNow = clock.instant();
            boolean blockedByUs = attempts.updateWritten(storeId, key, a -> {
                if (!owner.equals(a.getLeaseOwner()) || !a.isLeasedAt(blockNow)
                        || a.getState() != ReceiptAttemptState.ISSUING || a.getIssueCalls() != 0) {
                    return false;
                }
                a.setState(ReceiptAttemptState.BLOCKED);
                a.setBlockedReason(ReceiptBlockReason.NOT_ELIGIBLE.name());
                return true;
            }).isPresent();
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
        Instant guardNow = clock.instant();
        boolean stillOurs = attempts.updateWritten(storeId, key, a -> {
            if (!owner.equals(a.getLeaseOwner()) || !a.isLeasedAt(guardNow) || a.getState() != ReceiptAttemptState.ISSUING) {
                return false;
            }
            a.setIssueCalls(a.getIssueCalls() + 1);   // recorded before the call: the count survives a crash
            a.setLeaseUntil(guardNow.plus(LEASE));    // renew: the call itself can take a while, close to the lease
            return true;
        }).isPresent();
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
        } catch (ProviderCallRejectedException e) {
            // The limiter never made the call: undo the issueCalls bump from the guard above (nothing was sent)
            // and count it as a pre-send failure instead, exactly like a failure before the call was ever attempted.
            attempts.update(storeId, key, a -> {
                if (!owner.equals(a.getLeaseOwner()) || a.getState() != ReceiptAttemptState.ISSUING) {
                    return false;
                }
                a.setIssueCalls(a.getIssueCalls() - 1);
                a.setPreSendFailures(a.getPreSendFailures() + 1);
                a.setLastError(e.getMessage());
                a.setLastErrorAt(clock.instant());
                return true;
            });
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
                if (effectsFailed) {
                    // Growing backoff instead of retrying a broken effects run (e.g. the order was deleted) every
                    // 5 minutes forever with nobody told; nextEffectsRetry alerts the operator once it repeats.
                    a.setEffectsFailures(a.getEffectsFailures() + 1);
                    a.schedule(nextEffectsRetry(a.getEffectsFailures(), now));
                } else if (ReceiptEffects.pending(a)) {
                    a.schedule(now.plus(EFFECTS_RETRY));
                } else if (a.getDocumentUrl() == null && a.getLinkGaveUpAt() == null) {
                    a.setEffectsFailures(0);
                    Instant next = ReceiptSchedule.nextLink(a, now);
                    if (next == null) {
                        a.setLinkGaveUpAt(now);
                        a.unschedule();
                    } else {
                        a.schedule(next);
                    }
                } else {
                    a.setEffectsFailures(0);
                    a.unschedule();
                }
            }
            default -> a.unschedule();
        }
    }

    /** After the {@code effectsFailures}-th consecutive failed effects run: 5 min x 2^(effectsFailures-1), capped
     *  at 6 h. */
    private static Instant nextEffectsRetry(int effectsFailures, Instant now) {
        int exponent = Math.min(effectsFailures - 1, 32);   // guards the shift; the cap below applies long before this
        Duration delay = EFFECTS_RETRY.multipliedBy(1L << exponent);
        if (delay.compareTo(EFFECTS_RETRY_CAP) > 0) {
            delay = EFFECTS_RETRY_CAP;
        }
        return now.plus(delay);
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
