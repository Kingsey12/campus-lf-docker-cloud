/**
 * Campus Lost & Found Cloud Application Logic
 * Integrates with CampusCloud manager for distributed real-time sync.
 */

document.addEventListener("DOMContentLoaded", () => {
  const cloud = window.CampusCloud;

  // --- DOM Elements ---
  const itemForm = document.getElementById("item-form");
  const itemList = document.getElementById("item-list");
  const searchInput = document.getElementById("search-input");
  const filterCategory = document.getElementById("filter-category");
  const filterType = document.getElementById("filter-type");
  const filterStatus = document.getElementById("filter-status");
  const quickCategoryPills = document.getElementById("quick-category-pills");

  const statLostCount = document.getElementById("stat-lost-count");
  const statFoundCount = document.getElementById("stat-found-count");
  const statResolvedCount = document.getElementById("stat-resolved-count");
  const statCloudLatency = document.getElementById("stat-cloud-latency");

  const btnCloudStatus = document.getElementById("btn-cloud-status");
  const cloudPillText = document.getElementById("cloud-pill-text");
  const btnOpenCloudArch = document.getElementById("btn-open-cloud-arch");
  const modalCloudArch = document.getElementById("modal-cloud-arch");
  const btnCloseCloudArch = document.getElementById("btn-close-cloud-arch");
  const btnModalCloseFooter = document.getElementById("btn-modal-close-footer");

  const modalAuth = document.getElementById("modal-auth");
  const btnOpenAuth = document.getElementById("btn-open-auth");
  const btnCloseAuth = document.getElementById("btn-close-auth");
  const authForm = document.getElementById("auth-form");
  const btnGuestLogin = document.getElementById("btn-guest-login");
  const btnCreateAccount = document.getElementById("btn-create-account");
  const authFormContainer = document.getElementById("auth-form-container");
  const authProfileContainer = document.getElementById("auth-profile-container");
  const authBtnLabel = document.getElementById("auth-btn-label");
  const activeUserBadge = document.getElementById("active-user-badge");
  const btnLogout = document.getElementById("btn-logout");

  const modalChat = document.getElementById("modal-chat");
  const chatItemTitle = document.getElementById("chat-item-title");
  const btnCloseChat = document.getElementById("btn-close-chat");
  const chatThread = document.getElementById("chat-thread");
  const chatForm = document.getElementById("chat-form");
  const chatInput = document.getElementById("chat-input");

  const imageFileInput = document.getElementById("image-file");
  const imagePreviewWrap = document.getElementById("image-preview-wrap");
  const imagePreview = document.getElementById("image-preview");
  const btnRemoveImage = document.getElementById("btn-remove-image");

  // State
  let currentItems = [];
  let currentUploadedBase64 = "";
  let activeChatItemId = null;
  let unsubscribeChat = null;

  // Set default date to today
  const dateInput = document.getElementById("date");
  if (dateInput) {
    dateInput.value = new Date().toISOString().split("T")[0];
  }

  // --- Initial Data Load ---
  async function loadInitialData() {
    try {
      currentItems = await cloud.getItems();
      updateStatsAndFeed();
      updateAuthUI(cloud.state.currentUser);
      updateTelemetryUI(cloud.getStatusInfo());
    } catch (error) {
      console.error("Initial cloud load failed:", error);
      currentItems = [];
      updateStatsAndFeed();
      showToast("Could not load cloud listings. Refresh the page.", "warning");
    }
  }

  // --- Subscriptions to Realtime Cloud Events ---
  cloud.subscribe("items", (updatedItems) => {
    currentItems = updatedItems;
    updateStatsAndFeed();
    showToast("Cloud synchronized: items updated", "info");
  });

  cloud.subscribe("status", (statusInfo) => {
    updateTelemetryUI(statusInfo);
    if (statusInfo.status === "offline") {
      showToast("Cloud connection failed. Check Firebase configuration.", "warning");
    }
  });

  cloud.subscribe("auth", (user) => {
    updateAuthUI(user);
  });

  cloud.subscribe("chats", (chatPayload) => {
    if (activeChatItemId && chatPayload.itemId === activeChatItemId) {
      renderChatMessages(chatPayload.all);
    }
  });

  // --- Stats and Feed Rendering ---
  function updateStatsAndFeed() {
    const lostCount = currentItems.filter(i => i.type === "Lost" && i.status === "Open").length;
    const foundCount = currentItems.filter(i => i.type === "Found" && i.status === "Open").length;
    const resolvedCount = currentItems.filter(i => i.status === "Claimed").length;

    statLostCount.textContent = lostCount;
    statFoundCount.textContent = foundCount;
    statResolvedCount.textContent = resolvedCount;

    renderFeed();
  }

  function renderFeed() {
    const query = (searchInput.value || "").trim().toLowerCase();
    const cat = filterCategory.value;
    const typ = filterType.value;
    const stat = filterStatus.value;

    const filtered = currentItems.filter((item) => {
      const matchQuery = !query || 
        (item.title && item.title.toLowerCase().includes(query)) ||
        (item.location && item.location.toLowerCase().includes(query)) ||
        (item.category && item.category.toLowerCase().includes(query)) ||
        (item.description && item.description.toLowerCase().includes(query));

      const matchCategory = cat === "All" || item.category === cat;
      const matchType = typ === "All" || item.type === typ;
      const matchStatus = stat === "All" || item.status === stat;

      return matchQuery && matchCategory && matchType && matchStatus;
    });

    if (filtered.length === 0) {
      itemList.innerHTML = `
        <div class="empty-state">
          <div class="empty-state-icon">🔎</div>
          <h3>No items found</h3>
          <p>No lost or found reports match the current filters. Try changing filters or post a new item.</p>
        </div>
      `;
      return;
    }

    itemList.innerHTML = "";
    filtered.forEach((item) => {
      const card = document.createElement("article");
      const isClaimed = item.status === "Claimed";
      card.className = `item-card ${isClaimed ? "claimed" : ""}`;

      const typeBadgeClass = item.type === "Lost" ? "badge-lost" : "badge-found";
      const icon = item.type === "Lost" ? "🔍" : "🎁";

      const timeAgo = formatTimeAgo(item.createdAt);

      card.innerHTML = `
        <div class="item-thumb-wrap">
          <div class="item-badge-group">
            <span class="badge ${typeBadgeClass}">${icon} ${escapeHtml(item.type)}</span>
            <span class="badge badge-category">${escapeHtml(item.category)}</span>
          </div>
          ${isClaimed ? '<span class="badge badge-resolved">✓ Resolved</span>' : ''}
          ${
            item.imageDataUrl
              ? `<img src="${escapeHtml(item.imageDataUrl)}" alt="${escapeHtml(item.title)}" loading="lazy" />`
              : `<div class="item-thumb-placeholder">${icon}</div>`
          }
        </div>

        <div class="item-content">
          <h4 class="item-title">${escapeHtml(item.title)}</h4>
          <p class="item-desc">${escapeHtml(item.description)}</p>

          <div class="item-meta-list">
            <div class="meta-row">
              <strong>📍 Seen:</strong> <span>${escapeHtml(item.location)}</span>
            </div>
            <div class="meta-row">
              <strong>📅 Date:</strong> <span>${escapeHtml(item.date)} (${timeAgo})</span>
            </div>
            <div class="meta-row">
              <strong>👤 Posted by:</strong> <span>${escapeHtml(item.contactName)}</span>
            </div>
          </div>

          <div class="item-actions">
            <button class="btn btn-secondary btn-sm" data-action="contact" data-id="${item.id}">
              📞 Contact
            </button>
            <button class="btn btn-primary btn-sm" data-action="chat" data-id="${item.id}">
              💬 Cloud Chat
            </button>
            <button class="btn btn-ghost btn-sm btn-full" data-action="toggle-status" data-id="${item.id}">
              ${isClaimed ? "↩️ Reopen Report" : "✅ Mark as Resolved (Recovered)"}
            </button>
          </div>
        </div>
      `;

      itemList.appendChild(card);
    });
  }

  // --- Item Form Submission ---
  itemForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    const submitBtn = document.getElementById("btn-submit-item");
    const submitText = document.getElementById("submit-btn-text");
    submitBtn.disabled = true;
    submitText.textContent = "Publishing to Cloud...";

    try {
      const typeRadio = document.querySelector('input[name="item-type"]:checked');
      const itemType = typeRadio ? typeRadio.value : "Lost";

      const newItemData = {
        title: document.getElementById("title").value.trim(),
        category: document.getElementById("category").value,
        type: itemType,
        location: document.getElementById("location").value.trim(),
        date: document.getElementById("date").value,
        contactName: document.getElementById("contactName").value.trim(),
        contact: document.getElementById("contact").value.trim(),
        description: document.getElementById("description").value.trim(),
        imageDataUrl: currentUploadedBase64
      };

      await cloud.createItem(newItemData);

      // Reset form
      itemForm.reset();
      clearImagePreview();
      const dateInput = document.getElementById("date");
      if (dateInput) dateInput.value = new Date().toISOString().split("T")[0];

      // Auto-fill contact if logged in
      if (cloud.state.currentUser) {
        document.getElementById("contactName").value = cloud.state.currentUser.displayName;
        document.getElementById("contact").value = cloud.state.currentUser.email;
      }

      showToast("Item published to Cloud Database successfully!", "success");
    } catch (err) {
      console.error(err);
      showToast("Failed to publish item: " + err.message, "warning");
    } finally {
      submitBtn.disabled = false;
      submitText.textContent = "Publish to Cloud Database";
    }
  });

  // --- Image Upload Handling ---
  imageFileInput.addEventListener("change", async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    try {
      showToast("Uploading media to Cloud Object Storage...", "info");
      currentUploadedBase64 = await cloud.uploadImage(file);
      imagePreview.src = currentUploadedBase64;
      imagePreviewWrap.style.display = "block";
      showToast("Media uploaded to Cloud Storage", "success");
    } catch (err) {
      showToast(err.message, "warning");
      clearImagePreview();
    }
  });

  btnRemoveImage.addEventListener("click", (e) => {
    e.stopPropagation();
    clearImagePreview();
  });

  function clearImagePreview() {
    currentUploadedBase64 = "";
    imageFileInput.value = "";
    imagePreview.src = "";
    imagePreviewWrap.style.display = "none";
  }

  // --- Feed Action Clicks ---
  itemList.addEventListener("click", async (e) => {
    const target = e.target.closest("button");
    if (!target) return;

    const action = target.dataset.action;
    const itemId = target.dataset.id;
    if (!action || !itemId) return;

    const item = currentItems.find(i => i.id === itemId);
    if (!item) return;

    if (action === "contact") {
      alert(`Contact Details for "${item.title}":\n\nName: ${item.contactName}\nContact: ${item.contact}\nLast Seen: ${item.location}`);
    } else if (action === "chat") {
      if (!cloud.state.currentUser || cloud.state.currentUser.isGuest) {
        showToast("Sign in with your @gachon.ac.kr email to use chat.", "warning");
        modalAuth.showModal();
        return;
      }
      openChatModal(item);
    } else if (action === "toggle-status") {
      const newStatus = item.status === "Claimed" ? "Open" : "Claimed";
      await cloud.updateItemStatus(itemId, newStatus);
      showToast(
        newStatus === "Claimed" ? "Item marked as Recovered / Resolved!" : "Item report reopened.",
        "success"
      );
    }
  });

  // --- Realtime Chat Modal ---
  async function openChatModal(item) {
    activeChatItemId = item.id;
    chatItemTitle.innerHTML = `<span>💬</span> Chat: ${escapeHtml(item.title)}`;
    modalChat.showModal();

    if (unsubscribeChat) unsubscribeChat();
    unsubscribeChat = cloud.subscribeToChat(item.id, (messages) => {
      renderChatMessages(messages);
    });

    const messages = await cloud.getChatMessages(item.id);
    renderChatMessages(messages);

    // Initial greeting if chat is empty
    if (messages.length === 0) {
      const greeting = {
        role: "system",
        text: `Encrypted cloud chat opened for "${item.title}". Messages are synchronized in real time.`,
        timestamp: Date.now()
      };
      renderChatMessages([greeting]);
    }
  }

  function renderChatMessages(messages) {
    chatThread.innerHTML = "";
    messages.forEach((msg) => {
      const bubble = document.createElement("div");
      bubble.className = `msg-bubble ${msg.role || "you"}`;

      const time = new Date(msg.timestamp || msg.at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

      bubble.innerHTML = `
        ${msg.senderName && msg.role !== "system" ? `<div class="msg-author">${escapeHtml(msg.senderName)}</div>` : ""}
        <div>${escapeHtml(msg.text)}</div>
        <div class="msg-time">${time}</div>
      `;
      chatThread.appendChild(bubble);
    });
    chatThread.scrollTop = chatThread.scrollHeight;
  }

  chatForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!activeChatItemId) return;

    const text = chatInput.value.trim();
    if (!text) return;

    chatInput.value = "";
    await cloud.sendMessage(activeChatItemId, {
      role: "you",
      text: text
    });

    // Simulated respondent for live presentation
    setTimeout(async () => {
      if (activeChatItemId) {
        await cloud.sendMessage(activeChatItemId, {
          role: "owner",
          senderName: "Item Poster (Cloud Verified)",
          text: "Thank you for reaching out via Campus Cloud! Please mention any unique distinguishing detail so we can safely hand over the item."
        });
      }
    }, 900);
  });

  btnCloseChat.addEventListener("click", () => {
    modalChat.close();
    if (unsubscribeChat) {
      unsubscribeChat();
      unsubscribeChat = null;
    }
    activeChatItemId = null;
  });

  // --- Search & Filters ---
  searchInput.addEventListener("input", renderFeed);
  filterCategory.addEventListener("change", renderFeed);
  filterType.addEventListener("change", renderFeed);
  filterStatus.addEventListener("change", renderFeed);

  quickCategoryPills.addEventListener("click", (e) => {
    const btn = e.target.closest(".pill-btn");
    if (!btn) return;

    quickCategoryPills.querySelectorAll(".pill-btn").forEach(b => b.classList.remove("active"));
    btn.classList.add("active");

    const category = btn.dataset.category;
    filterCategory.value = category;
    renderFeed();
  });

  // --- Cloud Architecture Inspector & Telemetry Modal ---
  btnOpenCloudArch.addEventListener("click", () => modalCloudArch.showModal());
  btnCloudStatus.addEventListener("click", () => modalCloudArch.showModal());
  btnCloseCloudArch.addEventListener("click", () => modalCloudArch.close());
  btnModalCloseFooter.addEventListener("click", () => modalCloudArch.close());

  // Cloud Inspector Tabs
  const cloudTabs = document.querySelectorAll(".cloud-tab-btn");
  const tabContents = document.querySelectorAll(".tab-content");

  cloudTabs.forEach((tabBtn) => {
    tabBtn.addEventListener("click", () => {
      cloudTabs.forEach(b => b.classList.remove("active"));
      tabContents.forEach(c => c.style.display = "none");

      tabBtn.classList.add("active");
      const targetTab = document.getElementById(tabBtn.dataset.tab);
      if (targetTab) targetTab.style.display = "block";
    });
  });

  function updateTelemetryUI(statusInfo) {
    if (!statusInfo) return;
    const latencyEl = document.getElementById("telemetry-latency");
    const readsEl = document.getElementById("telemetry-reads");
    const writesEl = document.getElementById("telemetry-writes");
    const statusEl = document.getElementById("telemetry-status");
    const regionEl = document.getElementById("telemetry-region");

    if (latencyEl) latencyEl.textContent = `${statusInfo.latencyMs} ms`;
    if (readsEl) readsEl.textContent = statusInfo.reads;
    if (writesEl) writesEl.textContent = statusInfo.writes;
    if (statusEl) statusEl.textContent = statusInfo.status.toUpperCase();
    if (regionEl) regionEl.textContent = statusInfo.region;

    statCloudLatency.textContent = `${statusInfo.latencyMs} ms`;
    cloudPillText.textContent = `Cloud: Online (${statusInfo.latencyMs}ms)`;
  }

  // --- Authentication Modal & Flow ---
  btnOpenAuth.addEventListener("click", () => modalAuth.showModal());
  btnCloseAuth.addEventListener("click", () => modalAuth.close());

  authForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = document.getElementById("auth-email").value.trim();
    const password = document.getElementById("auth-password").value.trim();

    try {
      const user = await cloud.login(email, password);
      showToast(`Welcome back, ${user.displayName}!`, "success");
      modalAuth.close();
    } catch (err) {
      showToast("Authentication failed: " + err.message, "warning");
    }
  });

  btnCreateAccount.addEventListener("click", async () => {
    const email = document.getElementById("auth-email").value.trim().toLowerCase();
    const password = document.getElementById("auth-password").value.trim();
    if (!/^[^@\s]+@gachon\.ac\.kr$/.test(email)) {
      showToast("Only @gachon.ac.kr university emails are allowed.", "warning");
      return;
    }
    if (password.length < 6) {
      showToast("Password must contain at least 6 characters.", "warning");
      return;
    }
    try {
      const user = await cloud.createAccount(email, password);
      showToast(`Account created for ${user.email}.`, "success");
      modalAuth.close();
    } catch (err) {
      showToast("Account creation failed: " + err.message, "warning");
    }
  });

  btnGuestLogin.addEventListener("click", async () => {
    try {
      const user = await cloud.loginAsGuest();
      showToast(`Demo Student session started as ${user.displayName}!`, "success");
      modalAuth.close();
    } catch (err) {
      showToast("Guest login failed", "warning");
    }
  });

  btnLogout.addEventListener("click", async () => {
    await cloud.logout();
    showToast("Signed out of Campus Cloud.", "info");
    modalAuth.close();
  });

  function updateAuthUI(user) {
    const uploaderCard = itemForm.closest(".uploader-card");
    if (user) {
      authBtnLabel.textContent = user.displayName;
      activeUserBadge.textContent = `${user.displayName} (${user.role})`;
      authFormContainer.style.display = "none";
      authProfileContainer.style.display = "block";
      if (uploaderCard) uploaderCard.style.display = user.isGuest ? "none" : "";

      document.getElementById("profile-name").textContent = user.displayName;
      document.getElementById("profile-email").textContent = user.email;
      document.getElementById("profile-role").textContent = user.role;
      document.getElementById("profile-avatar").src = user.avatarUrl;

      // Pre-fill item form
      const nameField = document.getElementById("contactName");
      const contactField = document.getElementById("contact");
      if (nameField && !nameField.value) nameField.value = user.displayName;
      if (contactField && !contactField.value) contactField.value = user.email;
    } else {
      authBtnLabel.textContent = "Sign In";
      activeUserBadge.textContent = "Guest Session";
      authFormContainer.style.display = "block";
      authProfileContainer.style.display = "none";
      if (uploaderCard) uploaderCard.style.display = "none";
    }
  }

  // --- Utilities ---
  function showToast(message, type = "info") {
    const container = document.getElementById("toast-container");
    const toast = document.createElement("div");
    toast.className = `toast ${type}`;

    let icon = "ℹ️";
    if (type === "success") icon = "✅";
    if (type === "warning") icon = "⚠️";

    toast.innerHTML = `<span>${icon}</span><span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = "0";
      toast.style.transform = "translateX(100%)";
      toast.style.transition = "all 0.3s ease";
      setTimeout(() => toast.remove(), 300);
    }, 3200);
  }

  function escapeHtml(str) {
    if (!str) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function formatTimeAgo(timestamp) {
    if (!timestamp) return "";
    const diffSec = Math.floor((Date.now() - timestamp) / 1000);
    if (diffSec < 60) return "just now";
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHours = Math.floor(diffMin / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}d ago`;
  }

  // Start app
  loadInitialData();
});
