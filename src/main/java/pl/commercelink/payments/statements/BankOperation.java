package pl.commercelink.payments.statements;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One operation of a bank statement: the money that came in (a positive amount) or went out (negative), its title and
 * sender as the bank printed them, and its number. The number is the bank's own when the export has one, otherwise
 * {@link OperationNumbers} derives it from the operation, so the same operation gets the same number in every file it
 * appears in; it is stored with every payment booked from the operation and tells a repeated upload apart.
 */
public record BankOperation(int line, LocalDate date, BigDecimal amount, String title, String sender,
                            String senderAccount, String number) {

    public boolean isIncoming() {
        return amount.signum() > 0;
    }
}
