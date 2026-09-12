package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@DynamoDBDocument
public class ShippingDetails {

    @DynamoDBAttribute(attributeName = "id")
    private String id;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "surname")
    private String surname;
    @DynamoDBAttribute(attributeName = "companyName")
    private String companyName;
    @DynamoDBAttribute(attributeName = "streetAndNumber")
    private String streetAndNumber;
    @DynamoDBAttribute(attributeName = "postalCode")
    private String postalCode;
    @DynamoDBAttribute(attributeName = "city")
    private String city;
    @DynamoDBAttribute(attributeName = "country")
    private String country;
    @DynamoDBAttribute(attributeName = "email")
    private String email;
    @DynamoDBAttribute(attributeName = "phone")
    private String phone;
    @DynamoDBAttribute(attributeName = "default")
    private boolean _default = true;

    public ShippingDetails() {
    }

    // required by dynamodb
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getStreetAndNumber() {
        return streetAndNumber;
    }

    public void setStreetAndNumber(String streetAndNumber) {
        this.streetAndNumber = streetAndNumber;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean is_default() {
        return _default;
    }

    public void set_default(boolean isDefault) {
        this._default = isDefault;
    }

    @DynamoDBIgnore
    public String getFullName() {
        if (StringUtils.isNotBlank(surname)) {
            return name + " " + surname;
        } else {
            return name;
        }
    }

    @DynamoDBIgnore
    public String getDisplayName() {
        if (StringUtils.isNotBlank(getFullName())) {
            return getFullName();
        }
        return getCompanyName();
    }


    @DynamoDBIgnore
    public boolean isProperlyFilled() {
        return StringUtils.isNotBlank(getStreetAndNumber()) &&
                StringUtils.isNotBlank(getPostalCode()) &&
                StringUtils.isNotBlank(getCity()) &&
                StringUtils.isNotBlank(getCountry()) &&
                StringUtils.isNotBlank(getEmail()) &&
                StringUtils.isNotBlank(getPhone()) &&
                (StringUtils.isNotBlank(getFullName()) || StringUtils.isNotBlank(getCompanyName()));
    }

    @DynamoDBIgnore
    public boolean isCompanyAddress() {
        return StringUtils.isNotBlank(getCompanyName());
    }

    @DynamoDBIgnore
    public static ShippingDetails _default() {
        ShippingDetails shippingDetails = new ShippingDetails();
        shippingDetails.setCountry("PL");
        return shippingDetails;
    }

    @DynamoDBIgnore
    public static ShippingDetails pickUpPoint(ShippingDetails pickUpAddress, String pickUpRecipientName) {
        ShippingDetails shippingDetails = new ShippingDetails();
        shippingDetails.setName(pickUpRecipientName);
        shippingDetails.setStreetAndNumber(pickUpAddress.getStreetAndNumber());
        shippingDetails.setPostalCode(pickUpAddress.getPostalCode());
        shippingDetails.setCity(pickUpAddress.getCity());
        shippingDetails.setCountry(pickUpAddress.getCountry());
        shippingDetails.setEmail(pickUpAddress.getEmail());
        shippingDetails.setPhone(pickUpAddress.getPhone());
        shippingDetails.set_default(true);
        return shippingDetails;
    }

    @DynamoDBIgnore
    public boolean isCountryChange(String requestedCountry) {
        String requested = trimToNull(requestedCountry);
        return requested != null && !StringUtils.equalsIgnoreCase(requested, country);
    }

    @DynamoDBIgnore
    public ShippingDetails withEditableFieldsFrom(ShippingDetails requested) {
        ShippingDetails updated = copy();
        updated.setId(id);
        updated.setName(trimToNull(requested.getName()));
        updated.setSurname(trimToNull(requested.getSurname()));
        updated.setCompanyName(trimToNull(requested.getCompanyName()));
        updated.setStreetAndNumber(trimToNull(requested.getStreetAndNumber()));
        updated.setPostalCode(trimToNull(requested.getPostalCode()));
        updated.setCity(trimToNull(requested.getCity()));
        updated.setEmail(trimToNull(requested.getEmail()));
        updated.setPhone(trimToNull(requested.getPhone()));
        return updated;
    }

    @DynamoDBIgnore
    public boolean isPlainTextWithin(int maxFieldLength) {
        return Stream.of(name, surname, companyName, streetAndNumber, postalCode, city, email, phone)
                .filter(StringUtils::isNotEmpty)
                .allMatch(value -> value.length() <= maxFieldLength && !value.contains("<") && !value.contains(">"));
    }

    @DynamoDBIgnore
    public ShippingDetails copy() {
        ShippingDetails copy = new ShippingDetails();
        copy.setName(this.name);
        copy.setSurname(this.surname);
        copy.setCompanyName(this.companyName);
        copy.setStreetAndNumber(this.streetAndNumber);
        copy.setPostalCode(this.postalCode);
        copy.setCity(this.city);
        copy.setCountry(this.country);
        copy.setEmail(this.email);
        copy.setPhone(this.phone);
        copy.set_default(this._default);
        return copy;
    }
}
