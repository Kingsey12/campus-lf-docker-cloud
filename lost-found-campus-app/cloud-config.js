/**
 * Cloud Configuration & Multi-Provider Architecture
 * Course: Cloud Design and Implementation
 * Project: Lost & Found Campus Cloud App
 * 
 * Supports Dual-Mode:
 * 1. Live Cloud Provider (Google Cloud / Firebase Firestore, Auth, Storage)
 * 2. Enterprise Cloud Simulator (Edge / Local NoSQL with Realtime Broadcast Sync)
 */

const DEFAULT_FIREBASE_CONFIG = {
  apiKey: "AIzaSyAmtIeGgpe4kXHrJFtVY_6o19AOciT76uA",
  authDomain: "campus-lost-found-12.firebaseapp.com",
  projectId: "campus-lost-found-12",
  storageBucket: "campus-lost-found-12.firebasestorage.app",
  messagingSenderId: "584279989415",
  appId: "1:584279989415:android:a17fb642c6f3c0687a4f82"
};

class CloudManager {
  constructor() {
    this.storageKeyConfig = "lf_cloud_config_v2";
    this.storageKeyItems = "lf_cloud_items_v2";
    this.storageKeyChats = "lf_cloud_chats_v2";
    this.storageKeyAuth = "lf_cloud_auth_user_v2";
    this.storageKeyMetrics = "lf_cloud_metrics_v2";

    this.broadcastChannel = typeof BroadcastChannel !== "undefined" ? new BroadcastChannel("campus_lostfound_cloud_sync") : null;
    this.subscribers = {
      items: [],
      chats: [],
      auth: [],
      status: []
    };

    this.state = {
      mode: "simulator", // "simulator" | "firebase"
      status: "online",
      latencyMs: 38,
      readsCount: 0,
      writesCount: 0,
      cloudRegion: "asia-northeast3 (Seoul / Campus Edge)",
      firebaseInitialized: false,
      currentUser: this.loadCurrentUser()
    };

    this.initializeFirebase();

    this.init();
  }

  initializeFirebase() {
    if (typeof firebase === "undefined" || !DEFAULT_FIREBASE_CONFIG.apiKey) return;
    try {
      const app = firebase.apps.length ? firebase.app() : firebase.initializeApp(DEFAULT_FIREBASE_CONFIG);
      this.db = app.firestore();
      this.auth = app.auth();
      this.storage = app.storage();
      this.state.mode = "firebase";
      this.state.firebaseInitialized = true;
      this.auth.onAuthStateChanged(user => {
        this.state.currentUser = user ? {
          uid: user.uid,
          email: user.email || "guest@campus.edu",
          displayName: user.email ? user.email.split("@")[0] : "Campus Guest",
          role: user.isAnonymous ? "Guest" : "Gachon Student",
          isGuest: user.isAnonymous
        } : null;
        this.notify("auth", this.state.currentUser);
      });
      if (!this.auth.currentUser) {
        this.auth.signInAnonymously().catch(error => {
          console.warn("Anonymous guest sign-in failed:", error);
        });
      }
    } catch (error) {
      console.warn("Firebase web initialization failed; using simulator:", error);
    }
  }

  normalizeItem(item) {
    const imageUrl = item.imageDataUrl || item.imageUri || "";
    return { ...item, imageDataUrl: imageUrl, imageUri: imageUrl };
  }

  init() {
    const savedConfig = localStorage.getItem(this.storageKeyConfig);
    if (savedConfig) {
      try {
        const parsed = JSON.parse(savedConfig);
        if (parsed.mode === "firebase" && parsed.apiKey) {
          this.state.mode = "firebase";
        }
      } catch (e) {
        console.warn("Could not parse saved cloud config:", e);
      }
    }

    // Measure simulated latency jitter
    setInterval(() => {
      if (this.state.status === "online") {
        const base = this.state.mode === "firebase" ? 95 : 32;
        this.state.latencyMs = Math.floor(base + Math.random() * 25);
        this.notify("status", this.getStatusInfo());
      }
    }, 5000);

    // Cross-tab real-time sync listener
    if (this.broadcastChannel) {
      this.broadcastChannel.onmessage = (event) => {
        const { type, payload } = event.data || {};
        if (type === "ITEMS_UPDATED") {
          this.notify("items", this.getItemsInternal());
        } else if (type === "CHAT_UPDATED") {
          this.notify("chats", payload);
        } else if (type === "AUTH_UPDATED") {
          this.state.currentUser = payload;
          this.notify("auth", payload);
        }
      };
    }

    // Seed sample data if empty
    if (this.state.mode === "simulator") {
      this.seedInitialData();
    } else {
      this.observeCloudItems();
    }
  }

  observeCloudItems() {
    const emitItems = (snapshot) => {
      const items = snapshot.docs
        .map(doc => this.normalizeItem({ id: doc.id, ...doc.data() }))
        .sort((left, right) => (right.createdAt || 0) - (left.createdAt || 0));
      this.notify("items", items);
    };

    this.db.collection("items").orderBy("createdAt", "desc").onSnapshot(emitItems, (error) => {
      console.warn("Ordered Firestore item listener failed; retrying without orderBy:", error);
      this.db.collection("items").onSnapshot(emitItems, (fallbackError) => {
        console.error("Firestore item listener failed:", fallbackError);
        this.state.status = "offline";
        this.notify("status", this.getStatusInfo());
      });
    });
  }

  seedInitialData() {
    const existing = localStorage.getItem(this.storageKeyItems);
    if (!existing || JSON.parse(existing).length === 0) {
      const sampleItems = [
        {
          id: "item-001",
          title: "MacBook Pro M2 Silver",
          category: "Electronics",
          type: "Lost",
          status: "Open",
          location: "Library Building, 3rd Floor Quiet Zone",
          date: "2026-09-20",
          contactName: "Azizbek Rahimov",
          contact: "azizbek@campus.edu / +998901234567",
          description: "Silver 14-inch MacBook Pro inside a black felt sleeve. Has university CS club sticker on the top cover.",
          imageDataUrl: "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=600&q=80",
          creatorId: "user-seed-1",
          createdAt: Date.now() - 1000 * 60 * 60 * 4
        },
        {
          id: "item-002",
          title: "Campus Student ID & Metro Card",
          category: "ID Cards",
          type: "Found",
          status: "Open",
          location: "Main Cafeteria, Table near Coffee Station",
          date: "2026-09-21",
          contactName: "Madina Karimova",
          contact: "madina.k@campus.edu",
          description: "Student ID card belonging to Shakhzodbek M. Handed over to Campus Security Desk #2.",
          imageDataUrl: "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=600&q=80",
          creatorId: "user-seed-2",
          createdAt: Date.now() - 1000 * 60 * 60 * 2
        },
        {
          id: "item-003",
          title: "Sony WH-1000XM4 Wireless Headphones",
          category: "Electronics",
          type: "Lost",
          status: "Claimed",
          location: "Sports Complex & Gym Locker Room",
          date: "2026-09-18",
          contactName: "Jasur Aliyev",
          contact: "jasur@campus.edu",
          description: "Black over-ear headphones in zippered hard case. Recovered and returned successfully via campus lost & found!",
          imageDataUrl: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=600&q=80",
          creatorId: "user-seed-3",
          createdAt: Date.now() - 1000 * 60 * 60 * 24 * 2
        },
        {
          id: "item-004",
          title: "Calculus & Linear Algebra Textbook",
          category: "Books",
          type: "Found",
          status: "Open",
          location: "Lecture Hall B, Room 204",
          date: "2026-09-21",
          contactName: "Dilshod Vohidov",
          contact: "+998939876543",
          description: "Hardcover university mathematics textbook. Notes written on page 45.",
          imageDataUrl: "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80",
          creatorId: "user-seed-4",
          createdAt: Date.now() - 1000 * 60 * 45
        }
      ];
      localStorage.setItem(this.storageKeyItems, JSON.stringify(sampleItems));
    }
  }

  // --- Auth Operations ---
  loadCurrentUser() {
    const saved = localStorage.getItem(this.storageKeyAuth);
    if (saved) {
      try { return JSON.parse(saved); } catch (e) { return null; }
    }
    return null;
  }

  async login(email, password) {
    this.recordMetric("write");
    const normalizedEmail = email.trim().toLowerCase();
    if (!/^[^@\s]+@gachon\.ac\.kr$/.test(normalizedEmail)) {
      throw new Error("Only @gachon.ac.kr university emails are allowed.");
    }
    if (this.state.firebaseInitialized) {
      const result = await this.auth.signInWithEmailAndPassword(normalizedEmail, password);
      return result.user;
    }
    // Simulated cloud auth latency
    await this.delay(200);

    const user = {
      uid: "usr-" + Math.abs(this.hashCode(email)),
      email: normalizedEmail,
      displayName: normalizedEmail.split("@")[0],
      role: email.includes("admin") ? "Campus Admin" : "Student",
      campusId: "U-" + Math.floor(10000 + Math.random() * 90000),
      avatarUrl: `https://api.dicebear.com/7.x/bottts/svg?seed=${encodeURIComponent(email)}`
    };

    this.state.currentUser = user;
    localStorage.setItem(this.storageKeyAuth, JSON.stringify(user));
    this.notify("auth", user);
    this.broadcastMessage("AUTH_UPDATED", user);
    return user;
  }

  async loginAsGuest() {
    if (this.state.firebaseInitialized) {
      const result = await this.auth.signInAnonymously();
      const user = {
        uid: result.user.uid,
        email: "guest@campus.edu",
        displayName: "Campus Student",
        role: "Student"
      };
      this.state.currentUser = user;
      this.notify("auth", user);
      return user;
    }
    const guestEmail = "student." + Math.floor(100 + Math.random() * 899) + "@campus.edu";
    return this.login(guestEmail, "demo123");
  }

  async createAccount(email, password) {
    const normalizedEmail = email.trim().toLowerCase();
    if (!/^[^@\s]+@gachon\.ac\.kr$/.test(normalizedEmail)) {
      throw new Error("Only @gachon.ac.kr university emails are allowed.");
    }
    if (this.state.firebaseInitialized) {
      const result = await this.auth.createUserWithEmailAndPassword(normalizedEmail, password);
      return result.user;
    }
    return this.login(normalizedEmail, password);
  }

  async logout() {
    this.recordMetric("write");
    this.state.currentUser = null;
    localStorage.removeItem(this.storageKeyAuth);
    this.notify("auth", null);
    this.broadcastMessage("AUTH_UPDATED", null);
  }

  // --- Database (CRUD) Operations ---
  async getItems() {
    this.recordMetric("read");
    if (this.state.firebaseInitialized) {
      try {
        const snapshot = await this.db.collection("items").orderBy("createdAt", "desc").get();
        return snapshot.docs.map(doc => this.normalizeItem({ id: doc.id, ...doc.data() }));
      } catch (error) {
        const snapshot = await this.db.collection("items").get();
        return snapshot.docs
          .map(doc => this.normalizeItem({ id: doc.id, ...doc.data() }))
          .sort((left, right) => (right.createdAt || 0) - (left.createdAt || 0));
      }
    }
    await this.delay(60);
    return this.getItemsInternal();
  }

  getItemsInternal() {
    try {
      const data = localStorage.getItem(this.storageKeyItems);
      return data ? JSON.parse(data) : [];
    } catch (e) {
      return [];
    }
  }

  async createItem(itemData) {
    this.recordMetric("write");
    if (this.state.firebaseInitialized) {
      if (!this.state.currentUser || this.state.currentUser.isGuest) {
        throw new Error("Sign in with a @gachon.ac.kr email to create a listing.");
      }
      const id = "item-" + Date.now().toString(36) + "-" + Math.random().toString(36).substring(2, 6);
      const newItem = {
        id,
        ...itemData,
        imageUri: itemData.imageDataUrl || "",
        status: "Open",
        creatorId: this.state.currentUser.uid,
        createdAt: Date.now(),
        updatedAt: Date.now()
      };
      await this.db.collection("items").doc(id).set(newItem);
      return newItem;
    }
    await this.delay(180);

    const newItem = {
      id: "item-" + Date.now().toString(36) + "-" + Math.random().toString(36).substring(2, 6),
      title: itemData.title,
      category: itemData.category,
      type: itemData.type,
      status: "Open", // "Open" | "Claimed"
      location: itemData.location,
      date: itemData.date,
      contactName: itemData.contactName,
      contact: itemData.contact,
      description: itemData.description,
      imageDataUrl: itemData.imageDataUrl || "",
      creatorId: this.state.currentUser ? this.state.currentUser.uid : "anonymous",
      createdAt: Date.now(),
      updatedAt: Date.now()
    };

    const items = this.getItemsInternal();
    items.unshift(newItem);
    localStorage.setItem(this.storageKeyItems, JSON.stringify(items));

    this.notify("items", items);
    this.broadcastMessage("ITEMS_UPDATED", newItem);
    return newItem;
  }

  async updateItemStatus(itemId, newStatus) {
    this.recordMetric("write");
    if (this.state.firebaseInitialized) {
      await this.db.collection("items").doc(itemId).update({ status: newStatus, updatedAt: Date.now() });
      return { id: itemId, status: newStatus };
    }
    await this.delay(120);

    const items = this.getItemsInternal();
    const item = items.find(i => i.id === itemId);
    if (!item) throw new Error("Item not found");

    item.status = newStatus;
    item.updatedAt = Date.now();

    localStorage.setItem(this.storageKeyItems, JSON.stringify(items));
    this.notify("items", items);
    this.broadcastMessage("ITEMS_UPDATED", item);
    return item;
  }

  async deleteItem(itemId) {
    this.recordMetric("write");
    if (this.state.firebaseInitialized) {
      await this.db.collection("items").doc(itemId).delete();
      return true;
    }
    await this.delay(120);

    let items = this.getItemsInternal();
    items = items.filter(i => i.id !== itemId);
    localStorage.setItem(this.storageKeyItems, JSON.stringify(items));

    this.notify("items", items);
    this.broadcastMessage("ITEMS_UPDATED", { deletedId: itemId });
    return true;
  }

  // --- Real-time Chat Operations ---
  async getChatMessages(itemId) {
    this.recordMetric("read");
    if (this.state.firebaseInitialized) {
      const snapshot = await this.chatMessages(itemId)
        .orderBy("at", "asc").get();
      return snapshot.docs.map(doc => {
        const data = doc.data();
        return { id: doc.id, ...data, timestamp: data.timestamp || data.at };
      });
    }
    await this.delay(50);
    const chats = this.loadChatsInternal();
    return chats[itemId] || [];
  }

  subscribeToChat(itemId, callback) {
    if (!this.state.firebaseInitialized) return () => {};
    if (!this.state.currentUser || this.state.currentUser.isGuest) return () => {};
    return this.chatMessages(itemId)
      .orderBy("at", "asc")
      .onSnapshot(snapshot => {
        callback(snapshot.docs.map(doc => {
          const data = doc.data();
          return { id: doc.id, ...data, timestamp: data.timestamp || data.at };
        }));
      });
  }

  loadChatsInternal() {
    try {
      const data = localStorage.getItem(this.storageKeyChats);
      return data ? JSON.parse(data) : {};
    } catch (e) {
      return {};
    }
  }

  async sendMessage(itemId, messageData) {
    this.recordMetric("write");
    if (this.state.firebaseInitialized) {
      if (!this.state.currentUser || this.state.currentUser.isGuest) {
        throw new Error("Sign in with a @gachon.ac.kr email to use chat.");
      }
      const newMsg = {
        senderId: this.state.currentUser ? this.state.currentUser.uid : "guest-user",
        senderName: messageData.senderName || (this.state.currentUser ? this.state.currentUser.displayName : "Student"),
        role: messageData.role || "you",
        text: messageData.text,
        timestamp: Date.now(),
        at: Date.now()
      };
      const ref = await this.chatMessages(itemId).add(newMsg);
      return { id: ref.id, ...newMsg };
    }
    await this.delay(100);

    const chats = this.loadChatsInternal();
    if (!chats[itemId]) {
      chats[itemId] = [];
    }

    const newMsg = {
      id: "msg-" + Date.now() + "-" + Math.random().toString(36).substring(2, 5),
      senderId: this.state.currentUser ? this.state.currentUser.uid : "guest-user",
      senderName: messageData.senderName || (this.state.currentUser ? this.state.currentUser.displayName : "Student"),
      role: messageData.role || "you", // "you" | "owner" | "system"
      text: messageData.text,
      timestamp: Date.now()
    };

    chats[itemId].push(newMsg);
    localStorage.setItem(this.storageKeyChats, JSON.stringify(chats));

    this.notify("chats", { itemId, message: newMsg, all: chats[itemId] });
    this.broadcastMessage("CHAT_UPDATED", { itemId, message: newMsg, all: chats[itemId] });

    return newMsg;
  }

  chatMessages(itemId) {
    return this.db.collection("chats").doc(itemId)
      .collection("users").doc(this.state.currentUser.uid).collection("messages");
  }

  // --- Cloud Storage ---
  async uploadImage(file) {
    this.recordMetric("write");
    if (this.state.firebaseInitialized) {
      if (!file) return "";
      const ref = this.storage.ref().child(`items/${Date.now()}-${file.name}`);
      const snapshot = await ref.put(file);
      return snapshot.ref.getDownloadURL();
    }
    return new Promise((resolve, reject) => {
      if (!file) {
        resolve("");
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        reject(new Error("File size exceeds cloud storage limit (5MB)"));
        return;
      }

      const reader = new FileReader();
      reader.onload = () => {
        // Simulates CDN upload & returns object URL/Base64
        resolve(reader.result);
      };
      reader.onerror = () => reject(new Error("Failed to process image"));
      reader.readAsDataURL(file);
    });
  }

  // --- Realtime Subscription ---
  subscribe(channel, callback) {
    if (this.subscribers[channel]) {
      this.subscribers[channel].push(callback);
    }
    return () => {
      this.subscribers[channel] = this.subscribers[channel].filter(cb => cb !== callback);
    };
  }

  notify(channel, payload) {
    if (this.subscribers[channel]) {
      this.subscribers[channel].forEach(cb => {
        try { cb(payload); } catch (e) { console.error(e); }
      });
    }
  }

  broadcastMessage(type, payload) {
    if (this.broadcastChannel) {
      try {
        this.broadcastChannel.postMessage({ type, payload });
      } catch (e) {
        console.warn("Broadcast failed:", e);
      }
    }
  }

  // --- Cloud Metrics & Telemetry ---
  recordMetric(type) {
    if (type === "read") this.state.readsCount++;
    if (type === "write") this.state.writesCount++;
    this.notify("status", this.getStatusInfo());
  }

  getStatusInfo() {
    return {
      mode: this.state.mode,
      status: this.state.status,
      latencyMs: this.state.latencyMs,
      region: this.state.cloudRegion,
      reads: this.state.readsCount,
      writes: this.state.writesCount,
      currentUser: this.state.currentUser
    };
  }

  // --- Utilities ---
  delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  hashCode(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = ((hash << 5) - hash) + str.charCodeAt(i);
      hash |= 0;
    }
    return hash;
  }
}

// Global Cloud Instance
window.CampusCloud = new CloudManager();
