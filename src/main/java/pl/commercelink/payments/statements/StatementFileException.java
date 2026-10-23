package pl.commercelink.payments.statements;

import lombok.Getter;

/** A statement file that cannot be read with the account's format at all; the message key says why, for the operator. */
@Getter
public class StatementFileException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] arguments;

    public StatementFileException(String messageKey, Object... arguments) {
        super(messageKey);
        this.messageKey = messageKey;
        this.arguments = arguments;
    }
}
