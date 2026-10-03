# Campus L&F Docker Demo Guide

## A. Run the cloud backend

From the project root:

```powershell
docker compose up -d --build
```

If Firebase CRUD will be demonstrated, revoke any previously exposed key, create a new Firebase Admin service account key, and place it at `secrets/firebase-service-account.json`. The Compose file mounts that file read-only. Never paste the service-account JSON into the repository or chat.

## B. Show the running container

```powershell
docker compose ps
```

Expected state: the `backend` service is running and eventually reports `healthy`.

## C. Test the health endpoint

```powershell
Invoke-RestMethod http://localhost:3000/api/health
```

Expected response includes:

```json
{
  "status": "ok",
  "service": "Campus L&F Backend"
}
```

The `firebase` field indicates whether Firebase Admin initialized successfully.

## D. Demonstrate Campus L&F

1. Open the existing Android application in Android Studio or on the emulator.
2. Explain that Android Authentication, real-time listeners, chat, and Storage remain on Firebase.
3. Explain that Android item create/delete requests use the Docker REST API, which writes to the same Firestore `items` collection through Firebase Admin SDK.
4. Show the backend API in a browser or REST client at `http://localhost:3000/api/items`.
5. For a protected write request, send a Firebase ID token in the header:

```text
Authorization: Bearer <Firebase ID token>
```

## E. Demonstrate failure

```powershell
docker compose stop backend
docker compose ps
```

Now the health request is unavailable:

```powershell
Invoke-RestMethod http://localhost:3000/api/health
```

This demonstrates that the containerized service has been stopped while Firebase itself remains an independent managed service.

## F. Demonstrate recovery

```powershell
docker compose start backend
Invoke-RestMethod http://localhost:3000/api/health
```

The service returns to the healthy state. The Compose configuration also has `restart: unless-stopped`, so an unexpected container failure is automatically restarted.

## G. Show logs

```powershell
docker compose logs backend
```

Useful lines include the listening port and Firebase initialization status.

## H. Explain scalability

The backend is stateless: it stores no item data inside the container. Firestore is the shared data layer, so multiple backend instances can use the same data. In a production setup, a load balancer would distribute requests across instances. The local Compose demo intentionally uses one published host port and does not add Kubernetes or unnecessary infrastructure.

## Stop the demo

```powershell
docker compose down
```
