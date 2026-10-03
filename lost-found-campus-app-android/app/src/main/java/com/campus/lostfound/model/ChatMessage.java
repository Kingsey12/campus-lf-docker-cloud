package com.campus.lostfound.model;

public class ChatMessage {
    private String sender;
    private String text;
    private long at;

    public ChatMessage() {
    }

    public ChatMessage(String sender, String text, long at) {
        this.sender = sender;
        this.text = text;
        this.at = at;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getAt() {
        return at;
    }

    public void setAt(long at) {
        this.at = at;
    }
}
