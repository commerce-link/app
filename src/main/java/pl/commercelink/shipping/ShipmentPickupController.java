package pl.commercelink.shipping;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.api.PickupWindow;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * "Zamów odbiór": one courier for the chosen packages of one carrier at one pickup address. Opened from the orders list
 * (every package of the store waiting for a courier) and from an RMA page. A page, not a dialog: the windows come from the
 * provider, and a dialog rendered with a list or a record would ask for them on every visit.
 */
@Slf4j
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ShipmentPickupController {

    static final String PAGE = "/dashboard/shipping/pickups/new";
    static final int DAYS_AHEAD = 4;
    private static final String DEFAULT_BACK = "/dashboard/orders";
    private static final String FORM_ERROR = "pickupError";
    // the address comes from the request and ends up in a redirect: only a plain path of the dashboard is followed
    private static final Pattern SAFE_BACK = Pattern.compile("/dashboard/[A-Za-z0-9_\\-./?=&%+]*");
    private static final Pattern OWNER_PAGE = Pattern.compile("/dashboard/(orders|rma)/([A-Za-z0-9\\-]+)(?:[/?].*)?");

    private final ShipmentPickupService pickupService;
    private final StoresRepository storesRepository;
    private final ShippingProviderFactory providerFactory;
    private final MessageSource messageSource;

    @GetMapping(PAGE)
    public String page(@RequestParam(required = false) String group, @RequestParam(required = false) String back,
                       Model model, Locale locale) {
        Store store = storesRepository.findById(storeId());
        List<PickupGroup> groups = pickupService.groups(storeId());
        PickupGroup selected = groups.stream().filter(g -> g.key().equals(group)).findFirst()
                .orElse(groups.isEmpty() ? null : groups.get(0));
        String safeBack = safeBack(back);

        List<ShipmentPickupPage.WindowOption> windows = List.of();
        Map<String, String> refused = Map.of();
        String windowsError = null;
        if (selected != null) {
            try {
                ShipmentPickupService.PageWindows pageWindows =
                        pickupService.pageWindows(store, selected.provider(), selected.pickUpAddressId(),
                                externalIds(selected), DAYS_AHEAD);
                windows = pageWindows.windows().stream().map(w -> windowOption(w, locale)).toList();
                refused = pageWindows.refused();
            } catch (ShippingUnavailableException e) {
                windowsError = messageSource.getMessage("shipping.error.no.provider", null, locale);
            } catch (RuntimeException e) {
                log.warn("Pickup windows of {} in store {} could not be read", selected.key(), storeId(), e);
                windowsError = ProviderErrors.describe(e);
            }
        }

        model.addAttribute("pickupPage", new ShipmentPickupPage(
                groups.stream().map(g -> groupOption(g, g == selected, store, locale)).toList(),
                selected == null ? null : selected.key(),
                selected == null ? List.of() : packageRows(selected, refused, safeBack, locale),
                selected == null ? null : addressLine(store, selected.pickUpAddressId(), locale),
                windows, windowsError, (String) model.getAttribute(FORM_ERROR), safeBack));
        return "shipping-pickup";
    }

    @PostMapping("/dashboard/shipping/pickups")
    public String order(@RequestParam String group,
                        @RequestParam(name = "externalIds", required = false) List<String> externalIds,
                        @RequestParam(required = false) String window, @RequestParam(required = false) String back,
                        RedirectAttributes redirectAttributes, Locale locale) {
        String safeBack = safeBack(back);
        if (externalIds == null || externalIds.isEmpty()) {
            return backToPage(group, safeBack, "shipping.pickup.none.selected", redirectAttributes, locale);
        }
        Optional<PickupWindow> chosenWindow = parseWindow(window);
        if (chosenWindow.isEmpty()) {
            return backToPage(group, safeBack, "shipping.pickup.window.none.selected", redirectAttributes, locale);
        }
        Optional<PickupGroup> chosen = pickupService.groups(storeId()).stream()
                .filter(g -> g.key().equals(group))
                .findFirst();
        // only packages that still wait in this group: the form may be stale, or carry ids from elsewhere
        List<PickupTarget> targets = chosen.map(g -> g.entries().stream()
                        .filter(c -> externalIds.contains(c.externalId()))
                        .map(PickupCandidate::target)
                        .toList())
                .orElse(List.of());
        if (targets.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", message("shipping.pickup.gone", locale));
            return "redirect:" + safeBack;
        }

        PickupStart start;
        try {
            start = pickupService.order(storesRepository.findById(storeId()), chosen.get().provider(),
                    chosen.get().pickUpAddressId(), targets, chosenWindow.get());
        } catch (ShippingUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage", message("shipping.pickup.no.provider", locale));
            return "redirect:" + safeBack;
        }
        switch (start.outcome()) {
            case STARTED -> redirectAttributes.addFlashAttribute("successMessage",
                    message(startedKey(chosen.get(), targets, safeBack), locale));
            case REFUSED -> redirectAttributes.addFlashAttribute("errorMessage", start.error());
            case GONE -> redirectAttributes.addFlashAttribute("errorMessage", message("shipping.pickup.gone", locale));
        }
        return "redirect:" + safeBack;
    }

    private String storeId() {
        return CustomSecurityContext.getStoreId();
    }

    static String safeBack(String back) {
        return back != null && SAFE_BACK.matcher(back).matches() && !back.contains("//") && !back.contains("..")
                ? back : DEFAULT_BACK;
    }

    // date|from|to|token, as the page renders the window's value
    static Optional<PickupWindow> parseWindow(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", 4);
        if (parts.length < 3) {
            return Optional.empty();
        }
        try {
            String token = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
            return Optional.of(new PickupWindow(LocalDate.parse(parts[0]), LocalTime.parse(parts[1]),
                    LocalTime.parse(parts[2]), token));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private String backToPage(String group, String back, String errorKey, RedirectAttributes redirectAttributes,
                              Locale locale) {
        redirectAttributes.addFlashAttribute(FORM_ERROR, message(errorKey, locale));
        return redirect(PAGE, group, back);
    }

    // encoded as variables: a back address of the orders list carries its own "&" and "="
    private static String redirect(String page, String group, String back) {
        return "redirect:" + UriComponentsBuilder.fromPath(page)
                .queryParam("group", "{group}")
                .queryParam("back", "{back}")
                .encode()
                .buildAndExpand(group, back)
                .toUriString();
    }

    private List<ShipmentPickupPage.PackageRow> packageRows(PickupGroup group, Map<String, String> refused, String back,
                                                            Locale locale) {
        return group.entries().stream().map(c -> packageRow(c, refused.get(c.externalId()), back, locale)).toList();
    }

    private ShipmentPickupPage.GroupOption groupOption(PickupGroup group, boolean selected, Store store, Locale locale) {
        ShippingProviderDescriptor descriptor = providerFactory.getDescriptor(group.provider());
        String integration = descriptor != null ? descriptor.displayName() : group.provider();
        String address = pickUpAddress(store, group.pickUpAddressId())
                .map(ShippingDetails::getDisplayName)
                .orElseGet(() -> message("shipping.pickup.place.unknown", locale));
        String label = messageSource.getMessage("shipping.pickup.group",
                new Object[]{carrierName(group.carrier()), integration, address, group.entries().size()}, locale);
        return new ShipmentPickupPage.GroupOption(group.key(), label, selected);
    }

    /** Allegro's own network (ALLEGRO_ONE_KURIER, ...) reads "One by Allegro" like on the shipments card; others as they come. */
    private static String carrierName(String carrier) {
        String name = AllegroCarrierNames.displayName(carrier);
        return name != null ? name : carrier;
    }

    private ShipmentPickupPage.PackageRow packageRow(PickupCandidate candidate, String refusal, String back,
                                                     Locale locale) {
        String label = ConversionUtil.getShortenedId(candidate.ownerId()) + " · " + candidate.trackingNo();
        String refusalLine = refusal == null ? null
                : messageSource.getMessage("shipping.pickup.package.refused", new Object[]{refusal}, locale);
        return new ShipmentPickupPage.PackageRow(candidate.externalId(), label, ownerMarker(candidate, back, locale),
                refusalLine);
    }

    // the package of the order or RMA the operator came from reads "to zamówienie" / "to zgłoszenie"
    private String ownerMarker(PickupCandidate candidate, String back, Locale locale) {
        if (!isOfBackPage(candidate, back)) {
            return null;
        }
        return message(candidate.ownerType() == ShipmentOwnerType.ORDER
                ? "shipping.pickup.this.order" : "shipping.pickup.this.rma", locale);
    }

    /**
     * The page the operator returns to shows its own packages: when it has some waiting in this group and none of them
     * went into the command (refused by the carrier, or unticked), "Zamawiamy odbiór" alone would read as if they did.
     */
    private static String startedKey(PickupGroup group, List<PickupTarget> targets, String back) {
        List<PickupCandidate> own = group.entries().stream().filter(c -> isOfBackPage(c, back)).toList();
        boolean leftOut = !own.isEmpty() && own.stream()
                .noneMatch(c -> targets.stream().anyMatch(t -> t.externalId().equals(c.externalId())));
        if (!leftOut) {
            return "shipping.pickup.started";
        }
        return own.get(0).ownerType() == ShipmentOwnerType.ORDER
                ? "shipping.pickup.started.without.this.order" : "shipping.pickup.started.without.this.rma";
    }

    private static boolean isOfBackPage(PickupCandidate candidate, String back) {
        var page = OWNER_PAGE.matcher(back);
        if (!page.matches() || !page.group(2).equals(candidate.ownerId())) {
            return false;
        }
        ShipmentOwnerType pageType = "orders".equals(page.group(1)) ? ShipmentOwnerType.ORDER : ShipmentOwnerType.RMA;
        return candidate.ownerType() == pageType;
    }

    private String addressLine(Store store, String pickUpAddressId, Locale locale) {
        return pickUpAddress(store, pickUpAddressId)
                .map(a -> a.getDisplayName() + ", " + a.getStreetAndNumber() + ", " + a.getPostalCode() + " " + a.getCity())
                .orElseGet(() -> message("shipping.pickup.place.missing", locale));
    }

    private static Optional<ShippingDetails> pickUpAddress(Store store, String pickUpAddressId) {
        return store.getPickUpAddresses().stream()
                .filter(a -> Objects.equals(a.getId(), pickUpAddressId))
                .findFirst();
    }

    private static ShipmentPickupPage.WindowOption windowOption(PickupWindow window, Locale locale) {
        String value = window.date() + "|" + window.from() + "|" + window.to() + "|"
                + (window.token() == null ? "" : window.token());
        DateTimeFormatter hours = DateTimeFormatter.ofPattern("H:mm", locale);
        String label = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(window.date()) + ", "
                + hours.format(window.from()) + "–" + hours.format(window.to());
        return new ShipmentPickupPage.WindowOption(value, label);
    }

    private static List<String> externalIds(PickupGroup group) {
        return group.entries().stream().map(PickupCandidate::externalId).toList();
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}
