package pl.commercelink.web.settings;

import java.util.List;

public record TileStatus(Tone tone, String messageKey, List<Object> args) {

    public enum Tone {
        OK, NEUTRAL, WARNING
    }

    public static TileStatus ok(String messageKey, Object... args) {
        return new TileStatus(Tone.OK, messageKey, List.of(args));
    }

    public static TileStatus neutral(String messageKey, Object... args) {
        return new TileStatus(Tone.NEUTRAL, messageKey, List.of(args));
    }

    public static TileStatus warning(String messageKey, Object... args) {
        return new TileStatus(Tone.WARNING, messageKey, List.of(args));
    }

    public Object[] argsArray() {
        return args.toArray();
    }

    public String cssClass() {
        return switch (tone) {
            case OK -> "is-ok";
            case NEUTRAL -> "is-neutral";
            case WARNING -> "is-warn";
        };
    }
}
