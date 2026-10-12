package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.builtin.WarehouseItemPage.*;
import pl.commercelink.web.orders.Money;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Builds the item page (spec §4) with every text resolved; the template prints, never computes. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WarehouseItemPageFactory {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MessageSource messages;
    private final StoreCategories storeCategories;
    private final SupplierLabels supplierLabels;
    private final SupplierRegistry suppliers;
    private final DeliveryRedirectResolver redirects;

    WarehouseItemPage page(WarehouseItem item, String back, boolean admin, WarehouseItemEditForm form,
                           Map<String, String> errors, String errorMessage, Locale locale) {
        FulfilmentStatus status = item.getStatus();
        Mode mode = status == FulfilmentStatus.Destroyed ? Mode.DESTROYED : status == FulfilmentStatus.New ? Mode.NEW : Mode.EDIT;
        ItemBack itemBack = ItemBack.resolve(back, status);
        WarehouseItemEditForm shown = form != null ? form : WarehouseItemEditForm.of(item);
        shown.setBack(ItemBack.safe(back));
        List<StoreCategories.Group> groups = storeCategories.groupsFor(item.getStoreId());
        boolean outside = StringUtils.isNotBlank(item.getCategory())
                && groups.stream().noneMatch(g -> g.names().contains(item.getCategory()));
        return new WarehouseItemPage(item.getItemId(), item.getName(), text(locale, WarehouseStatuses.labelKey(status)),
                WarehouseStatuses.tone(status), StringUtils.trimToNull(item.getEan()), StringUtils.trimToNull(item.getManufacturerCode()),
                StringUtils.left(item.getItemId(), 8), itemBack.href(), text(locale, itemBack.labelKey()), mode,
                mode == Mode.DESTROYED ? destroyedTitle(item, locale) : null,
                mode == Mode.NEW ? List.of() : facts(item, locale),
                mode == Mode.NEW ? null : amount(item.totalUnitCost().netValue(), locale),
                origin(item, admin, mode, locale), shown, errors, errorMessage, groups, outside,
                conditions(locale), mode == Mode.NEW ? statuses(locale) : List.of(),
                mode == Mode.DESTROYED ? condition(item.getCondition(), locale) : null,
                mode == Mode.NEW ? delete(item, back, locale) : null);
    }

    private String destroyedTitle(WarehouseItem item, Locale locale) {
        return item.getDestroyedAt() == null ? text(locale, "warehouse.item.destroyed.title.undated")
                : text(locale, "warehouse.item.destroyed.title", DATE.format(item.getDestroyedAt()));
    }

    private List<Fact> facts(WarehouseItem item, Locale locale) {
        List<Fact> facts = new ArrayList<>(List.of(
                new Fact(text(locale, "warehouse.item.fact.qty.label"), text(locale, "warehouse.item.fact.qty", item.getQty()), true),
                new Fact(text(locale, "warehouse.item.fact.net"), amount(item.unitCost().netValue(), locale), true),
                new Fact(text(locale, "warehouse.item.fact.gross"), amount(item.unitCost().grossValue(), locale), true),
                new Fact(text(locale, "warehouse.item.fact.vat"), text(locale, "warehouse.item.fact.vat.value", Math.round((item.getTax() - 1) * 100)), true)));
        if (item.hasUnitSystemCost()) {
            facts.add(new Fact(text(locale, "warehouse.item.fact.systemCost"),
                    text(locale, "warehouse.item.fact.systemCost.value", Money.format(item.systemCost().netValue())), true));
        }
        return facts;
    }

    private Origin origin(WarehouseItem item, boolean admin, Mode mode, Locale locale) {
        SupplierLabelMap labels = supplierLabels.forStoreId(item.getStoreId());
        WarehouseItemRow row = new WarehouseRowMapper(messages, locale, redirects, labels::of, labels::has,
                id -> labels.has(id) || suppliers.exists(id), admin).map(item);
        String documents = mode != Mode.NEW && row.hasDelivery() && StringUtils.isNotBlank(item.getManufacturerCode())
                ? "/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=" + encode(item.getDeliveryId())
                  + "&mfn=" + encode(item.getManufacturerCode()) + "&from=item&itemId=" + encode(item.getItemId())
                : null;
        List<Link> serials = Arrays.stream(StringUtils.defaultString(item.getSerialNo()).split(","))
                .map(String::trim).filter(StringUtils::isNotEmpty)
                .map(s -> new Link(s, "/dashboard/item/history?serialNo=" + encode(s))).toList();
        return new Origin(row.deliveryHref(), row.deliveryNumber(), row.supplier(), documents, serials);
    }

    private List<Choice> conditions(Locale locale) {
        return Arrays.stream(ItemCondition.values()).map(c -> condition(c, locale)).toList();
    }

    private Choice condition(ItemCondition condition, Locale locale) {
        ItemCondition c = condition == null ? ItemCondition.Sealed : condition;
        String tone = switch (c) { case Damaged -> "is-bad"; case OpenBox -> "is-warn"; default -> "is-ok"; };
        return new Choice(c.name(), text(locale, "ItemCondition." + c.name()), null, tone);
    }

    private List<Choice> statuses(Locale locale) {
        return WarehouseItemController.NEW_ITEM_STATUSES.stream()
                .map(s -> new Choice(s.name(), text(locale, WarehouseStatuses.labelKey(s)),
                        text(locale, "warehouse.item.new.status." + s.name() + ".desc"), null)).toList();
    }

    private Delete delete(WarehouseItem item, String back, Locale locale) {
        String safe = ItemBack.safe(back);
        String href = "/dashboard/warehouse/items/" + item.getItemId() + "/delete" + (safe == null ? "" : "?back=" + encode(safe));
        return new Delete(href, text(locale, "warehouse.item.delete.title", item.getName()),
                text(locale, "warehouse.item.delete.message"), text(locale, "warehouse.item.delete.action"));
    }

    private String amount(double value, Locale locale) {
        return text(locale, "general.currency.amount", Money.format(value));
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
