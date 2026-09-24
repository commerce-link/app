package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.receipts.api.ReceiptProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates receipt attempts and carries out operator actions. A new key is created only when every earlier attempt
 * of the order is dead; the key itself is created conditionally, so two concurrent requests create one attempt.
 */
@Slf4j
@Service
public class ReceiptAttemptService {

    static final String SYSTEM = "System";

    private final ReceiptAttemptStore attempts;
    private final StoresRepository storesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final ReceiptProviderFactory providerFactory;
    private final ReceiptRequestConverter converter;
    private final ReceiptEligibility eligibility;
    private final ReceiptWorkPublisher publisher;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final OrderLifecycle orderLifecycle;
    private final ReceiptAlerts alerts;
    private final Clock clock;

    @Autowired
    public ReceiptAttemptService(ReceiptAttemptStore attempts, StoresRepository storesRepository,
                                 OrdersRepository ordersRepository, OrderItemsRepository orderItemsRepository,
                                 ReceiptProviderFactory providerFactory, ReceiptRequestConverter converter,
                                 ReceiptEligibility eligibility, ReceiptWorkPublisher publisher,
                                 OptimisticLockingExecutor optimisticLockingExecutor,
                                 @org.springframework.context.annotation.Lazy OrderLifecycle orderLifecycle,
                                 ReceiptAlerts alerts) {
        this(attempts, storesRepository, ordersRepository, orderItemsRepository, providerFactory, converter,
                eligibility, publisher, optimisticLockingExecutor, orderLifecycle, alerts, Clock.systemDefaultZone());
    }

    ReceiptAttemptService(ReceiptAttemptStore attempts, StoresRepository storesRepository,
                          OrdersRepository ordersRepository, OrderItemsRepository orderItemsRepository,
                          ReceiptProviderFactory providerFactory, ReceiptRequestConverter converter,
                          ReceiptEligibility eligibility, ReceiptWorkPublisher publisher,
                          OptimisticLockingExecutor optimisticLockingExecutor, OrderLifecycle orderLifecycle,
                          ReceiptAlerts alerts, Clock clock) {
        this.attempts = attempts;
        this.storesRepository = storesRepository;
        this.ordersRepository = ordersRepository;
        this.orderItemsRepository = orderItemsRepository;
        this.providerFactory = providerFactory;
        this.converter = converter;
        this.eligibility = eligibility;
        this.publisher = publisher;
        this.optimisticLockingExecutor = optimisticLockingExecutor;
        this.orderLifecycle = orderLifecycle;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** The first attempt of a delivered order, unless the order already has one. */
    public Optional<ReceiptAttempt> startAutomatic(Store store, Order order) {
        if (!attempts.findByOrder(store.getStoreId(), order.getOrderId()).isEmpty()) {
            return Optional.empty();
        }
        return create(store, order, 1, SYSTEM);
    }

    public ReceiptAttempt reissue(String storeId, String orderId, String actor) {
        List<ReceiptAttempt> existing = attempts.findByOrder(storeId, orderId);
        if (existing.isEmpty()) {
            throw new ReceiptActionException("receipts.action.reissue.none");
        }
        if (existing.stream().anyMatch(a -> !a.getState().isDead())) {
            throw new ReceiptActionException("receipts.action.reissue.live");
        }
        Store store = storesRepository.findById(storeId);
        Order order = ordersRepository.findById(storeId, orderId);
        if (store == null || order == null || !eligibility.orderQualifies(order)) {
            throw new ReceiptActionException("receipts.action.reissue.notEligible");
        }
        String providerName = store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER);
        if (providerName == null || providerFactory.getDescriptor(providerName) == null) {
            // No provider chosen, or the chosen one's adapter is gone (uninstalled): either way there is nothing
            // to issue with, and going on would fail deep inside convert() instead of with a clean refusal here.
            throw new ReceiptActionException("receipts.action.reissue.noProvider");
        }
        int next = existing.stream().mapToInt(ReceiptAttempt::getAttemptNo).max().orElse(0) + 1;
        ReceiptAttempt created = create(store, order, next, actor)
                .orElseThrow(() -> new ReceiptActionException("receipts.action.reissue.concurrent"));
        // Every attempt in `existing` is dead (checked above) and is being superseded by `created`: its bell
        // alert, if any, no longer needs the operator's attention. The attempt's own `attention` field is left
        // untouched, so the order page still shows why it needed correcting.
        existing.forEach(alerts::resolve);
        return created;
    }

    public void checkNow(String storeId, String receiptKey) {
        ReceiptAttempt attempt = attempts.update(storeId, receiptKey, a -> {
                    if (!a.isScheduled()) {
                        return false;
                    }
                    a.schedule(clock.instant());
                    return true;
                })
                .orElseThrow(() -> new ReceiptActionException("receipts.action.notFound"));
        if (!attempt.isScheduled()) {
            throw new ReceiptActionException("receipts.action.check.nothingToDo");
        }
        publisher.publishNow(storeId, receiptKey);
    }

    /**
     * The operator resolved a hung attempt at the provider (e.g. fiscalised it by hand once) and records the result:
     * the order gets the operator's document and the attempt is never polled or issued again. Refused while the
     * processor holds the lease — an issue in flight may still order fiscalisation.
     * <p>Retryable: if the order write below failed on an earlier call, the attempt is already {@code CLOSED_MANUALLY}
     * with this same number, so the attempt update is skipped (nothing left to change) and only the idempotent order
     * step is redone.
     */
    public void closeManually(String storeId, String receiptKey, String number, String link, String actor) {
        if (StringUtils.isBlank(number)) {
            throw new ReceiptActionException("receipts.action.close.numberRequired");
        }
        if (!isValidLink(link)) {
            throw new ReceiptActionException("receipts.action.close.invalidLink");
        }
        String receiptNumber = number.strip();
        Instant now = clock.instant();
        ReceiptAttempt[] closed = new ReceiptAttempt[1];
        attempts.update(storeId, receiptKey, a -> {
            closed[0] = null;   // reset on every run: a value from an earlier, conflicting run must never survive (R3)
            if (a.getState() == ReceiptAttemptState.CLOSED_MANUALLY) {
                if (!Objects.equals(a.getReceiptNumber(), receiptNumber)) {
                    throw new ReceiptActionException("receipts.action.close.notHung");
                }
                closed[0] = a;   // already closed with this number: an earlier call's order step failed, retry only that
                return false;
            }
            if (a.getState() != ReceiptAttemptState.ISSUING && a.getState() != ReceiptAttemptState.PENDING) {
                throw new ReceiptActionException("receipts.action.close.notHung");
            }
            if (a.isLeasedAt(now)) {
                throw new ReceiptActionException("receipts.action.close.busy");
            }
            a.setState(ReceiptAttemptState.CLOSED_MANUALLY);
            a.setReceiptNumber(receiptNumber);
            a.setDocumentUrl(blankToNull(link));
            a.setLastError("Closed manually by " + actor);
            a.setLastErrorAt(now);
            a.unschedule();
            closed[0] = a;
            return true;
        }).orElseThrow(() -> new ReceiptActionException("receipts.action.notFound"));
        ReceiptAttempt attempt = closed[0];
        Document document = new Document(receiptKey, receiptNumber, blankToNull(link), DocumentType.Receipt,
                LocalDate.now(clock));
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(storeId, attempt.getOrderId()),
                order -> order.addDocumentIfMissing(document),
                this::saveThroughLifecycle);
    }

    /**
     * The operator retries a real e-mail failure — the send was attempted and failed, unlike an attempt whose
     * {@code emailSkippedAt} shows the store simply does not send this e-mail type or the buyer has no address,
     * which need no operator action. Clears the claim so a fresh {@link ReceiptEffects#apply} tries the send again,
     * resolves the now-obsolete bell alert, and wakes the attempt on the work queue the same way {@code checkNow}
     * does — a FISCALISED attempt whose e-mail step just finished is otherwise not scheduled on its own, so only
     * clearing the claim would leave it waiting for the next sweep.
     * <p>Refused while the processor's lease is held (mirrors {@link #closeManually}): while
     * {@link ReceiptEffects#apply} is claiming and sending the e-mail, the attempt already satisfies
     * {@link ReceiptAttempt#emailFailed()} (claimed, not yet sent) even though nothing has failed yet — clearing the
     * claim then would race the in-flight send and could get the e-mail sent twice. The lease check must run before
     * the {@code emailFailed()} check for exactly that reason: during the race window {@code emailFailed()} alone
     * would say "eligible".
     */
    public void resendEmail(String storeId, String orderId, String receiptKey, String by) {
        if (!receiptKey.startsWith(ReceiptAttemptKeys.orderPrefix(orderId))) {
            throw new ReceiptActionException("receipts.action.notFound");
        }
        Instant now = clock.instant();
        ReceiptAttempt attempt = attempts.update(storeId, receiptKey, a -> {
                    if (a.isLeasedAt(now)) {
                        throw new ReceiptActionException("receipts.action.resendEmail.busy");
                    }
                    if (!a.emailFailed()) {
                        return false;
                    }
                    a.setEmailClaimedAt(null);
                    a.schedule(now);
                    return true;
                })
                .orElseThrow(() -> new ReceiptActionException("receipts.action.notFound"));
        if (attempt.getEmailClaimedAt() != null) {
            throw new ReceiptActionException("receipts.action.resendEmail.notEligible");
        }
        log.info("Receipt attempt {} e-mail resend requested by {}", receiptKey, by);
        alerts.resolve(attempt);
        publisher.publishNow(storeId, receiptKey);
    }

    public List<ReceiptAttempt> attemptsOf(String storeId, String orderId) {
        return attempts.findByOrder(storeId, orderId);
    }

    public boolean hasLiveAttempt(String storeId, String orderId) {
        return attemptsOf(storeId, orderId).stream().anyMatch(a -> a.getState().isLive());
    }

    /**
     * The order screen's e-receipt section: every attempt of the order and whether the operator may issue a new one.
     * {@code locale} is the viewer's own request locale, not the fixed operator locale bell notifications use.
     */
    public ReceiptOrderView orderView(Order order, ReceiptAlerts alerts, Locale locale) {
        return ReceiptOrderView.of(attemptsOf(order.getStoreId(), order.getOrderId()),
                eligibility.orderQualifies(order), alerts, clock.instant(), locale);
    }

    void saveThroughLifecycle(Order order) {
        if (order.getStatus() == OrderStatus.Cancelled) {
            ordersRepository.save(order);   // the lifecycle ignores cancelled orders and would not save
        } else {
            orderLifecycle.update(order);
        }
    }

    private Optional<ReceiptAttempt> create(Store store, Order order, int attemptNo, String actor) {
        Instant now = clock.instant();
        String key = ReceiptAttemptKeys.of(order.getOrderId(), attemptNo);
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId(store.getStoreId());
        attempt.setReceiptKey(key);
        attempt.setOrderId(order.getOrderId());
        attempt.setAttemptNo(attemptNo);
        attempt.setProvider(store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER));
        attempt.setCreatedAt(now);
        attempt.setCreatedBy(actor);
        ReceiptConversion conversion = convert(store, order, key, attempt.getProvider());
        if (conversion instanceof ReceiptConversion.Converted converted) {
            attempt.setState(ReceiptAttemptState.ISSUING);
            attempt.setRequestSnapshot(ReceiptSnapshotJson.write(converted.snapshot()));
        } else {
            ReceiptConversion.Blocked blocked = (ReceiptConversion.Blocked) conversion;
            attempt.setState(ReceiptAttemptState.BLOCKED);
            attempt.setBlockedReason(blocked.reason().name());
            attempt.setBlockedDetail(blocked.detail());
        }
        attempt.schedule(now);   // blocked attempts wake once too, to raise the operator alert
        if (!attempts.create(attempt)) {
            return Optional.empty();
        }
        try {
            publisher.publishDue(attempt);
        } catch (RuntimeException e) {
            log.warn("Receipt attempt {} saved but not queued; the sweep will pick it up", key, e);
        }
        return Optional.of(attempt);
    }

    private ReceiptConversion convert(Store store, Order order, String key, String providerName) {
        ReceiptProvider provider;
        try {
            provider = providerFactory.get(store, providerName);
        } catch (RuntimeException e) {
            log.warn("Receipt provider {} of store {} unavailable", providerName, store.getStoreId(), e);
            return new ReceiptConversion.Blocked(ReceiptBlockReason.PROVIDER_UNAVAILABLE, e.getMessage());
        }
        if (provider == null) {
            // No descriptor for this name (adapter not on the classpath): providerFactory.get() returns null
            // rather than throwing, so this is checked separately from the catch above.
            log.warn("Receipt provider {} of store {} has no descriptor (adapter missing)", providerName, store.getStoreId());
            return new ReceiptConversion.Blocked(ReceiptBlockReason.PROVIDER_UNAVAILABLE, null);
        }
        return converter.convert(order, orderItemsRepository.findByOrderId(order.getOrderId()), key, provider,
                LocalDateTime.now(clock));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Blank (no link yet) or an http(s) URL: never a scheme an operator could paste in to run script in a browser. */
    private static boolean isValidLink(String link) {
        if (StringUtils.isBlank(link)) {
            return true;
        }
        String trimmed = link.strip();
        return StringUtils.startsWithIgnoreCase(trimmed, "http://") || StringUtils.startsWithIgnoreCase(trimmed, "https://");
    }
}
