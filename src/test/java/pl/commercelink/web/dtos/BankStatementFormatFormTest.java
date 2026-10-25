package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.BankStatementFormat;
import pl.commercelink.stores.PayoutChannel;
import pl.commercelink.orders.PaymentSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BankStatementFormatFormTest {

    @Test
    void requiresTheDateAmountAndTitleColumns() {
        // given
        BankStatementFormatForm form = BankStatementFormatForm.defaults();

        // when
        var errors = form.validate();

        // then
        assertThat(errors).containsOnlyKeys("dateColumn", "amountColumn", "titleColumn1");
    }

    @Test
    void requiresBothColumnsOfSplitAmounts() {
        // given
        BankStatementFormatForm form = filled();
        form.setAmountMode(BankStatementFormatForm.SPLIT);
        form.setCreditColumn("Uznania");

        // when
        var errors = form.validate();

        // then
        assertThat(errors).containsOnlyKeys("debitColumn");
    }

    @Test
    void refusesAHeaderRowThatIsNoLineNumber() {
        // given
        BankStatementFormatForm form = filled();
        form.setHeaderRow("pierwszy");

        // when / then
        assertThat(form.validate()).containsOnlyKeys("headerRow");
    }

    @Test
    void storesWhatWasTypedAndShowsItAgain() {
        // given
        BankStatementFormatForm form = filled();
        form.setSeparator("TAB");
        form.setTitleColumn2("#Tytuł");
        form.setChannels(new ArrayList<>(List.of(
                row("ALLEGRO", "Allegro", "", true),
                row("", "", "", false))));

        // when
        BankStatementFormat format = form.toFormat();
        BankStatementFormatForm shown = BankStatementFormatForm.from(format);

        // then
        assertThat(format.getSeparator()).isEqualTo("\t");
        assertThat(format.getHeaderRow()).isEqualTo(27);
        assertThat(format.isDecimalComma()).isTrue();
        assertThat(format.getTitleColumns()).containsExactly("#Opis operacji", "#Tytuł");
        assertThat(format.getPayoutChannels()).singleElement().satisfies(channel -> {
            assertThat(channel.getMatch()).isEqualTo("ALLEGRO");
            assertThat(channel.getOrderSource()).isEqualTo("Allegro");
            assertThat(channel.getMethod()).isNull();
            assertThat(channel.isNetOfFees()).isTrue();
        });
        assertThat(format.isComplete()).isTrue();
        assertThat(shown.getSeparator()).isEqualTo("TAB");
        assertThat(shown.getTitleColumn2()).isEqualTo("#Tytuł");
        assertThat(shown.getChannels()).hasSize(1 + BankStatementFormatForm.EMPTY_CHANNEL_ROWS);
    }

    @Test
    void aPayerMayHaveSeveralRowsEachForItsOwnOrders() {
        // given
        BankStatementFormatForm form = filled();
        form.setChannels(new ArrayList<>(List.of(
                row("SANTANDER", "", "Installments", false),
                row("SANTANDER", "SHOP", "OnlinePayment", false))));

        // when
        List<PayoutChannel> channels = form.toFormat().getPayoutChannels();

        // then
        assertThat(form.validate()).isEmpty();
        assertThat(channels).extracting(PayoutChannel::getMethod)
                .containsExactly(PaymentSource.Installments, PaymentSource.OnlinePayment);
        assertThat(channels.get(1).getOrderSource()).isEqualTo(PayoutChannel.SHOP);
        assertThat(BankStatementFormatForm.from(form.toFormat()).getChannels().get(0).getMethod()).isEqualTo("Installments");
    }

    @Test
    void asksForTheMatchAndTheChannelOfAStartedRow() {
        // given
        BankStatementFormatForm form = filled();
        form.setChannels(new ArrayList<>(List.of(row("", "Allegro", "", false), row("PAYPRO", "", "", true),
                row("PAYPRO", "", "Barter", false))));

        // when / then
        assertThat(form.validate()).containsOnlyKeys("channel-0-match", "channel-1-source", "channel-2-method");
    }

    @Test
    void aGuessKeepsTheChannelsAndTheSample() {
        // given
        BankStatementFormatForm form = BankStatementFormatForm.defaults();
        form.setChannels(new ArrayList<>(List.of(row("ALLEGRO", "Allegro", "", true))));
        form.attachSample(new byte[]{1, 2, 3}, "wyciag.csv");
        BankStatementFormat guess = new BankStatementFormat();
        guess.setEncoding("UTF-8");
        guess.setSeparator(",");
        guess.setHeaderRow(1);
        guess.setDateFormat("dd.MM.yyyy");
        guess.setDateColumn("Date");

        // when
        BankStatementFormatForm guessed = form.withGuess(guess);

        // then
        assertThat(guessed.getDateColumn()).isEqualTo("Date");
        assertThat(guessed.getEncoding()).isEqualTo("UTF-8");
        assertThat(guessed.getChannels().get(0).getMatch()).isEqualTo("ALLEGRO");
        assertThat(guessed.sample()).containsExactly(1, 2, 3);
        assertThat(guessed.getSampleName()).isEqualTo("wyciag.csv");
    }

    @Test
    void emptyHiddenSampleFieldsMeanNoSample() {
        // given
        BankStatementFormatForm form = BankStatementFormatForm.defaults();

        // when
        form.setSampleData("");
        form.setSampleName("");

        // then
        assertThat(form.getSampleName()).isNull();
        assertThat(form.sample()).isNull();
    }

    private static BankStatementFormatForm filled() {
        BankStatementFormatForm form = BankStatementFormatForm.defaults();
        form.setHeaderRow("27");
        form.setDateColumn("#Data księgowania");
        form.setAmountColumn("#Kwota");
        form.setTitleColumn1("#Opis operacji");
        return form;
    }

    private static BankStatementFormatForm.ChannelRow row(String match, String source, String method, boolean net) {
        BankStatementFormatForm.ChannelRow row = new BankStatementFormatForm.ChannelRow();
        row.setMatch(match);
        row.setSource(source);
        row.setMethod(method);
        row.setNetOfFees(net);
        return row;
    }
}
