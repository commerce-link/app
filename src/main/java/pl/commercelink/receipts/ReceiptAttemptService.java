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
    /** Why "E-paragon" and "Wystaw ponownie" are refused (and greyed) for a POS sale without the customer's e-mail. */
    public static final String POS_NEEDS_EMAIL = "receipts.action.posNeedsEmail";

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
        Reissue reissue = checkReissue(storeId, orderId, existing);
        int next = existing.stream().mapToInt(ReceiptAttempt::getAttemptNo).max().orElse(0) + 1;
        ReceiptAttempt created = create(reissue.store(), reissue.order(), next, actor)
                .orElseThrow(() -> new ReceiptActionException("receipts.action.reissue.concurrent"));
        // Every attempt in `existing` is dead (checked above) and is being superseded by `created`: its bell
        // alert, if any, no longer needs the operator's attention. The attempt's own `attention` field is left
        // untouched, so the order page still shows why it needed correcting.
        existing.forEach(alerts::resolve);
        return created;
    }

    /**
     * Why {@link #reissue} would refuse the order now (its message key), null when it would go ahead: the
     * confirmation page asks only what the POST would do.
     */
    public String reissueRefusal(String storeId, String orderId) {
        try {
            checkReissue(storeId, orderId, attempts.findByOrder(storeId, orderId));
            return null;
        } catch (ReceiptActionException e) {
            return e.getMessageKey();
        }
    }

    private Reissue checkReissue(String storeId, String orderId, List<ReceiptAttempt> existing) {
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
        if (!hasProvider(store)) {
            throw new ReceiptActionException("receipts.action.reissue.noProvider");
        }
        refusePosWithoutCustomerEmail(store, order);
        return new Reissue(store, order);
    }

    /**
     * A point-of-sale sale without the customer's e-mail would only get one more blocked attempt (and one more bell
     * alert): refused before any attempt is created, with the reason the order page shows under the greyed button.
     */
    private static void refusePosWithoutCustomerEmail(Store store, Order order) {
        if (ReceiptRequestConverter.blocksPosWithoutCustomerEmail(order, store)) {
            throw new ReceiptActionException(POS_NEEDS_EMAIL);
        }
    }

    /**
     * The "Wystaw ponownie" confirmation: a point-of-sale sale adds the cash register warning (its receipt may have
     * been printed there meanwhile). The same key in the dialog and on the page without JavaScript.
     */
    public static String reissueConfirmMessageKey(Order order) {
        return order != null && order.isPointOfSale()
                ? "receipts.action.reissue.confirm.message.pos" : "receipts.action.reissue.confirm.message";
    }

    /** {@link #reissueConfirmMessageKey(Order)} for the confirmation page, which has only the order's id. */
    public String reissueConfirmMessageKey(String storeId, String orderId) {
        return reissueConfirmMessageKey(ordersRepository.findById(storeId, orderId));
    }

    private record Reissue(Store store, Order order) {
    }

    /**
     * The order's first e-receipt, issued by the operator from the order's documents like an invoice. Unlike the
     * automatic start it does not wait for delivery and does not need the store's automation switched on — only a
     * provider to issue with; the attempt then runs exactly like an automatic one. An order that already has an
     * attempt is refused: a live one owns the receipt, a dead one is replaced with "Wystaw ponownie".
     */
    public ReceiptAttempt issueManually(String storeId, String orderId, String actor) {
        Reissue issue = checkIssue(storeId, orderId);
        return create(issue.store(), issue.order(), 1, actor)
                .orElseThrow(() -> new ReceiptActionException("receipts.action.reissue.concurrent"));
    }

    /** Why {@link #issueManually} would refuse the order now (its message key), null when it would go ahead. */
    public String issueRefusal(String storeId, String orderId) {
        try {
            checkIssue(storeId, orderId);
            return null;
        } catch (ReceiptActionException e) {
            return e.getMessageKey();
        }
    }

    private Reissue checkIssue(String storeId, String orderId) {
        if (!attempts.findByOrder(storeId, orderId).isEmpty()) {
            throw new ReceiptActionException("receipts.action.issue.exists");
        }
        Store store = storesRepository.findById(storeId);
        Order order = ordersRepository.findById(storeId, orderId);
        if (store == null || order == null || !eligibility.orderQualifies(order)) {
            throw new ReceiptActionException("receipts.action.reissue.notEligible");
        }
        if (!hasProvider(store)) {
            throw new ReceiptActionException("receipts.action.reissue.noProvider");
        }
        refusePosWithoutCustomerEmail(store, order);
        return new Reissue(store, order);
    }

    /** Whether the order documents offer "E-paragon": the conditions of {@link #issueManually} hold. */
    public boolean canIssueManually(Store store, Order order) {
        return canIssueManually(store, order, attemptsOf(order.getStoreId(), order.getOrderId()));
    }

    /** {@link #canIssueManually(Store, Order)} over attempts the caller already read. */
    boolean canIssueManually(Store store, Order order, List<ReceiptAttempt> orderAttempts) {
        return store != null && hasProvider(store) && eligibility.orderQualifies(order) && orderAttempts.isEmpty();
    }

    /**
     * No provider chosen, or the chosen one's adapter is gone (uninstalled): either way there is nothing to issue
     * with, and going on would fail deep inside convert() instead of with a clean refusal.
     */
    private boolean hasProvider(Store store) {
        String providerName = store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER);
        return providerName != null && providerFactory.getDescriptor(providerName) != null;
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
        ReceiptAttempt attempt = attempts.update(storeId, receiptKey, a -> {
            if (a.getState() == ReceiptAttemptState.CLOSED_MANUALLY) {
                if (!Objects.equals(a.getReceiptNumber(), receiptNumber)) {
                    throw new ReceiptActionException("receipts.action.close.notHung");
                }
                return false;   // already closed with this number: an earlier call's order step failed, retry only that
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
            return true;
        }).orElseThrow(() -> new ReceiptActionException("receipts.action.notFound"));
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
        // updateWritten, not update: a declined resend (nothing to resend) must not go on to resolve the bell and
        // wake the attempt — for an ISSUING attempt that would silently drop its ISSUING_UNKNOWN alert for good.
        ReceiptAttempt attempt = attempts.updateWritten(storeId, receiptKey, a -> {
                    if (a.isLeasedAt(now)) {
                        throw new ReceiptActionException("receipts.action.resendEmail.busy");
                    }
                    if (!a.emailFailed()) {
                        return false;
                    }
                    a.setEmailClaimedAt(null);
                    // The bell is resolved below; forgetting the stored reason too lets ReceiptAlerts.sync raise
                    // EMAIL_NOT_SENT afresh if the resent mail fails again (an unchanged reason publishes nothing).
                    a.setAttention(null);
                    a.schedule(now);
                    return true;
                })
                .orElseThrow(() -> attempts.find(storeId, receiptKey).isPresent()
                        ? new ReceiptActionException("receipts.action.resendEmail.notEligible")
                        : new ReceiptActionException("receipts.action.notFound"));
        log.info("Receipt attempt {} e-mail resend requested by {}", receiptKey, by);
        alerts.resolve(attempt);
        publisher.publishNow(storeId, receiptKey);
    }

    public List<ReceiptAttempt> attemptsOf(String storeId, String orderId) {
        return attempts.findByOrder(storeId, orderId);
    }

    /**
     * Brings the bell in line with what the order page shows for the order's dead attempts
     * ({@link ReceiptTrigger#settlesDeadAttempts}, the rule {@link ReceiptOrderView} reads at every render): when the
     * order settles them, their alerts are resolved (their stored attention stays, so the page still shows why they
     * stopped; live attempts keep theirs, a fiscalised receipt whose e-mail failed still needs sending); otherwise the
     * newest attempt, when dead, has its alert raised again (it may have been resolved by a closing document that is
     * gone now). Earlier, superseded attempts are left resolved: only the newest one asks for anything. Called by
     * every write that can change the rule's inputs: an order saved through the lifecycle, a document unpinned, an
     * invoice issued, the order cancelled, an attempt that died while the order changed under it. Costs one strongly
     * consistent query of the order's own key range ({@link ReceiptAttemptStore#findByOrder}), empty for an order that
     * never had an attempt; writes to the bell only for an order with a dead attempt. Never throws: the alerts must
     * not break the order write.
     */
    public void reconcileDeadAttemptAlerts(Order order) {
        try {
            List<ReceiptAttempt> orderAttempts = attemptsOf(order.getStoreId(), order.getOrderId());
            if (ReceiptTrigger.settlesDeadAttempts(order)) {
                orderAttempts.stream().filter(a -> a.getState().isDead()).forEach(alerts::resolve);
                return;
            }
            orderAttempts.stream()
                    .max((a, b) -> Integer.compare(a.getAttemptNo(), b.getAttemptNo()))
                    .filter(a -> a.getState().isDead())
                    .ifPresent(this::raiseAgain);
        } catch (RuntimeException e) {
            // WARN, not ERROR: the lifecycle cron reconciles every open order, so an outage of the notifications
            // table would raise one alert per order per run; the next save reconciles again
            log.warn("Receipt alerts of order {} of store {} could not be reconciled",
                    order.getOrderId(), order.getStoreId(), e);
        }
    }

    /** {@link #reconcileDeadAttemptAlerts(Order)} for a caller without the saved order at hand: reads it afresh. */
    public void reconcileDeadAttemptAlerts(String storeId, String orderId) {
        try {
            Order order = ordersRepository.findById(storeId, orderId);
            if (order != null) {
                reconcileDeadAttemptAlerts(order);
            }
        } catch (RuntimeException e) {
            log.warn("Receipt alerts of order {} of store {} could not be reconciled", orderId, storeId, e);
        }
    }

    private void raiseAgain(ReceiptAttempt newest) {
        ReceiptAttention attention = ReceiptAttentionEvaluator.evaluate(newest, clock.instant());
        if (attention == null || !alerts.republish(newest, attention)) {
            return;
        }
        // the stored reason was not the one raised (the processor kept the bell silent for a settled order): record
        // it, so the next reconcile publishes into the existing record instead of replacing it
        attempts.update(newest.getStoreId(), newest.getReceiptKey(), a -> {
            if (attention.name().equals(a.getAttention())) {
                return false;
            }
            a.setAttention(attention.name());
            return true;
        });
    }

    /**
     * Whether an attempt already owns the order's receipt, so a manual "add Receipt" would give it a second one: the
     * attempt is issuing or fiscalised, or an operator closed it with the document they resolved at the provider.
     */
    public boolean blocksManualReceipt(String storeId, String orderId) {
        return blocksManualReceipt(attemptsOf(storeId, orderId));
    }

    /** {@link #blocksManualReceipt(String, String)} over attempts the caller already read. */
    public static boolean blocksManualReceipt(List<ReceiptAttempt> orderAttempts) {
        return orderAttempts.stream()
                .anyMatch(a -> a.getState().isLive() || a.getState() == ReceiptAttemptState.CLOSED_MANUALLY);
    }

    /**
     * Whether the order's edits are locked for its e-receipt ({@link ReceiptOrderState#locksOrder}): the check an
     * order edit endpoint runs before changing anything the receipt's frozen request depends on.
     */
    public boolean locksOrder(Order order) {
        return receiptLock(order).locks();
    }

    /**
     * {@link #locksOrder} with why ({@link ReceiptOrderState#receiptLock}): still being issued, or fiscalised and
     * being attached to the order; the edit endpoints word their refusal after it.
     */
    public ReceiptLock receiptLock(Order order) {
        return ReceiptOrderState.receiptLock(attemptsOf(order.getStoreId(), order.getOrderId()), order);
    }

    /**
     * Whether the order has a fiscalised (or manually closed) e-receipt ({@link ReceiptOrderState#hasFiscalisedReceipt}):
     * the cancel confirmation warns that cancelling the order does not undo it.
     */
    public boolean hasFiscalisedReceipt(Order order) {
        return ReceiptOrderState.hasFiscalisedReceipt(attemptsOf(order.getStoreId(), order.getOrderId()), order);
    }

    /**
     * The order screen's e-receipt section: every attempt of the order and whether the operator may issue a new one.
     * {@code locale} is the viewer's own request locale, not the fixed operator locale bell notifications use.
     */
    public ReceiptOrderView orderView(Order order, ReceiptAlerts alerts, Locale locale) {
        return orderView(order, attemptsOf(order.getStoreId(), order.getOrderId()), alerts, locale);
    }

    private ReceiptOrderView orderView(Order order, List<ReceiptAttempt> orderAttempts, ReceiptAlerts alerts, Locale locale) {
        return ReceiptOrderView.of(orderAttempts, eligibility.orderQualifies(order),
                ReceiptTrigger.settlesDeadAttempts(order), alerts, clock.instant(), locale);
    }

    /**
     * The order page's whole e-receipt picture from a single read of the order's attempts: the rows, whether
     * "E-paragon" may be issued and whether an attempt owns the receipt. The same rules as {@link #orderView},
     * {@link #canIssueManually(Store, Order)} and {@link #blocksManualReceipt(String, String)}, which each read the
     * attempts again. A failing attempts store fails the page, as the old order page did: the operator must not see
     * an order without its receipt and be offered a second one.
     */
    public ReceiptOrderState orderState(Store store, Order order, ReceiptAlerts alerts, Locale locale) {
        List<ReceiptAttempt> orderAttempts = attemptsOf(order.getStoreId(), order.getOrderId());
        return new ReceiptOrderState(orderAttempts, orderView(order, orderAttempts, alerts, locale),
                canIssueManually(store, order, orderAttempts), blocksManualReceipt(orderAttempts));
    }

    void saveThroughLifecycle(Order order) {
        if (order.getStatus() == OrderStatus.Cancelled) {
            ordersRepository.save(order);   // the lifecycle ignores cancelled orders and would not save
            reconcileDeadAttemptAlerts(order);
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
        String storeEmail = store.getBillingDetails() == null ? null : store.getBillingDetails().getEmail();
        return converter.convert(order, orderItemsRepository.findByOrderId(order.getOrderId()), key, provider,
                LocalDateTime.now(clock), storeEmail);
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
