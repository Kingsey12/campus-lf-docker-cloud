# Lost Found Campus App (Android Java)

This is a native Android app (Java) version of the lost-and-found project.

## Features

- Add lost/found item posts
- Search by title/location/category
- Filter by category and type
- Contact owner via email or phone dialer
- In-app chat backed by Firestore
- Firebase Authentication and Firestore item synchronization
- `SharedPreferences` + JSON local cache

## Open Project

1. Open Android Studio.
2. Select **Open** and choose this folder:
   - `project/lost-found-campus-app-android`
3. Let Gradle sync finish.

## Build APK (Debug)

1. In Android Studio, go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. After build completes, click **locate** in the notification.
3. APK path is typically:
   - `app/build/outputs/apk/debug/app-debug.apk`

## Build Signed APK (Release)

1. Go to **Build > Generate Signed Bundle / APK**.
2. Choose **APK**.
3. Create/select keystore, fill key info, choose `release`.
4. Finish wizard to produce signed APK.

## Notes

- Authentication, real-time item listeners, chat, and Storage remain on Firebase.
- Item create/delete requests use the Dockerized Node.js + Express REST API using Firebase Admin SDK. The Android emulator reaches it at `http://10.0.2.2:3000/api`.
- Date input is text field (`YYYY-MM-DD`) for simplicity.
