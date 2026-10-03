package com.campus.lostfound.data;

import com.campus.lostfound.model.Item;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;

/** REST client for the Docker backend. Firebase remains the source for auth, chat, and listeners. */
public final class ApiClient {
    // Android emulator reaches services published on the host through 10.0.2.2.
    private static final String BASE_URL = "http://192.168.45.10:3000/api";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Gson GSON = new Gson();

    public interface Callback {
        void onComplete(Exception error);
    }

    private ApiClient() {
    }

    public static void createItem(Item item, Callback callback) {
        requestWithToken("POST", "/items", GSON.toJson(item), callback);
    }

    public static void updateItem(Item item, Callback callback) {
        requestWithToken("PUT", "/items/" + encodePath(item.getId()), GSON.toJson(item), callback);
    }

    public static void deleteItem(String itemId, Callback callback) {
        requestWithToken("DELETE", "/items/" + encodePath(itemId), null, callback);
    }

    private static void requestWithToken(String method, String path, String body, Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onComplete(new IllegalStateException("No Firebase user is signed in."));
            return;
        }

        user.getIdToken(false).addOnCompleteListener(tokenTask -> {
            if (!tokenTask.isSuccessful() || tokenTask.getResult() == null) {
                Exception error = tokenTask.getException() != null
                        ? tokenTask.getException()
                        : new IllegalStateException("Could not obtain Firebase ID token.");
                callback.onComplete(error);
                return;
            }

            String token = tokenTask.getResult().getToken();
            EXECUTOR.execute(() -> {
                try {
                    executeRequest(method, path, body, token);
                    callback.onComplete(null);
                } catch (Exception error) {
                    callback.onComplete(error);
                }
            });
        });
    }

    private static void executeRequest(String method, String path, String body, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);

        if (body != null) {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
        }

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            String errorBody = readBody(connection.getErrorStream());
            throw new IOException("Docker API returned HTTP " + statusCode
                    + (errorBody.isEmpty() ? "" : ": " + errorBody));
        }
        connection.disconnect();
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    private static String encodePath(String value) {
        return value == null ? "" : value.replace("%", "%25").replace("/", "%2F");
    }
}
