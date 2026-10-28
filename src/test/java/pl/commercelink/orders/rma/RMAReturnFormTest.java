package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class RMAReturnFormTest {

    @Test
    void aRefusedFormSurvivesASerializedSessionStore() throws Exception {
        // given
        ShippingDetails details = new ShippingDetails();
        details.setName("Anna");
        details.setPostalCode("30001");
        RMAReturnForm form = new RMAReturnForm();
        form.setShippingDetails(details);
        form.setSelectedPackageTemplateId("t1");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        // when
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(form);
        }
        RMAReturnForm copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (RMAReturnForm) in.readObject();
        }

        // then
        assertThat(copy.getSelectedPackageTemplateId()).isEqualTo("t1");
        assertThat(copy.getShippingDetails().getName()).isEqualTo("Anna");
        assertThat(copy.getShippingDetails().getPostalCode()).isEqualTo("30001");
    }
}
