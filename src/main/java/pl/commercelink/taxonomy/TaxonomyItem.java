package pl.commercelink.taxonomy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@DynamoDBTable(tableName = TaxonomyItem.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class TaxonomyItem {

    public static final String TABLE_NAME = "Taxonomy";
    public static final String MFN = "mfn";
    public static final String EAN = "ean";
    public static final String BRAND = "brand";
    public static final String NAME = "name";
    public static final String CATEGORY = "category";
    public static final String CATEGORY_ID = "categoryId";
    public static final String DATA_ACCURACY_SCORE = "dataAccuracyScore";
    public static final String NET_WEIGHT_IN_GRAMS = "netWeightInGrams";
    public static final String GROSS_WEIGHT_IN_GRAMS = "grossWeightInGrams";
    public static final String RAW_CATEGORY = "rawCategory";

    @DynamoDBHashKey(attributeName = MFN)
    private String mfn;

    @DynamoDBAttribute(attributeName = EAN)
    private String ean;

    @DynamoDBAttribute(attributeName = BRAND)
    private String brand;

    @DynamoDBAttribute(attributeName = NAME)
    private String name;

    @DynamoDBAttribute(attributeName = CATEGORY)
    private String category;

    @DynamoDBAttribute(attributeName = CATEGORY_ID)
    private String categoryId;

    @DynamoDBAttribute(attributeName = DATA_ACCURACY_SCORE)
    private Integer dataAccuracyScore;

    @DynamoDBAttribute(attributeName = NET_WEIGHT_IN_GRAMS)
    private Integer netWeightInGrams;

    @DynamoDBAttribute(attributeName = GROSS_WEIGHT_IN_GRAMS)
    private Integer grossWeightInGrams;

    @DynamoDBAttribute(attributeName = RAW_CATEGORY)
    private String rawCategory;

    public static TaxonomyItem from(Taxonomy taxonomy) {
        TaxonomyItem item = new TaxonomyItem();
        item.mfn = taxonomy.mfn();
        item.ean = taxonomy.ean();
        item.brand = taxonomy.brand();
        item.name = taxonomy.name();
        item.category = taxonomy.category();
        item.categoryId = taxonomy.categoryId();
        item.dataAccuracyScore = taxonomy.dataAccuracyScore();
        item.netWeightInGrams = taxonomy.netWeightInGrams();
        item.grossWeightInGrams = taxonomy.grossWeightInGrams();
        item.rawCategory = taxonomy.rawCategory();
        return item;
    }

    public Taxonomy toTaxonomy() {
        return new Taxonomy(ean, mfn, brand, name, category,
                dataAccuracyScore == null ? Integer.MAX_VALUE : dataAccuracyScore,
                netWeightInGrams, grossWeightInGrams, rawCategory, categoryId);
    }
}
