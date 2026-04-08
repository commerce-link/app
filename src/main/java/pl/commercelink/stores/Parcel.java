package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class Parcel {
    @DynamoDBAttribute(attributeName = "width")
    private int width;
    @DynamoDBAttribute(attributeName = "depth")
    private int depth;
    @DynamoDBAttribute(attributeName = "height")
    private int height;
    @DynamoDBAttribute(attributeName = "weight")
    private int weight;
    @DynamoDBAttribute(attributeName = "value")
    private int value;
    @DynamoDBAttribute(attributeName = "description")
    private String description;

    public Parcel() {
    }

    public Parcel(int width, int depth, int height, int weight, int value, String description) {
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.weight = weight;
        this.value = value;
        this.description = description;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return width > 0 && depth > 0 && height > 0 && weight > 0 && value > 0 && isNotBlank(description);
    }

    @DynamoDBIgnore
    public static Parcel empty() {
        Parcel parcel = new Parcel();
        parcel.setWidth(0);
        parcel.setDepth(0);
        parcel.setHeight(0);
        parcel.setWeight(0);
        parcel.setValue(0);
        parcel.setDescription("");
        return parcel;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
