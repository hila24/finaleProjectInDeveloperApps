/**
 * מי הצביע – who is allowed to see the individual votes.
 */
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, collection, deleteDoc, doc, getDoc, getDocs } from 'firebase/firestore';
import {
  FRIEND, OTHER_FRIEND, OWNER, POLL_ID, STRANGER,
  as, makeTestEnv, seed,
} from './helpers.mjs';

/** Two votes already on the poll, so there is something to read. */
const withVotes = async (db) => {
  await db.doc(`polls/${POLL_ID}/votes/${FRIEND}`).set({
    userName: 'noa', choiceImageId: 'img_0', ratings: {}, createdAt: Timestamp.now(),
  });
  await db.doc(`polls/${POLL_ID}/votes/${OTHER_FRIEND}`).set({
    userName: 'tal', choiceImageId: 'img_1', ratings: {}, createdAt: Timestamp.now(),
  });
};

describe('מי הצביע', () => {
  let env;
  before(async () => { env = await makeTestEnv('voters'); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => { await seed(env, withVotes); });

  it('the poll owner may read every vote – this is the "מי הצביע" screen', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(getDocs(collection(db, `polls/${POLL_ID}/votes`)));
  });

  it('a voter may read their own vote, which is how the feed knows they voted', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(getDoc(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`)));
  });

  it('a voter may not read what another person voted', async () => {
    const db = as(env, FRIEND);
    await assertFails(getDoc(doc(db, `polls/${POLL_ID}/votes/${OTHER_FRIEND}`)));
  });

  it('a voter may not list the whole vote collection', async () => {
    const db = as(env, FRIEND);
    await assertFails(getDocs(collection(db, `polls/${POLL_ID}/votes`)));
  });

  it('somebody outside the audience may not read the votes at all', async () => {
    const db = as(env, STRANGER);
    await assertFails(getDoc(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`)));
  });

  it('the owner may remove a vote from their own poll', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(deleteDoc(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`)));
  });

  it('a voter may not delete their own vote to get a second try', async () => {
    const db = as(env, FRIEND);
    await assertFails(deleteDoc(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`)));
  });
});
