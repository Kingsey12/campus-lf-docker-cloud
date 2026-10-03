package com.campus.lostfound.model;

public class Item {
    private String id;
    private String title;
    private String category;
    private String type;
    private String location;
    private String date;
    private String contactName;
    private String contact;
    private String description;
    private String imageUri;
    private String status = "Open";
    private String creatorId;
    private long createdAt;

    public Item() {
    }

    public Item(String id, String title, String category, String type, String location, String date,
            String contactName, String contact, String description, String imageUri, long createdAt) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.type = type;
        this.location = location;
        this.date = date;
        this.contactName = contactName;
        this.contact = contact;
        this.description = description;
        this.imageUri = imageUri;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isEmpty() ? "Open" : status;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
