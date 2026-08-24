/**
 * Shared setup for the security-rules tests.
 *
 * Every suite talks to the Firestore emulator with the real `firestore.rules`
 * loaded, so what these tests prove is exactly what the deployed rules do.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { Timestamp } from 'firebase/firestore';

const here = dirname(fileURLToPath(import.meta.url));
const rulesPath = resolve(here, '..', '..', 'firestore.rules');

/** Uids used across the suites, so the seeded data reads the same everywhere. */
export const OWNER = 'uid_owner';
export const FRIEND = 'uid_friend';
export const OTHER_FRIEND = 'uid_friend2';
export const STRANGER = 'uid_stranger';

export const POLL_ID = 'poll_1';
export const CLOSED_POLL_ID = 'poll_closed';
/** A poll the test user is not allowed to see – needed to prove reads are blocked. */
export const PRIVATE_POLL_ID = 'poll_private';

export function hoursFromNow(hours) {
  return Timestamp.fromMillis(Date.now() + hours * 3600 * 1000);
}

/**
 * Each suite file runs in its own process, so they get their own project id –
 * otherwise one suite's `clearFirestore()` would wipe the fixture another suite
 * is in the middle of using.
 */
export async function makeTestEnv(suite) {
  return initializeTestEnvironment({
    projectId: `demo-snapvote-${suite}`,
    firestore: {
      rules: readFileSync(rulesPath, 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
}

/** A signed-in Firestore handle for [uid]. */
export function as(env, uid) {
  return env.authenticatedContext(uid).firestore();
}

/** A Firestore handle for a visitor who has not logged in. */
export function asAnon(env) {
  return env.unauthenticatedContext().firestore();
}

export function pollDoc({
  ownerId = OWNER,
  visibleTo = [OWNER, FRIEND],
  deadline = hoursFromNow(24),
  mode = 'SINGLE',
  voteCount = 0,
  tally = { img_0: 0, img_1: 0 },
  images = [
    { id: 'img_0', label: 'A', thumb: 'AAAA' },
    { id: 'img_1', label: 'B', thumb: 'BBBB' },
  ],
} = {}) {
  return {
    question: 'איזו תמונה עדיפה?',
    ownerId,
    ownerName: 'hila',
    images,
    mode,
    createdAt: Timestamp.now(),
    deadline,
    visibleTo,
    voteCount,
    tally,
    imagesDeleted: false,
  };
}

/**
 * Puts the standard fixture in place with the rules switched off: one open poll
 * owned by OWNER and visible to FRIEND, one already-closed poll, and one poll
 * that only a stranger can see.
 */
export async function seed(env, extra) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();

    await db.doc(`users/${OWNER}`).set({
      username: 'hila', usernameLower: 'hila', email: 'hila@example.com',
      pollsCreated: 1, votesGiven: 0,
    });
    await db.doc(`users/${FRIEND}`).set({
      username: 'noa', usernameLower: 'noa', email: 'noa@example.com',
      pollsCreated: 0, votesGiven: 0,
    });
    await db.doc(`users/${STRANGER}`).set({
      username: 'zara', usernameLower: 'zara', email: 'zara@example.com',
      pollsCreated: 0, votesGiven: 0,
    });
    await db.doc('usernames/hila').set({ uid: OWNER, username: 'hila' });
    await db.doc('usernames/noa').set({ uid: FRIEND, username: 'noa' });

    await db.doc(`users/${OWNER}/friends/${FRIEND}`)
      .set({ username: 'noa', since: Timestamp.now() });
    await db.doc(`users/${FRIEND}/friends/${OWNER}`)
      .set({ username: 'hila', since: Timestamp.now() });

    await db.doc(`polls/${POLL_ID}`).set(pollDoc());
    await db.doc(`polls/${POLL_ID}/images/img_0`).set({ data: 'full-image-0' });
    await db.doc(`polls/${POLL_ID}/images/img_1`).set({ data: 'full-image-1' });

    await db.doc(`polls/${CLOSED_POLL_ID}`)
      .set(pollDoc({ deadline: hoursFromNow(-1) }));

    await db.doc(`polls/${PRIVATE_POLL_ID}`)
      .set(pollDoc({ ownerId: STRANGER, visibleTo: [STRANGER] }));

    if (extra) await extra(db);
  });
}
