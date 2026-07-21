package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreCreationServiceTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreSeeder seeder;
    @InjectMocks
    private StoreCreationService service;

    @Test
    void createsBareStoreWithGeneratedIdAndCreatedAt() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);

        // when
        Store store = service.createStore(CreateStoreRequest.bare("Sklep X", "key-1"));

        // then
        assertNotNull(store.getStoreId());
        assertEquals(10, store.getStoreId().length());
        assertEquals("Sklep X", store.getName());
        assertEquals("key-1", store.getApiKey());
        assertNotNull(store.getCreatedAt());
        assertNull(store.getDemo());
        verify(storesRepository).save(store);
    }

    @Test
    void retriesOnStoreIdCollision() {
        // given
        when(storesRepository.findById(anyString()))
                .thenReturn(new Store())
                .thenReturn(null);

        // when
        Store store = service.createStore(CreateStoreRequest.bare("Sklep X", null));

        // then
        assertNotNull(store.getStoreId());
        verify(storesRepository, times(2)).findById(anyString());
    }

    @Test
    void failsAfterExhaustedCollisionRetries() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(new Store());

        // when / then
        assertThrows(IllegalStateException.class,
                () -> service.createStore(CreateStoreRequest.bare("Sklep X", null)));
        verify(storesRepository, never()).save(any());
        verify(storesRepository, times(5)).findById(anyString());
    }

    @Test
    void appliesDemoMetadataAndDelegatesToSeeder() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);
        DemoStoreMetadata demo = new DemoStoreMetadata("a@b.pl", "2026-07-13T10:00:00Z", "2026-07-16T10:00:00Z");

        // when
        Store store = service.createStore(CreateStoreRequest.seeded("Sklep demo", demo, seeder));

        // then
        assertSame(demo, store.getDemo());
        InOrder inOrder = inOrder(storesRepository, seeder);
        inOrder.verify(storesRepository).save(store);
        inOrder.verify(seeder).seed(store);
        inOrder.verify(storesRepository).save(store);
    }

    @Test
    void skipsSeederWhenAbsent() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);

        // when
        service.createStore(CreateStoreRequest.bare("Sklep X", null));

        // then
        verifyNoInteractions(seeder);
    }

    @Test
    void wrapsSeederFailureInStoreSeedingException() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);
        RuntimeException cause = new RuntimeException("s3 down");
        doThrow(cause).when(seeder).seed(any());
        DemoStoreMetadata demo = new DemoStoreMetadata("a@b.pl", "2026-07-13T10:00:00Z", "2026-07-16T10:00:00Z");

        // when
        StoreSeedingException e = assertThrows(StoreSeedingException.class,
                () -> service.createStore(CreateStoreRequest.seeded("Sklep demo", demo, seeder)));

        // then
        assertNotNull(e.getStoreId());
        assertEquals(10, e.getStoreId().length());
        assertSame(cause, e.getCause());
    }

    @Test
    void addsWelcomeNotificationForNonDemoStore() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);

        // when
        Store store = service.createStore(CreateStoreRequest.bare("Sklep X", null));

        // then
        assertTrue(store.getNotifications().stream()
                .anyMatch(n -> n.getType() == StoreNotificationType.WELCOME));
    }

    @Test
    void skipsWelcomeNotificationForDemoStore() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);
        DemoStoreMetadata demo = new DemoStoreMetadata("a@b.pl", "2026-07-13T10:00:00Z", "2026-07-16T10:00:00Z");

        // when
        Store store = service.createStore(CreateStoreRequest.seeded("Sklep demo", demo, seeder));

        // then
        assertTrue(store.getNotifications().stream()
                .noneMatch(n -> n.getType() == StoreNotificationType.WELCOME));
    }

    @Test
    void skipsWelcomeNotificationWhenFlagDisabled() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);

        // when
        Store store = service.createStore(CreateStoreRequest.bare("Sklep X", null, false));

        // then
        assertTrue(store.getNotifications().stream()
                .noneMatch(n -> n.getType() == StoreNotificationType.WELCOME));
    }

    @Test
    void setsRegistrationEmailAsBillingEmail() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);

        // when
        Store store = service.createStore(CreateStoreRequest.registered("Sklep X", "owner@example.com"));

        // then
        assertNotNull(store.getBillingDetails());
        assertEquals("owner@example.com", store.getBillingDetails().getEmail());
    }

    @Test
    void copiesDemoOwnerEmailToBillingEmail() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);
        DemoStoreMetadata demo = new DemoStoreMetadata("a@b.pl", "2026-07-13T10:00:00Z", "2026-07-16T10:00:00Z");

        // when
        Store store = service.createStore(CreateStoreRequest.seeded("Sklep demo", demo, seeder));

        // then
        assertNotNull(store.getBillingDetails());
        assertEquals("a@b.pl", store.getBillingDetails().getEmail());
    }

    @Test
    void leavesBillingDetailsEmptyWithoutOwnerEmail() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);

        // when
        Store store = service.createStore(CreateStoreRequest.bare("Sklep X", null));

        // then
        assertNull(store.getBillingDetails());
    }
}
