package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.*;
import pl.commercelink.stores.Store;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentCreationServiceTest {

    @Mock private ShippingService shippingService;
    @Mock private ShipmentOwners owners;
    @Mock private ShipmentOwner owner;
    @Mock private ShipmentCreationEventPublisher publisher;
    @Mock private ShippingProvider provider;
    @Mock private Store store;
    @Mock private MessageSource messageSource;

    private ShipmentCreationService service;
    private final ShipmentRequest request = ShipmentRequest.builder().build();

    @BeforeEach
    void setUp() {
        when(shippingService.providerFor(store)).thenReturn(provider);
        when(shippingService.providerName(store)).thenReturn("furgonetka");
        when(owners.get(ShipmentOwnerType.ORDER)).thenReturn(owner);
        when(owner.markCreating(any(), any())).thenReturn(true);
        // answered only when the integration the command went to is named
        when(messageSource.getMessage(eq("shipping.creation.unconfirmed"), argThat(namesTheIntegration()), any(Locale.class)))
                .thenReturn("Integracja wysyłki (Furgonetka) nie potwierdziła nadania");
        when(messageSource.getMessage(eq("shipping.creation.notCreated"), argThat(namesTheIntegration()), any(Locale.class)))
                .thenReturn("Integracja wysyłki (Furgonetka) nie utworzyła paczki");
        service = new ShipmentCreationService(shippingService, owners, publisher, messageSource,
                ShippingIntegrationNamesFixture.names());
    }

    private static ArgumentMatcher<Object[]> namesTheIntegration() {
        return args -> args != null && args.length == 1 && ShippingIntegrationNamesFixture.DISPLAY_NAME.equals(args[0]);
    }

    private ShipmentCreationCheckRequest seed() {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.ORDER)
                .ownerId("order-1").pickUpAddressId("addr-1").build();
    }

    @Test
    void thePlaceholderIsSavedBeforeTheProviderIsCalled() {
        // given
        when(provider.createShipment(eq(request), anyString()))
                .thenAnswer(i -> ShipmentCreation.pending(i.getArgument(1), "21480003"));
        ArgumentCaptor<Shipment> placeholder = ArgumentCaptor.forClass(Shipment.class);

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.STARTED);
        InOrder order = inOrder(owner, provider, publisher);
        order.verify(owner).markCreating(any(), placeholder.capture());
        order.verify(provider).createShipment(eq(request), anyString());
        order.verify(owner).recordExternalId(argThat(r -> "21480003".equals(r.getExternalId())));
        order.verify(publisher).publish(argThat(r -> "21480003".equals(r.getExternalId()) && r.getAttempt() == 1
                && "furgonetka".equals(r.getProvider())));
        assertThat(placeholder.getValue().isCreating()).isTrue();
        assertThat(placeholder.getValue().getProvider()).isEqualTo("furgonetka");
        assertThat(placeholder.getValue().getPickUpAddressId()).isEqualTo("addr-1");
    }

    @Test
    void aRefusalMarksTheShipmentFailedAndSaysWhy() {
        // given
        when(provider.createShipment(eq(request), anyString())).thenThrow(new ShippingException("HTTP 400",
                new HttpClientException(400, "{\"errors\":[{\"message\":\"Nieprawidłowy kod pocztowy\"}]}")));

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("Nieprawidłowy kod pocztowy");
        assertThat(start.providerAnswer()).isTrue();
        verify(owner).refused(any(), eq("Nieprawidłowy kod pocztowy"), isNull());
        verify(publisher, never()).publish(any());
    }

    @Test
    void aRefusalBeforeTheProviderAnsweredIsNotTheProvidersAnswer() {
        // given
        when(provider.createShipment(eq(request), anyString()))
                .thenThrow(new ShippingException("could not create the package: no parcels"));

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("Integracja wysyłki (Furgonetka) nie utworzyła paczki");
        assertThat(start.providerAnswer()).isFalse();
        // stored as a key: the adapter's English words are for the log, not for whoever opens the shipment
        verify(owner).refused(any(), isNull(), eq("shipping.creation.notCreated"));
    }

    @Test
    void aFailureTheProviderReportsIsItsAnswer() {
        // given
        when(provider.createShipment(eq(request), anyString()))
                .thenAnswer(i -> ShipmentCreation.failed(i.getArgument(1), null, "Nieprawidłowy kod pocztowy"));

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.REFUSED);
        assertThat(start.providerAnswer()).isTrue();
    }

    @Test
    void unknownOutcomeStaysPendingAndIsChecked() {
        // given
        when(provider.createShipment(eq(request), anyString())).thenThrow(new RuntimeException("HTTP request failed"));

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.STARTED);
        verify(owner, never()).refused(any(), any(), any());
        verify(publisher).publish(argThat(r -> r.getExternalId() == null));
    }

    @Test
    void aGoneOwnerIsNeverSentToTheProvider() {
        // given
        when(owner.markCreating(any(), any())).thenReturn(false);

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.GONE);
        verifyNoInteractions(provider);
    }

    @Test
    void theCheckIsSentEvenWhenThePackageIdCannotBeRecorded() {
        // given
        when(provider.createShipment(eq(request), anyString()))
                .thenAnswer(i -> ShipmentCreation.pending(i.getArgument(1), "21480003"));
        doThrow(new RuntimeException("optimistic locking exhausted")).when(owner).recordExternalId(any());

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.STARTED);
        verify(publisher).publish(argThat(r -> "21480003".equals(r.getExternalId())));
    }

    @Test
    void aCheckThatCannotBeSentMarksTheShipmentUnconfirmedInsteadOfLeavingItPending() {
        // given
        when(provider.createShipment(eq(request), anyString()))
                .thenAnswer(i -> ShipmentCreation.pending(i.getArgument(1), "21480003"));
        doThrow(new RuntimeException("queue does not exist")).when(publisher).publish(any());

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("Integracja wysyłki (Furgonetka) nie potwierdziła nadania");
        // stored as a key, so the reason is shown in the language of whoever opens the shipment later
        verify(owner).failed(argThat(r -> "21480003".equals(r.getExternalId())), isNull(), eq("shipping.creation.unconfirmed"));
        verify(owner, never()).refused(any(), any(), any());
    }

    @Test
    void aRefusalStillReachesThePageWhenMarkingTheShipmentFailedDoesNotWork() {
        // given
        when(provider.createShipment(eq(request), anyString())).thenThrow(new ShippingException("HTTP 400",
                new HttpClientException(400, "{\"errors\":[{\"message\":\"Nieprawidłowy kod pocztowy\"}]}")));
        doThrow(new RuntimeException("optimistic locking exhausted")).when(owner).refused(any(), any(), any());

        // when
        ShipmentCreationStart start = service.start(seed(), request, store, new Shipment(ShipmentType.Courier));

        // then
        assertThat(start.outcome()).isEqualTo(ShipmentCreationStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("Nieprawidłowy kod pocztowy");
        verify(publisher, never()).publish(any());
    }
}
