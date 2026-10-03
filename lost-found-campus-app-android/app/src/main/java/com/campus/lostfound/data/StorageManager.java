package com.campus.lostfound.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.campus.lostfound.model.ChatMessage;
import com.campus.lostfound.model.Item;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.auth.FirebaseAuth;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageManager {
    private static final String PREFS_NAME = "lost_found_prefs";
    private static final String KEY_ITEMS = "lost_found_items_v1";
    private static final String KEY_CHATS = "lost_found_chats_v1";

    private final SharedPreferences sharedPreferences;
    private final Gson gson;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public interface ItemsCallback {
        void onSuccess(List<Item> items);

        void onError(Exception error);
    }

    public interface SaveCallback {
        void onComplete(Exception error);
    }

    public interface AuthCallback {
        void onReady(String userId);

        void onError(Exception error);
    }

    public interface ChatCallback {
        void onChanged(List<ChatMessage> messages);

        void onError(Exception error);
    }

    public StorageManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    public String getCurrentUserId() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }

    public String getCurrentUserEmail() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getEmail();
    }

    public boolean isEmailVerified() {
        return auth.getCurrentUser() != null && auth.getCurrentUser().isEmailVerified();
    }

    public void sendVerificationEmail(SaveCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onComplete(new IllegalStateException("No email account is signed in."));
            return;
        }
        auth.getCurrentUser().sendEmailVerification()
                .addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    public void ensureSignedIn(AuthCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            callback.onReady(currentUserId);
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> callback.onReady(result.getUser().getUid()))
                .addOnFailureListener(callback::onError);
    }

    public boolean isGuest() {
        return auth.getCurrentUser() == null
                || auth.getCurrentUser().isAnonymous()
                || !auth.getCurrentUser().isEmailVerified();
    }

    public void signInWithEmail(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onReady(result.getUser().getUid()))
                .addOnFailureListener(callback::onError);
    }

    public void createEmailAccount(String email, String password, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onReady(result.getUser().getUid()))
                .addOnFailureListener(callback::onError);
    }

    public void sendPasswordReset(String email, SaveCallback callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    public void signOut() {
        auth.signOut();
    }

    public void deleteCurrentAccount(SaveCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onComplete(new IllegalStateException("No signed-in account."));
            return;
        }
        auth.getCurrentUser().delete()
                .addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    public ListenerRegistration observeItems(ItemsCallback callback) {
        return firestore.collection("items")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Item> items = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            Item item = document.toObject(Item.class);
                            if (item != null) {
                                if (item.getId() == null || item.getId().isEmpty()) {
                                    item.setId(document.getId());
                                }
                                items.add(item);
                            }
                        }
                    }
                    saveItems(items);
                    callback.onSuccess(items);
                });
    }

    public void saveItem(Item item, SaveCallback callback) {
        ApiClient.createItem(item, error -> {
            if (error == null) {
                List<Item> items = getItems();
                items.removeIf(existing -> item.getId().equals(existing.getId()));
                items.add(0, item);
                saveItems(items);
            }
            callback.onComplete(error);
        });
    }

    public void deleteItemFromCloud(String itemId, SaveCallback callback) {
        ApiClient.deleteItem(itemId, error -> {
            if (error == null) {
                deleteItem(itemId);
            }
            callback.onComplete(error);
        });
    }

    public ListenerRegistration observeChat(String itemId, ChatCallback callback) {
        return chatMessages(itemId)
                .orderBy("at", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<ChatMessage> messages = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            ChatMessage message = document.toObject(ChatMessage.class);
                            if (message != null) {
                                messages.add(message);
                            }
                        }
                    }
                    callback.onChanged(messages);
                });
    }

    public void addCloudMessage(String itemId, ChatMessage message, SaveCallback callback) {
        chatMessages(itemId)
                .add(message)
                .addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    private CollectionReference chatMessages(String itemId) {
        String userId = getCurrentUserId();
        String chatUserId = userId == null ? "anonymous" : userId;
        return firestore.collection("chats").document(itemId)
                .collection("users").document(chatUserId).collection("messages");
    }

    public List<Item> getItems() {
        String raw = sharedPreferences.getString(KEY_ITEMS, "");
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<Item>>() {
        }.getType();
        List<Item> items = gson.fromJson(raw, type);
        return items == null ? new ArrayList<>() : items;
    }

    public void saveItems(List<Item> items) {
        sharedPreferences.edit().putString(KEY_ITEMS, gson.toJson(items)).apply();
    }

    public void deleteItem(String itemId) {
        List<Item> items = getItems();
        List<Item> updatedItems = new ArrayList<>();
        for (Item item : items) {
            if (!item.getId().equals(itemId)) {
                updatedItems.add(item);
            }
        }
        saveItems(updatedItems);
    }

    public Map<String, List<ChatMessage>> getChats() {
        String raw = sharedPreferences.getString(KEY_CHATS, "");
        if (raw == null || raw.isEmpty()) {
            return new HashMap<>();
        }

        Type type = new TypeToken<Map<String, List<ChatMessage>>>() {
        }.getType();
        Map<String, List<ChatMessage>> chats = gson.fromJson(raw, type);
        return chats == null ? new HashMap<>() : chats;
    }

    public void saveChats(Map<String, List<ChatMessage>> chats) {
        sharedPreferences.edit().putString(KEY_CHATS, gson.toJson(chats)).apply();
    }
}
