package pl.commercelink.registration;

import java.util.regex.Pattern;

final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 256;
    private static final Pattern SYMBOL = Pattern.compile("[\\^$*.\\[\\]{}()?\"!@#%&/\\\\,><':;|_~`+=\\- ]");

    private PasswordPolicy() {
    }

    static boolean isValid(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isDigit)
                && SYMBOL.matcher(password).find();
    }
}
