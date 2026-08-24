/**
 * תגובות – impersonation, empty text, and who may delete what.
 */
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, addDoc, collection, deleteDoc, doc, setDoc, updateDoc } from 'firebase/firestore';
import {
  FRIEND, OWNER, POLL_ID, STRANGER,
  as, makeTestEnv, seed,
} from './helpers.mjs';

const withComments = async (db) => {
  await db.doc(`polls/${POLL_ID}/comments/c_friend`).set({
    userId: FRIEND, userName: 'noa', text: 'אני בעד A', createdAt: Timestamp.now(),
  });
};

const comment = (userId, text = 'נראה טוב') => ({
  userId, userName: 'noa', text, createdAt: Timestamp.now(),
});

describe('תגובות', () => {
  let env;
  before(async () => { env = await makeTestEnv('comments'); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => { await seed(env, withComments); });

  it('a friend in the audience may leave a comment', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(
      addDoc(collection(db, `polls/${POLL_ID}/comments`), comment(FRIEND))
    );
  });

  it('writing a comment in somebody else’s name is rejected', async () => {
    const db = as(env, FRIEND);
    await assertFails(
      addDoc(collection(db, `polls/${POLL_ID}/comments`), comment(OWNER))
    );
  });

  it('an empty comment is rejected', async () => {
    const db = as(env, FRIEND);
    await assertFails(
      addDoc(collection(db, `polls/${POLL_ID}/comments`), comment(FRIEND, ''))
    );
  });

  it('a comment longer than 500 characters is rejected', async () => {
    const db = as(env, FRIEND);
    await assertFails(
      addDoc(collection(db, `polls/${POLL_ID}/comments`), comment(FRIEND, 'א'.repeat(501)))
    );
  });

  it('somebody outside the audience may not comment', async () => {
    const db = as(env, STRANGER);
    await assertFails(
      addDoc(collection(db, `polls/${POLL_ID}/comments`), comment(STRANGER))
    );
  });

  it('a comment may not be edited after it is posted', async () => {
    const db = as(env, FRIEND);
    await assertFails(
      updateDoc(doc(db, `polls/${POLL_ID}/comments/c_friend`), { text: 'שיניתי דעתי' })
    );
  });

  it('the author may delete their own comment', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(deleteDoc(doc(db, `polls/${POLL_ID}/comments/c_friend`)));
  });

  it('a stranger may not delete a comment that is not theirs', async () => {
    const db = as(env, STRANGER);
    await assertFails(deleteDoc(doc(db, `polls/${POLL_ID}/comments/c_friend`)));
  });

  it('the poll owner may moderate any comment on their poll', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(deleteDoc(doc(db, `polls/${POLL_ID}/comments/c_friend`)));
  });
});
