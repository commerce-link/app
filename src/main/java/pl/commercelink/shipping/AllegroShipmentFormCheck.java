package pl.commercelink.shipping;

import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.api.PackageOption;
import pl.commercelink.shipping.api.ShipmentProposal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The Wysyłam z Allegro form against the limits of the buyer's delivery method (the proposal): one complete parcel
 * within the dimensions and weight, cash on delivery and insurance within the method's maximum, insurance at least
 * the cash on delivery. Checked before Allegro is asked, so the operator fixes the field instead of reading a refusal.
 * The adapter checks the same before it sends the command; this check only puts the reasons next to the fields.
 */
public final class AllegroShipmentFormCheck {

    /** field: "parcel", "cashOnDeliveryAmount" or "insurance"; key and args: the message shown next to it. */
    public record Problem(String field, String key, Object[] args) {
    }

    private AllegroShipmentFormCheck() {
    }

    /** storeHasBankAccount: cash on delivery is paid out to the store's default bank account, so without one it is refused. */
    public static List<Problem> check(ShippingForm form, ShipmentProposal proposal, boolean storeHasBankAccount) {
        List<Problem> problems = new ArrayList<>();
        List<ParcelForm> parcels = form.getCompleteParcels();
        if (parcels.isEmpty()) {
            problems.add(new Problem("parcel", "shipping.allegro.error.parcel", new Object[0]));
            return problems;
        }
        if (parcels.size() > 1) {
            problems.add(new Problem("parcel", "shipping.allegro.error.oneParcel", new Object[0]));
            return problems;
        }
        ParcelForm parcel = parcels.get(0);
        PackageOption option = packageOption(proposal);
        if (option != null) {
            if (exceedsDimensions(parcel, option)) {
                problems.add(new Problem("parcel", "shipping.allegro.error.dimensions", new Object[]{
                        plain(option.maxLength()), plain(option.maxWidth()), plain(option.maxHeight())}));
            }
            if (exceeds(parcel.getWeight(), option.maxWeight())) {
                problems.add(new Problem("parcel", "shipping.allegro.error.weight", new Object[]{plain(option.maxWeight())}));
            }
        }
        BigDecimal cod = form.isCashOnDelivery() ? BigDecimal.valueOf(form.getCashOnDeliveryAmount()) : BigDecimal.ZERO;
        if (proposal.maxCashOnDelivery() != null && cod.compareTo(proposal.maxCashOnDelivery()) > 0) {
            problems.add(new Problem("cashOnDeliveryAmount", "shipping.allegro.error.cod",
                    new Object[]{plain(proposal.maxCashOnDelivery())}));
        }
        if (form.isCashOnDelivery() && !storeHasBankAccount) {
            problems.add(new Problem("cashOnDeliveryAmount", "shipping.allegro.error.cod.noAccount", new Object[0]));
        }
        BigDecimal insurance = BigDecimal.valueOf(parcel.getValue());
        // the insured value is whole zloty (ParcelForm#value): at least the cash on delivery rounded up
        BigDecimal minimum = cod.setScale(0, RoundingMode.CEILING);
        if (insurance.compareTo(minimum) < 0) {
            problems.add(new Problem("insurance", "shipping.allegro.error.insurance.min", new Object[]{plain(minimum)}));
        } else if (proposal.maxInsurance() != null && insurance.compareTo(proposal.maxInsurance()) > 0) {
            problems.add(new Problem("insurance", "shipping.allegro.error.insurance.max",
                    new Object[]{plain(proposal.maxInsurance())}));
        }
        return problems;
    }

    /** The limits of a plain parcel, else of the first option the method has; null when it gives none. */
    public static PackageOption packageOption(ShipmentProposal proposal) {
        List<PackageOption> options = proposal.packageOptions() == null ? List.of() : proposal.packageOptions();
        return options.stream().filter(o -> "PACKAGE".equals(o.type())).findFirst()
                .orElse(options.isEmpty() ? null : options.get(0));
    }

    static String plain(BigDecimal value) {
        return value == null ? "–" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * The parcel's longest side against the method's longest limit, and so on down: a box fits whichever way it lies,
     * and the template's depth and width reach the form's length and width columns in either order. A missing limit
     * leaves its rank unlimited.
     */
    private static boolean exceedsDimensions(ParcelForm parcel, PackageOption option) {
        List<Integer> sides = new ArrayList<>(List.of(parcel.getDepth(), parcel.getWidth(), parcel.getHeight()));
        sides.sort(java.util.Comparator.reverseOrder());
        List<BigDecimal> limits = new ArrayList<>(java.util.Arrays.asList(option.maxLength(), option.maxWidth(), option.maxHeight()));
        limits.sort(java.util.Comparator.nullsLast(java.util.Comparator.<BigDecimal>naturalOrder()).reversed());
        for (int i = 0; i < 3; i++) {
            if (exceeds(sides.get(i), limits.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean exceeds(int value, BigDecimal max) {
        return max != null && BigDecimal.valueOf(value).compareTo(max) > 0;
    }
}
