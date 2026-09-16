package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreLogoControllerTest {

    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private StoreLogoController controller;

    @Test
    void answersNotFoundWhenTheStoreHasNoServableLogo() {
        // when
        ResponseEntity<?> response = controller.getStoreLogo("store-1");

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(storesRepository, never()).getLogoResponse(any());
    }
}
