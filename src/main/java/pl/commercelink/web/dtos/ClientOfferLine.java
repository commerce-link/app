package pl.commercelink.web.dtos;

import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ClientOfferLine(String groupId, BasketItem item, List<Option> options) {

    public record Option(BasketItem item, boolean selected, double priceDelta) {

        public boolean isIncluded() {
            return priceDelta == 0;
        }

        public double absPriceDelta() {
            return Math.abs(priceDelta);
        }
    }

    public static List<ClientOfferLine> from(Basket basket) {
        List<ClientOfferLine> lines = new ArrayList<>();
        Set<String> seenGroups = new HashSet<>();
        for (BasketItem item : basket.getBasketItems()) {
            if (!item.isInVariantGroup()) {
                lines.add(new ClientOfferLine(null, item, List.of()));
            } else if (seenGroups.add(item.getVariantGroupId())) {
                lines.add(groupLine(basket, item.getVariantGroupId()));
            }
        }
        return lines;
    }

    private static ClientOfferLine groupLine(Basket basket, String groupId) {
        List<BasketItem> group = basket.getVariantGroup(groupId);
        BasketItem chosen = basket.getEffectiveBasketItems().stream()
                .filter(i -> i.isInVariantGroup(groupId))
                .findFirst()
                .orElse(group.get(0));
        List<Option> options = group.stream()
                .map(i -> new Option(i, i == chosen, i.getTotalPrice() - chosen.getTotalPrice()))
                .toList();
        return new ClientOfferLine(groupId, chosen, options);
    }

    public boolean isGroup() {
        return groupId != null && options.size() > 1;
    }

    public String getLabel() {
        return isGroup() ? groupId : item.getCategory();
    }
}
