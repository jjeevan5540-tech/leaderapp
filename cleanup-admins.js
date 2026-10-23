/**
 * ONE-TIME CLEANUP & SETUP SCRIPT (Run locally with Node.js)
 * 
 * Instructions:
 * 1. Export a backup of your Firestore database first from the Firebase Console.
 * 2. Install firebase-admin and bcrypt: npm install firebase-admin bcrypt
 * 3. Place your serviceAccountKey.json in this directory.
 * 4. Run: node cleanup-admins.js
 */

const admin = require('firebase-admin');
const bcrypt = require('bcrypt');

// Initialize Firebase Admin SDK
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function cleanAndSetupAdmin() {
  const collectionName = 'admins';
  console.log(`Scanning collection "${collectionName}" for deletion...`);

  const snapshot = await db.collection(collectionName).get();
  console.log(`Found ${snapshot.size} documents in "${collectionName}". Deleting...`);

  const batch = db.batch();
  snapshot.docs.forEach((doc) => {
    batch.delete(doc.ref);
  });
  await batch.commit();
  console.log(`Successfully deleted ${snapshot.size} old documents from "${collectionName}".`);

  // Create new admin with hashed password
  const newMobile = '8660982471';
  const plainPassword = 'VN@1234';
  const saltRounds = 10;
  const passwordHash = await bcrypt.hash(plainPassword, saltRounds);

  const newAdminData = {
    mobile: newMobile,
    passwordHash: passwordHash,
    role: 'admin',
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  };

  const newDocRef = db.collection(collectionName).doc(newMobile);
  await newDocRef.set(newAdminData);
  console.log(`Successfully created new admin document in "${collectionName}" with mobile: ${newMobile}`);
}

cleanAndSetupAdmin()
  .then(() => {
    console.log('Cleanup and setup completed successfully.');
    process.exit(0);
  })
  .catch((error) => {
    console.error('Error during cleanup and setup:', error);
    process.exit(1);
  });
