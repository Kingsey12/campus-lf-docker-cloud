require('dotenv').config();

const fs = require('fs');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const admin = require('firebase-admin');

const app = express();
const port = Number(process.env.PORT || 3000);
const requireAuth = process.env.REQUIRE_AUTH !== 'false';

app.disable('x-powered-by');
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
app.use(express.json({ limit: '2mb' }));

let firebaseError = null;
let firestore = null;
let firebaseConfigured = false;

function initializeFirebase() {
  if (admin.apps.length > 0) {
    firestore = admin.firestore();
    return;
  }

  try {
    const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n');
    const serviceAccountFile = process.env.FIREBASE_SERVICE_ACCOUNT_FILE;

    if (serviceAccountFile && fs.existsSync(serviceAccountFile)) {
      const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountFile, 'utf8'));
      admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
      firestore = admin.firestore();
      firebaseConfigured = true;
      return;
    }

    const hasExplicitCredentials = process.env.FIREBASE_PROJECT_ID
      && process.env.FIREBASE_CLIENT_EMAIL
      && privateKey;

    if (!hasExplicitCredentials) {
      firebaseError = new Error('Firebase Admin credentials are not configured.');
      return;
    }

    admin.initializeApp({
      credential: admin.credential.cert({
        projectId: process.env.FIREBASE_PROJECT_ID,
        clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
        privateKey
      })
    });

    firestore = admin.firestore();
    firebaseConfigured = true;
  } catch (error) {
    firebaseError = error;
  }
}

initializeFirebase();

function getFirestore() {
  if (!firestore) {
    const error = new Error('Firebase Admin is not configured. Set the Firebase environment variables.');
    error.statusCode = 503;
    throw error;
  }
  return firestore;
}

function toItem(document) {
  return { id: document.id, ...document.data() };
}

function validateItemPayload(payload, partial = false) {
  const requiredFields = ['title', 'category', 'type'];
  if (!partial) {
    for (const field of requiredFields) {
      if (typeof payload[field] !== 'string' || payload[field].trim() === '') {
        return `${field} is required`;
      }
    }
  }

  if (payload.type !== undefined && !['Lost', 'Found'].includes(payload.type)) {
    return 'type must be Lost or Found';
  }
  return null;
}

async function authenticate(req, res, next) {
  if (!requireAuth) {
    return next();
  }

  const authorization = req.get('authorization') || '';
  if (!authorization.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'A Firebase ID token is required.' });
  }

  try {
    req.user = await admin.auth().verifyIdToken(authorization.slice(7));
    return next();
  } catch (error) {
    return res.status(401).json({ error: 'The Firebase ID token is invalid or expired.' });
  }
}

function canModifyItem(req, item) {
  if (!requireAuth) return true;
  return req.user && (req.user.uid === item.creatorId || req.user.admin === true);
}

app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'Campus L&F Backend',
    firebase: firebaseConfigured,
    authenticationRequired: requireAuth
  });
});

app.get('/api/items', async (req, res, next) => {
  try {
    const snapshot = await getFirestore().collection('items').orderBy('createdAt', 'desc').get();
    res.json(snapshot.docs.map(toItem));
  } catch (error) {
    next(error);
  }
});

app.get('/api/items/:id', async (req, res, next) => {
  try {
    const document = await getFirestore().collection('items').doc(req.params.id).get();
    if (!document.exists) return res.status(404).json({ error: 'Item not found.' });
    return res.json(toItem(document));
  } catch (error) {
    return next(error);
  }
});

app.post('/api/items', authenticate, async (req, res, next) => {
  try {
    const validationError = validateItemPayload(req.body);
    if (validationError) return res.status(400).json({ error: validationError });

    const db = getFirestore();
    const document = req.body.id
      ? db.collection('items').doc(req.body.id)
      : db.collection('items').doc();
    const item = {
      ...req.body,
      id: document.id,
      status: req.body.status || 'Open',
      creatorId: req.user?.uid || req.body.creatorId || null,
      createdAt: req.body.createdAt || Date.now()
    };
    await document.set(item);
    return res.status(201).json(item);
  } catch (error) {
    return next(error);
  }
});

app.put('/api/items/:id', authenticate, async (req, res, next) => {
  try {
    const validationError = validateItemPayload(req.body, true);
    if (validationError) return res.status(400).json({ error: validationError });

    const document = await getFirestore().collection('items').doc(req.params.id).get();
    if (!document.exists) return res.status(404).json({ error: 'Item not found.' });

    const currentItem = toItem(document);
    if (!canModifyItem(req, currentItem)) return res.status(403).json({ error: 'You cannot modify this item.' });

    const updates = { ...req.body, updatedAt: Date.now() };
    delete updates.id;
    await document.ref.set(updates, { merge: true });
    return res.json({ ...currentItem, ...updates, id: document.id });
  } catch (error) {
    return next(error);
  }
});

app.delete('/api/items/:id', authenticate, async (req, res, next) => {
  try {
    const document = await getFirestore().collection('items').doc(req.params.id).get();
    if (!document.exists) return res.status(404).json({ error: 'Item not found.' });

    if (!canModifyItem(req, document.data())) return res.status(403).json({ error: 'You cannot delete this item.' });
    await document.ref.delete();
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
});

app.use((error, req, res, next) => {
  console.error(error);
  const statusCode = error.statusCode || 500;
  return res.status(statusCode).json({ error: statusCode === 500 ? 'Internal server error.' : error.message });
});

const server = app.listen(port, '0.0.0.0', () => {
  console.log(`Campus L&F Backend listening on port ${port}`);
  if (firebaseError) console.warn(`Firebase initialization failed: ${firebaseError.message}`);
});

function shutdown(signal) {
  console.log(`${signal} received; shutting down gracefully.`);
  server.close(() => process.exit(0));
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
