package pl.commercelink.marketplace.links;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Links an offer published on a marketplace by another system (identified by the seller's own offer key, e.g. the
 * Empik {@code offer_sku}) to the EANs of the product it stands for (several when the marketplace sells one offer
 * for a set of equivalent EANs).
 */
@DynamoDBTable(tableName = MarketplaceOfferLink.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceOfferLink {

    public static final String TABLE_NAME = "MarketplaceOfferLinks";

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "linkKey")
    private String linkKey;
    @DynamoDBAttribute(attributeName = "marketplace")
    private String marketplace;
    @DynamoDBAttribute(attributeName = "sellerOfferKey")
    private String sellerOfferKey;
    @DynamoDBAttribute(attributeName = "eans")
    private List<String> eans;
    @DynamoDBAttribute(attributeName = "source")
    @DynamoDBTypeConvertedEnum
    private MarketplaceOfferLinkSource source;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = MarketplaceOfferLinkInstantConverter.class)
    private Instant createdAt;
    @DynamoDBAttribute(attributeName = "createdBy")
    private String createdBy;

    public static MarketplaceOfferLink of(String storeId, String marketplace, String sellerOfferKey,
                                          Collection<String> eans, MarketplaceOfferLinkSource source,
                                          String createdBy, Instant now) {
        MarketplaceOfferLink link = new MarketplaceOfferLink();
        link.storeId = storeId;
        link.linkKey = linkKey(marketplace, sellerOfferKey);
        link.marketplace = marketplace;
        link.sellerOfferKey = sellerOfferKey;
        link.eans = unifiedDistinct(eans);
        link.source = source;
        link.createdBy = createdBy;
        link.createdAt = now;
        return link;
    }

    private static List<String> unifiedDistinct(Collection<String> eans) {
        LinkedHashSet<String> unified = new LinkedHashSet<>();
        if (eans != null) {
            for (String ean : eans) {
                String value = OfferEans.unify(ean);
                if (value != null) {
                    unified.add(value);
                }
            }
        }
        return new ArrayList<>(unified);
    }

    /** Not normalised: the offer key comes from the marketplace and is stable, so it is matched verbatim. */
    public static String linkKey(String marketplace, String sellerOfferKey) {
        return marketplace + "#" + sellerOfferKey;
    }
}
