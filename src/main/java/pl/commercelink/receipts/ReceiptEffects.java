package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * What a fiscalised receipt sets off, each step once (marked on the attempt): the receipt document on the order
 * (which closes a paid, delivered order), the marketplace event and the buyer's e-mail. The document is attached even
 * without a link — the sale is registered — while the event and the e-mail wait for the link. The e-mail is claimed
 * before it is sent: a crash in between loses it instead of sending two.
 */
@Slf4j
@Component
public class ReceiptEffects {

    static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final ReceiptAttemptStore attempts;
    private final OrdersRepository ordersRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final ReceiptAttemptService attemptService;
    private final OrderLifecycleEventPublisher lifecycleEventPublisher;
    private final EmailClient emailClient;
    private final OrderEventsRepository orderEventsRepository;
    private final StoresRepository storesRepository;
    private final Clock clock;

    @Autowired
    public ReceiptEffects(ReceiptAttemptStore attempts, OrdersRepository ordersRepository,
                          OptimisticLockingExecutor optimisticLockingExecutor, ReceiptAttemptService attemptService,
                          OrderLifecycleEventPublisher lifecycleEventPublisher, EmailClient emailClient,
                          OrderEventsRepository orderEventsRepository, StoresRepository storesRepository) {
        this(attempts, ordersRepository, optimisticLockingExecutor, attemptService, lifecycleEventPublisher,
                emailClient, orderEventsRepository, storesRepository, Clock.systemUTC());
    }

    ReceiptEffects(ReceiptAttemptStore attempts, OrdersRepository ordersRepository,
                   OptimisticLockingExecutor optimisticLockingExecutor, ReceiptAttemptService attemptService,
                   OrderLifecycleEventPublisher lifecycleEventPublisher, EmailClient emailClient,
                   OrderEventsRepository orderEventsRepository, StoresRepository storesRepository, Clock clock) {
        this.attempts = attempts;
        this.ordersRepository = ordersRepository;
        this.optimisticLockingExecutor = optimisticLockingExecutor;
        this.attemptService = attemptService;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.emailClient = emailClient;
        this.orderEventsRepository = orderEventsRepository;
        this.storesRepository = storesRepository;
        this.clock = clock;
    }

    /** Whether a fiscalised attempt still has a step to do now. */
    public static boolean pending(ReceiptAttempt a) {
        if (a.getState() != ReceiptAttemptState.FISCALISED) {
            return false;
        }
        boolean linked = a.getDocumentUrl() != null;
        return a.getDocumentAttachedAt() == null
                || linked && (a.getDocumentLinkedAt() == null || a.getMarketplaceNotifiedAt() == null
                || a.getEmailClaimedAt() == null);
    }

    public void apply(String storeId, String receiptKey) {
        Optional<ReceiptAttempt> loaded = attempts.find(storeId, receiptKey);
        if (loaded.isEmpty() || !pending(loaded.get())) {
            return;
        }
        ReceiptAttempt attempt = loaded.get();
        boolean linked = attempt.getDocumentUrl() != null;
        if (attempt.getDocumentAttachedAt() == null || linked && attempt.getDocumentLinkedAt() == null) {
            Order order = attachDocument(attempt);
            Instant now = clock.instant();
            attempts.update(storeId, receiptKey, a -> {
                a.setDocumentAttachedAt(a.getDocumentAttachedAt() != null ? a.getDocumentAttachedAt() : now);
                if (attempt.getDocumentUrl() != null) {
                    a.setDocumentLinkedAt(now);
                }
                return true;
            });
            if (linked && attempt.getMarketplaceNotifiedAt() == null) {
                notifyMarketplace(storeId, receiptKey, order);
            }
        } else if (linked && attempt.getMarketplaceNotifiedAt() == null) {
            notifyMarketplace(storeId, receiptKey, ordersRepository.findById(storeId, attempt.getOrderId()));
        }
        if (linked && attempt.getEmailClaimedAt() == null) {
            sendEmail(storeId, receiptKey);
        }
    }

    /**
     * A stale read here (e.g. an eventually-consistent {@code findById} right after an earlier attach already
     * persisted the document) is not a risk of a duplicate: {@code Order} carries
     * {@code @DynamoDBVersionAttribute}, so saving an entity built from an older version conflicts with
     * {@link com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException} — nothing on the save path
     * (plain {@code ordersRepository.save} for a cancelled order, {@code OrderLifecycle.update} otherwise) catches
     * or clobbers that — and {@link OptimisticLockingExecutor#modifyAndSave} retries the whole load-mutate-save on
     * a fresh read. That retry lands on an order that already has the document the conflicting write persisted, so
     * the dedup check below finds it and does not add a second one.
     */
    private Order attachDocument(ReceiptAttempt attempt) {
        String key = attempt.getReceiptKey();
        LocalDate issuedAt = attempt.getFiscalisedAt() != null
                ? LocalDate.ofInstant(attempt.getFiscalisedAt(), WARSAW) : LocalDate.now(clock.withZone(WARSAW));
        String number = attempt.getReceiptNumber() != null ? attempt.getReceiptNumber() : key;
        Order[] saved = new Order[1];
        optimisticLockingExecutor.modifyAndSave(
                () -> ordersRepository.findById(attempt.getStoreId(), attempt.getOrderId()),
                order -> {
                    Optional<Document> existing = order.getDocuments().stream()
                            .filter(d -> d.getType() == DocumentType.Receipt && key.equals(d.getId()))
                            .findFirst();
                    if (existing.isEmpty()) {
                        order.addDocument(new Document(key, number, attempt.getDocumentUrl(), DocumentType.Receipt, issuedAt));
                    } else if (existing.get().getLink() == null && attempt.getDocumentUrl() != null) {
                        existing.get().setLink(attempt.getDocumentUrl());
                    }
                    saved[0] = order;
                },
                order -> attemptService.saveThroughLifecycle(order));
        return saved[0];
    }

    private void notifyMarketplace(String storeId, String receiptKey, Order order) {
        boolean claimed = attempts.updateWritten(storeId, receiptKey, a -> {
            if (a.getMarketplaceNotifiedAt() != null) {
                return false;
            }
            a.setMarketplaceNotifiedAt(clock.instant());
            return true;
        }).isPresent();
        if (claimed && order != null) {
            lifecycleEventPublisher.publish(order, OrderLifecycleEventType.InvoiceCreated);
        }
    }

    /**
     * The store not sending this e-mail type, or the buyer never giving an address, is not a failure: there is
     * nothing to retry and nothing for the operator to fix, unlike a real send error. Depends only on the request
     * snapshot, which the claim never changes, so it answers the same for the claimed attempt as inside the claim.
     */
    private static boolean skipsEmail(Store store, ReceiptAttempt attempt) {
        return ReceiptSnapshotJson.read(attempt.getRequestSnapshot()).buyerEmail() == null
                || store == null || !store.supportsNotification(EmailNotificationType.ORDER_RECEIPT);
    }

    private void sendEmail(String storeId, String receiptKey) {
        // Loaded once, outside the claim predicate: a store lookup is not part of the attempt's own state and must
        // not be repeated on every predicate retry.
        Store store = storesRepository.findById(storeId);
        Optional<ReceiptAttempt> claimed = attempts.updateWritten(storeId, receiptKey, a -> {
            if (a.getEmailClaimedAt() != null || a.getDocumentUrl() == null) {
                return false;
            }
            Instant now = clock.instant();
            a.setEmailClaimedAt(now);
            if (skipsEmail(store, a)) {
                a.setEmailSkippedAt(now);
            }
            return true;
        });
        if (claimed.isEmpty() || skipsEmail(store, claimed.get())) {
            return;
        }
        ReceiptAttempt attempt = claimed.get();
        String email = ReceiptSnapshotJson.read(attempt.getRequestSnapshot()).buyerEmail();
        Order order = ordersRepository.findById(storeId, attempt.getOrderId());
        boolean sent;
        try {
            sent = email != null && emailClient.send(storeId, EmailNotificationType.ORDER_RECEIPT,
                    new ReceiptEmailNotification(email, recipientName(order), attempt.getOrderId(), attempt.getDocumentUrl()));
        } catch (RuntimeException e) {
            log.error("E-receipt e-mail for {} failed", receiptKey, e);
            sent = false;
        }
        Instant now = clock.instant();
        boolean delivered = sent;
        attempts.update(storeId, receiptKey, a -> {
            if (delivered) {
                // Belt and braces against a resendEmail that raced this in-flight send and cleared the claim (the
                // lease guard in ReceiptAttemptService.resendEmail is meant to prevent that, but this write must
                // never regress an already-recorded send, and must re-claim if the claim was cleared under it):
                // pending() only stops seeing this attempt as needing the e-mail step once both are set again.
                if (a.getEmailSentAt() == null) {
                    a.setEmailSentAt(now);
                }
                if (a.getEmailClaimedAt() == null) {
                    a.setEmailClaimedAt(now);
                }
            } else {
                a.setLastError("E-receipt e-mail not sent (e-mail type off, no template or no address)");
                a.setLastErrorAt(now);
            }
            return true;
        });
        if (delivered) {
            // The rest of the app records OrderEvents in the server's local time (LocalDateTime.now()); the
            // clock here is UTC, so the instant is converted through the system default zone rather than the
            // clock's own zone to keep this event's timestamp consistent with every other OrderEvent.
            orderEventsRepository.save(new OrderEvent(attempt.getOrderId(), EventType.email,
                    EmailNotificationType.ORDER_RECEIPT.name(),
                    LocalDateTime.ofInstant(now, ZoneId.systemDefault())));
        }
    }

    private static String recipientName(Order order) {
        BillingDetails billing = order == null ? null : order.getBillingDetails();
        if (billing == null) {
            return null;
        }
        String name = ((billing.getName() == null ? "" : billing.getName()) + " "
                + (billing.getSurname() == null ? "" : billing.getSurname())).strip();
        return name.isEmpty() ? null : name;
    }
}
