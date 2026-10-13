package pl.commercelink.web.stores;

/** The account state shown as the one pill of a row; the tones follow the account status pill of the frame. */
public enum StoreState {
    FULL("is-ok", "stores.list.state.full", null, false, StoresListQuery.Segment.FULL),
    TRIAL("is-info", "stores.list.state.trial", "stores.list.note.trial", false, StoresListQuery.Segment.TRIAL),
    TRIAL_ENDING("is-warn", "stores.list.state.trial", "stores.list.note.trial", true, StoresListQuery.Segment.TRIAL),
    TRIAL_ENDED("is-bad", "stores.list.state.trialEnded", "stores.list.note.deletion", true, StoresListQuery.Segment.INACTIVE),
    SWITCHED_OFF("is-bad", "stores.list.state.switchedOff", "stores.list.note.since", false, StoresListQuery.Segment.INACTIVE),
    DEMO("is-neutral", "stores.list.state.demo", "stores.list.note.expires", false, StoresListQuery.Segment.DEMO);

    private final String tone;
    private final String labelKey;
    private final String noteKey;
    private final boolean noteWarn;
    private final StoresListQuery.Segment segment;

    StoreState(String tone, String labelKey, String noteKey, boolean noteWarn, StoresListQuery.Segment segment) {
        this.tone = tone;
        this.labelKey = labelKey;
        this.noteKey = noteKey;
        this.noteWarn = noteWarn;
        this.segment = segment;
    }

    public String tone() { return tone; }
    public String labelKey() { return labelKey; }
    public String noteKey() { return noteKey; }
    public boolean noteWarn() { return noteWarn; }
    public StoresListQuery.Segment segment() { return segment; }
}
