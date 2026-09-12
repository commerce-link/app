package pl.commercelink.web.dtos;

import pl.commercelink.baskets.OfferItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record OfferTableRow(Kind kind, OfferItem item, String groupId, int groupSize) {

    public enum Kind {
        ITEM,
        GROUP_HEAD,
        GROUP_FOOT
    }

    public static List<OfferTableRow> from(List<OfferItem> offerItems) {
        List<OfferTableRow> rows = new ArrayList<>();
        String currentGroup = null;
        for (int i = 0; i < offerItems.size(); i++) {
            OfferItem item = offerItems.get(i);
            String groupId = item.getVariantGroupId();
            if (!Objects.equals(groupId, currentGroup)) {
                if (currentGroup != null) {
                    rows.add(new OfferTableRow(Kind.GROUP_FOOT, null, currentGroup, 0));
                }
                if (groupId != null) {
                    rows.add(new OfferTableRow(Kind.GROUP_HEAD, null, groupId, runLength(offerItems, i, groupId)));
                }
                currentGroup = groupId;
            }
            rows.add(new OfferTableRow(Kind.ITEM, item, groupId, 0));
        }
        if (currentGroup != null) {
            rows.add(new OfferTableRow(Kind.GROUP_FOOT, null, currentGroup, 0));
        }
        return rows;
    }

    private static int runLength(List<OfferItem> offerItems, int start, String groupId) {
        int length = 0;
        while (start + length < offerItems.size() && groupId.equals(offerItems.get(start + length).getVariantGroupId())) {
            length++;
        }
        return length;
    }

    public boolean isItem() {
        return kind == Kind.ITEM;
    }

    public boolean isGroupHead() {
        return kind == Kind.GROUP_HEAD;
    }

    public boolean isGroupFoot() {
        return kind == Kind.GROUP_FOOT;
    }
}
