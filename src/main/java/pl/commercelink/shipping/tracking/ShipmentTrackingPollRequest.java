package pl.commercelink.shipping.tracking;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** One parcel to ask its integration about; {@code provider} is the integration that tracks it. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class ShipmentTrackingPollRequest {

    private String storeId;
    private String trackingNo;
    private String provider;
}
