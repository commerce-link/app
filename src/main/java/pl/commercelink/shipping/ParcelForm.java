package pl.commercelink.shipping;

public class ParcelForm {

    private int width;
    private int depth;
    private int height;
    private int weight;
    private int value;
    private String description;
    private String type;

    public static ParcelForm empty() {
        ParcelForm parcel = new ParcelForm();
        parcel.setWidth(0);
        parcel.setDepth(0);
        parcel.setHeight(0);
        parcel.setWeight(0);
        parcel.setValue(0);
        parcel.setDescription("");
        parcel.setType("package");
        return parcel;
    }

    public ParcelForm() {
    }

    public ParcelForm(int width, int depth, int height, int weight, int value, String description, String type) {
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.weight = weight;
        this.value = value;
        this.description = description;
        this.type = type;
    }

    public boolean isComplete() {
        return width > 0 && depth > 0 && height > 0 && weight > 0
                && description != null && !description.isEmpty()
                && type != null && !type.isEmpty();
    }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
