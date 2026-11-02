package pl.commercelink.marketplace.links;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Links an offer published on a marketplace by another system (identified by the seller's own offer key, e.g. the
 * Empik {@code offer_sku}) to a CommerceLink product.
 */
@DynamoDBTable(tableName = MarketplaceOfferLink.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceOfferLink {

    public static final String TABLE_NAME = "MarketplaceOfferLinks";
    public static final String PIM_ID_INDEX = "StoreIdPimIdIndex";

    @DynamoDBHashKey(attributeName = "storeId")
    @DynamoDBIndexHashKey(globalSecondaryIndexName = PIM_ID_INDEX, attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "linkKey")
    private String linkKey;
    @DynamoDBAttribute(attributeName = "marketplace")
    private String marketplace;
    @DynamoDBAttribute(attributeName = "sellerOfferKey")
    private String sellerOfferKey;
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = PIM_ID_INDEX, attributeName = "pimId")
    private String pimId;
    @DynamoDBAttribute(attributeName = "source")
    @DynamoDBTypeConvertedEnum
    private MarketplaceOfferLinkSource source;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = MarketplaceOfferLinkInstantConverter.class)
    private Instant createdAt;
    @DynamoDBAttribute(attributeName = "createdBy")
    private String createdBy;

    public static MarketplaceOfferLink of(String storeId, String marketplace, String sellerOfferKey, String pimId,
                                          MarketplaceOfferLinkSource source, String createdBy, Instant now) {
        MarketplaceOfferLink link = new MarketplaceOfferLink();
        link.storeId = storeId;
        link.linkKey = linkKey(marketplace, sellerOfferKey);
        link.marketplace = marketplace;
        link.sellerOfferKey = sellerOfferKey;
        link.pimId = pimId;
        link.source = source;
        link.createdBy = createdBy;
        link.createdAt = now;
        return link;
    }

    /** Not normalised: the offer key comes from the marketplace and is stable, so it is matched verbatim. */
    public static String linkKey(String marketplace, String sellerOfferKey) {
        return marketplace + "#" + sellerOfferKey;
    }
}
