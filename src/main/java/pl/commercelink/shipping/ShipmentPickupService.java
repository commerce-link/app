package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.shipping.api.CommandStatus;
import pl.commercelink.shipping.api.PickupOrder;
import pl.commercelink.shipping.api.PickupWindow;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * "Zamów odbiór": which packages wait, which windows the carrier offers, and ordering one pickup for many packages.
 * The packages are marked PENDING on their owners before the command is sent, so a second request finds them taken;
 * the result is settled by {@link ShipmentPickupChecker} from shipment-pickup-queue. Whatever goes wrong after the
 * packages were marked, they never stay PENDING without a check that will settle them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentPickupService {

    static final String UNCONFIRMED_KEY = ShipmentPickup.UNCONFIRMED_KEY;
    static final String NOT_SENT_KEY = "shipping.pickup.not.sent";

    private final PickupCandidates candidates;
    private final ShipmentOwners owners;
    private final ShippingService shippingService;
    private final ShipmentPickupEventPublisher publisher;
    private final MessageSource messageSource;

    /**
     * The store's packages that can be ordered, by integration, carrier and pickup address. A package whose pickup is
     * being ordered is not listed until its command fails.
     */
    public List<PickupGroup> groups(String storeId) {
        Map<String, List<PickupCandidate>> byKey = new TreeMap<>();
        for (PickupCandidate candidate : candidates.of(storeId)) {
            byKey.computeIfAbsent(candidate.groupKey(), k -> new ArrayList<>()).add(candidate);
        }
        return byKey.entrySet().stream()
                .map(e -> {
                    PickupCandidate first = e.getValue().get(0);
                    return new PickupGroup(e.getKey(), first.provider(), first.carrier(), first.pickUpAddressId(),
                            List.copyOf(e.getValue()));
                })
                .sorted(Comparator.comparing(PickupGroup::carrier, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    /** Windows common to the packages, from today on; daysAhead differs between the page and the immediate pickup. */
    public List<PickupWindow> windows(Store store, String provider, List<String> externalIds, int daysAhead) {
        ShippingProvider shippingProvider = providerFor(store, provider);
        if (shippingProvider == null || !shippingProvider.supportsPickups() || externalIds.isEmpty()) {
            return List.of();
        }
        return shippingProvider.pickupWindows(externalIds, LocalDate.now(), daysAhead);
    }

    /**
     * Windows for the page. One package the carrier refuses (e.g. its pickup was booked in the provider's panel, or it
     * was handed in at a point) makes the provider refuse the whole request, which would leave every package of the
     * group without a courier: on a refusal each package is asked about alone, the refused ones are named with the
     * provider's reason and the windows are those of the rest. An error that is not a refusal is thrown as before.
     */
    public PageWindows pageWindows(Store store, String provider, List<String> externalIds, int daysAhead) {
        try {
            return new PageWindows(windows(store, provider, externalIds, daysAhead), Map.of());
        } catch (RuntimeException e) {
            if (!ProviderErrors.isRefusal(e)) {
                throw e;
            }
            Map<String, String> refused = new LinkedHashMap<>();
            for (String externalId : externalIds) {
                try {
                    windows(store, provider, List.of(externalId), daysAhead);
                } catch (RuntimeException single) {
                    if (!ProviderErrors.isRefusal(single)) {
                        throw single;
                    }
                    refused.put(externalId, ProviderErrors.describe(single));
                }
            }
            if (refused.isEmpty()) {
                throw e;
            }
            List<String> rest = externalIds.stream().filter(id -> !refused.containsKey(id)).toList();
            return new PageWindows(windows(store, provider, rest, daysAhead), refused);
        }
    }

    /** The windows of the packages the carrier accepts, and the refused packages with the carrier's reason. */
    public record PageWindows(List<PickupWindow> windows, Map<String, String> refused) {
    }

    /**
     * The operator gave the package to the carrier without "Zamów odbiór": it no longer waits for a courier and leaves
     * the pickup list. False when it is not among the store's packages waiting for a courier any more.
     */
    public boolean handOver(String storeId, String externalId) {
        PickupCandidate candidate = candidates.of(storeId).stream()
                .filter(c -> c.externalId().equals(externalId))
                .findFirst().orElse(null);
        if (candidate == null) {
            return false;
        }
        return owners.get(candidate.ownerType()).applyPickup(storeId, candidate.ownerId(), List.of(externalId),
                p -> p.isAwaiting() ? ShipmentPickup.handedOver() : p) > 0;
    }

    public PickupStart order(Store store, String provider, List<PickupTarget> targets, PickupWindow window) {
        ShippingProvider shippingProvider = providerFor(store, provider);
        if (shippingProvider == null) {
            return PickupStart.gone();
        }
        String storeId = store.getStoreId();
        String commandId = UUID.randomUUID().toString();
        List<PickupTarget> marked = markPending(storeId, targets.stream().distinct().toList(), commandId, window);
        if (marked.isEmpty()) {
            return PickupStart.gone();
        }
        ShipmentPickupCheckRequest check = ShipmentPickupCheckRequest.of(storeId, provider, commandId, marked, window);
        List<String> externalIds = marked.stream().map(PickupTarget::externalId).distinct().toList();
        PickupOrder result;
        try {
            result = shippingProvider.orderPickup(externalIds, window, commandId);
        } catch (RuntimeException e) {
            if (ProviderErrors.isRefusal(e)) {
                return refused(check, ProviderErrors.describe(e));
            }
            log.warn("Pickup command {} in store {} for packages {} has an unknown outcome; it stays PENDING and is "
                    + "checked", commandId, storeId, externalIds, e);
            return publishCheck(check);
        }
        if (result != null && result.status() == CommandStatus.FAILED) {
            return refused(check, result.error());
        }
        return publishCheck(check);
    }

    /**
     * Marked before the command is sent, like a cancellation: a second request finds them pending. A mark that breaks
     * off sends nothing, and the packages it already marked are failed, so they can be ordered again.
     */
    private List<PickupTarget> markPending(String storeId, List<PickupTarget> targets, String commandId,
                                           PickupWindow window) {
        LocalDateTime now = LocalDateTime.now();
        UnaryOperator<ShipmentPickup> mark = p -> p.isAwaiting()
                ? ShipmentPickup.pending(commandId, now, window.date(), window.from(), window.to()) : p;
        List<PickupTarget> marked = new ArrayList<>();
        try {
            for (PickupTarget target : targets) {
                ShipmentOwner owner = owners.get(target.ownerType());
                if (owner.applyPickup(storeId, target.ownerId(), List.of(target.externalId()), mark) > 0) {
                    marked.add(target);
                }
            }
        } catch (RuntimeException e) {
            log.error("Pickup command {} in store {} was not sent: marking its packages {} pending broke off",
                    commandId, storeId, targets.stream().map(PickupTarget::externalId).toList(), e);
            settle(storeId, targets, commandId, p -> p.failedWithKey(NOT_SENT_KEY));
            throw e;
        }
        return marked;
    }

    private PickupStart refused(ShipmentPickupCheckRequest check, String reason) {
        if (!settle(check.getStoreId(), check.getTargets(), check.getCommandId(), p -> p.failed(reason))) {
            // the check asks the provider about the refused command and settles what could not be marked failed here
            publishCheck(check);
        }
        return PickupStart.refused(check.getCommandId(), reason);
    }

    /**
     * Without the check message nothing would ever settle the pickups, and they could not be ordered again: they are
     * failed as unconfirmed instead, so the operator checks the provider's panel before ordering again.
     */
    private PickupStart publishCheck(ShipmentPickupCheckRequest check) {
        try {
            publisher.publish(check);
            return PickupStart.started();
        } catch (RuntimeException e) {
            log.error("Check of pickup command {} in store {} for packages {} could not be sent; the courier may be "
                    + "ordered", check.getCommandId(), check.getStoreId(), externalIds(check), e);
            settle(check.getStoreId(), check.getTargets(), check.getCommandId(), p -> p.failedWithKey(UNCONFIRMED_KEY));
            String reason = messageSource.getMessage(UNCONFIRMED_KEY, null, LocaleContextHolder.getLocale());
            return PickupStart.refused(check.getCommandId(), reason);
        }
    }

    /** Applies the change to the packages still pending for the command; false when an owner could not be written. */
    private boolean settle(String storeId, List<PickupTarget> targets, String commandId,
                           UnaryOperator<ShipmentPickup> change) {
        boolean settled = true;
        for (PickupTarget target : targets) {
            try {
                owners.get(target.ownerType()).applyPickup(storeId, target.ownerId(), List.of(target.externalId()),
                        p -> p.isPendingFor(commandId) ? change.apply(p) : p);
            } catch (RuntimeException e) {
                settled = false;
                log.error("Pickup of package {} ({} {}) in store {} stays PENDING for command {}: settling it failed",
                        target.externalId(), target.ownerType(), target.ownerId(), storeId, commandId, e);
            }
        }
        return settled;
    }

    private static List<String> externalIds(ShipmentPickupCheckRequest check) {
        return check.getTargets().stream().map(PickupTarget::externalId).toList();
    }

    // the window and the command go to the integration that created the packages, which must be the store's
    private ShippingProvider providerFor(Store store, String provider) {
        if (provider == null || !provider.equals(shippingService.providerName(store))) {
            return null;
        }
        return shippingService.providerFor(store);
    }
}
