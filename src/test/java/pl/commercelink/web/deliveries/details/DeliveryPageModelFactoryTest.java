package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.details.DeliveryFixtures.*;

class DeliveryPageModelFactoryTest {

    @Test
    void theBackLinkIsTheSanitisedListAndTheSuperAdminsQueue() {
        // when
        DeliveryPageModel admin = DeliveryPageModelFactory.build(data(warehouse()),
                new DeliveryViewer(false, true, "/dashboard/deliveries?scope=all"));
        DeliveryPageModel foreign = DeliveryPageModelFactory.build(data(warehouse()), new DeliveryViewer(false, true, "//evil"));
        DeliveryPageModel superAdmin = DeliveryPageModelFactory.build(data(warehouse()), SUPER_ADMIN);

        // then
        assertThat(admin.backHref()).isEqualTo("/dashboard/deliveries?scope=all");
        assertThat(admin.backLabelKey()).isEqualTo("nav.deliveries");
        assertThat(foreign.backHref()).isEqualTo("/dashboard/deliveries");
        assertThat(superAdmin.backLabelKey()).isEqualTo("nav.deliveries.queue");
        assertThat(admin.shortId()).isEqualTo("2f9eb794");
    }

    @Test
    void receiveAllOpenedWithoutJavaScriptChecksEveryWaitingAllocation() {
        // given
        Delivery delivery = partlyReceived(warehouse());

        // when
        DeliveryPageModel model = DeliveryPageModelFactory.build(data(delivery, "receive-all", Set.of()), ADMIN);

        // then
        assertThat(model.openDialog()).isEqualTo("receive-all");
        assertThat(model.items().products().get(0).allocations().get(0).checked()).isFalse();
        assertThat(model.items().products().get(1).allocations().get(0).checked()).isTrue();
        assertThat(model.dialogs().pending()).extracting(DeliveryPageModel.PendingLine::productName)
                .containsExactly("Samsung MirageDrive 2TB NVMe");
        assertThat(model.dialogs().receivedCount()).isEqualTo(1);
        assertThat(model.dialogs().allocationCount()).isEqualTo(2);
    }

    @Test
    void aDialogTheViewerCannotUseIsNotOpened() {
        // when / then
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "merge", Set.of(1)), ADMIN).openDialog()).isNull();
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "split", Set.of(1)), ADMIN).openDialog()).isEqualTo("split");
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "split", Set.of()), ADMIN).openDialog()).isNull();
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "terms", Set.of()), USER).openDialog()).isNull();
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "terms", Set.of()), ADMIN).openDialog()).isEqualTo("terms");
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "force", Set.of()), ADMIN).openDialog()).isNull();
        assertThat(DeliveryPageModelFactory.build(data(outcomeUnknown(own(warehouse())), "force", Set.of()), ADMIN).openDialog()).isEqualTo("force");
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "reject", Set.of()), SUPER_ADMIN).openDialog()).isNull();
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "<script>", Set.of()), ADMIN).openDialog()).isNull();
        assertThat(DeliveryPageModelFactory.build(data(warehouse(), "payment-0", Set.of()), ADMIN).openDialog()).isNull();
    }

    @Test
    void aPaymentDialogOpensForAnExistingPaymentOfAnEditableDelivery() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment("R", "Bank", PaymentSource.BankTransfer, 100, 0));

        // when / then
        assertThat(DeliveryPageModelFactory.build(data(delivery, "payment-0", Set.of()), ADMIN).openDialog()).isEqualTo("payment-0");
        assertThat(DeliveryPageModelFactory.build(data(delivery, "payment-1", Set.of()), ADMIN).openDialog()).isNull();
        assertThat(DeliveryPageModelFactory.build(data(delivery, "payment-0", Set.of()), SUPER_ADMIN).openDialog()).isNull();
        assertThat(DeliveryPageModelFactory.build(data(delivery, "payment-00", Set.of()), ADMIN).openDialog()).isNull();
    }

    @Test
    void theQuantityDialogOpensForTheProductNamedInTheAddress() {
        // given
        DeliveryPageData data = new DeliveryPageData(warehouse(), "Manual-Hurt", null, List.of(), null, List.of(), null,
                Set.of(), "qty", "MFN-MIRAGE-01", NOW);

        // when
        DeliveryPageModel model = DeliveryPageModelFactory.build(data, ADMIN);

        // then
        assertThat(model.openDialog()).isEqualTo("qty");
        assertThat(model.dialogs().qtyProduct().mfn()).isEqualTo("MFN-MIRAGE-01");
    }

    @Test
    void theDialogsStartFromTheSuggestedDateTheCustomersShipmentAndNow() {
        // given
        DeliveryPageData data = new DeliveryPageData(withStatus(dropship(), DeliveryOrderStatus.FAILED), "AcmeB", null,
                List.of(), dropshipOrder(), List.of("DPD", "InPost"), LocalDate.of(2026, 10, 9), Set.of(), null, null, NOW);

        // when
        DeliveryPageModel.Dialogs dialogs = DeliveryPageModelFactory.build(data, ADMIN).dialogs();

        // then
        assertThat(dialogs.suggestedEstimatedDeliveryAt()).isEqualTo("2026-10-09");
        assertThat(dialogs.externalId()).isEqualTo("ACB-DS-5530");
        assertThat(dialogs.carrierOptions()).containsExactly("DPD", "InPost");
        assertThat(dialogs.shipmentType()).isEqualTo("PickupPoint");
        assertThat(dialogs.carrier()).isEqualTo("DPD");
        assertThat(dialogs.collectionPoint()).isEqualTo("PL12345");
        assertThat(dialogs.shippedAtDefault()).isEqualTo("2026-10-01T12:00");
        assertThat(dialogs.splitDate()).isEqualTo("2026-10-03");
    }

    @Test
    void dialogIdsFollowOneRule() {
        // when / then
        assertThat(DeliveryPageModelFactory.dialogId("receive-all")).isEqualTo("receive-all-dialog");
        assertThat(DeliveryPageModelFactory.dialogId("payment-2")).isEqualTo("payment-2-dialog");
        assertThat(DeliveryPageModelFactory.dialogId("ship-all")).isEqualTo("ship-dialog");
        assertThat(DeliveryPageModelFactory.dialogId("remove-all")).isEqualTo("remove-dialog");
    }

    @Test
    void theModelKnowsWhichDialogsItsActionsOpen() {
        // when
        DeliveryPageModel unknown = DeliveryPageModelFactory.build(data(outcomeUnknown(own(warehouse()))), ADMIN);
        DeliveryPageModel awaiting = DeliveryPageModelFactory.build(data(global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL))), SUPER_ADMIN);

        // then
        assertThat(unknown.offers("complete-dialog")).isTrue();
        assertThat(unknown.offers("force-dialog")).isTrue();
        assertThat(unknown.offers("reject-dialog")).isFalse();
        assertThat(awaiting.offers("reject-dialog")).isTrue();
        assertThat(DeliveryPageModelFactory.build(data(warehouse()), ADMIN).offers("receive-all-dialog")).isTrue();
        assertThat(DeliveryPageModelFactory.build(data(warehouse()), ADMIN).items().anyQtyChange()).isTrue();
    }
}
