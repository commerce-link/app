package pl.commercelink.orders;

import java.time.LocalDateTime;

public class ItemHistoryEvent {
    private String id;
    private LocalDateTime date;
    private String source;
    private String title;
    private String link;

    public ItemHistoryEvent(String id, LocalDateTime date, String source, String title, String link) {
        this.id = id;
        this.date = date;
        this.source = source;
        this.title = title;
        this.link = link;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
