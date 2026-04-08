package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

class RMAItemAcceptNotification extends EmailNotification {
    @JsonProperty("rmaId")
    private String rmaId;
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("rmaItems")
    private List<ItemInfo> rmaItems;

    RMAItemAcceptNotification(String recipientEmail, String recipientName, String rmaId, String orderId, List<RMAItem> rmaItems) {
        super(recipientEmail, recipientName);
        this.rmaId = rmaId;
        this.orderId = orderId;
        this.rmaItems = rmaItems.stream()
                .map(i -> new ItemInfo(
                        i.getName(),
                        i.getStatus().getLocalizedName(new Locale("pl")),
                        i.getDesiredResolution().getLocalizedName(new Locale("pl")),
                        i.getActualResolution() != null ? i.getActualResolution().getLocalizedName(new Locale("pl")) : "N/A",
                        i.getReason())
                )
                .collect(Collectors.toList());
    }

    public String getRmaId() {
        return rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public List<ItemInfo> getRmaItems() {
        return rmaItems;
    }

    static class ItemInfo {
        @JsonProperty("name")
        private final String name;
        @JsonProperty("status")
        private final String status;
        @JsonProperty("desiredResolution")
        private final String desiredResolution;
        @JsonProperty("actualResolution")
        private String actualResolution;
        @JsonProperty("reason")
        private String reason;

        public ItemInfo(String name, String status, String desiredResolution, String actualResolution, String reason) {
            this.name = name;
            this.status = status;
            this.desiredResolution = desiredResolution;
            this.actualResolution = actualResolution;
            this.reason = reason;
        }

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }

        public String getDesiredResolution() {
            return desiredResolution;
        }

        public String getActualResolution() {
            return actualResolution;
        }

        public String getReason() {
            return reason;
        }
    }
}
