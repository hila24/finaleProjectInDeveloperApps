/**
 * חברים – mutual friendship, and the privacy of the feed that rests on it.
 */
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, orderBy, query, setDoc, updateDoc, where,
} from 'firebase/firestore';
import { Timestamp } from 'firebase/firestore';
import {
  FRIEND, OWNER, POLL_ID, PRIVATE_POLL_ID, STRANGER,
  as, makeTestEnv, pollDoc, seed,
} from './helpers.mjs';

describe('חברים', () => {
  let env;
  before(async () => { env = await makeTestEnv('friends'); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => { await seed(env); });

  it('I may add a friend to my own list', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(
      setDoc(doc(db, `users/${OWNER}/friends/${STRANGER}`), {
        username: 'zara', since: Timestamp.now(),
      })
    );
  });

  it('I may write the mirrored document on the other person’s list – friendship is mutual', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(
      setDoc(doc(db, `users/${STRANGER}/friends/${OWNER}`), {
        username: 'hila', since: Timestamp.now(),
      })
    );
  });

  it('an outsider may not wire two other people together', async () => {
    const db = as(env, STRANGER);
    await assertFails(
      setDoc(doc(db, `users/${OWNER}/friends/${FRIEND}`), {
        username: 'noa', since: Timestamp.now(),
      })
    );
  });

  it('a friendship record may not be edited after the fact', async () => {
    const db = as(env, OWNER);
    await assertFails(
      updateDoc(doc(db, `users/${OWNER}/friends/${FRIEND}`), { username: 'renamed' })
    );
  });

  it('I may read my own friends list', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(getDocs(collection(db, `users/${OWNER}/friends`)));
  });

  it('I may not read somebody else’s friends list', async () => {
    const db = as(env, STRANGER);
    await assertFails(getDocs(collection(db, `users/${OWNER}/friends`)));
  });

  it('I may remove a friend from my own list', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(deleteDoc(doc(db, `users/${OWNER}/friends/${FRIEND}`)));
  });

  it('the other party may remove the mirrored record, so unfriending works both ways', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(deleteDoc(doc(db, `users/${OWNER}/friends/${FRIEND}`)));
  });

  it('the owner may read their own poll', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(getDoc(doc(db, `polls/${POLL_ID}`)));
  });

  it('a friend listed in visibleTo may read the poll', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(getDoc(doc(db, `polls/${POLL_ID}`)));
  });

  it('somebody who is not a friend gets PERMISSION_DENIED on the poll itself', async () => {
    const db = as(env, STRANGER);
    await assertFails(getDoc(doc(db, `polls/${POLL_ID}`)));
  });

  it('the feed query – visibleTo contains me, ordered by deadline – is allowed', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(getDocs(query(
      collection(db, 'polls'),
      where('visibleTo', 'array-contains', FRIEND),
      where('deadline', '>', Timestamp.now()),
      orderBy('deadline', 'asc'),
    )));
  });

  it('asking for every poll instead of only mine is rejected', async () => {
    const db = as(env, FRIEND);
    await assertFails(getDocs(collection(db, 'polls')));
  });

  it('"הסקרים שלי" – asking by ownerId alone – is allowed', async () => {
    // Rules are not filters: a query is allowed only when its own constraints prove
    // the rule. This query says nothing about visibleTo, so it passes only because
    // the read rule spells ownership out as a branch of its own. Without that the
    // whole profile query is denied and the screen silently falls back to cache.
    const db = as(env, OWNER);
    await assertSucceeds(getDocs(query(
      collection(db, 'polls'), where('ownerId', '==', OWNER)
    )));
  });

  it('I may read my own poll even if visibleTo somehow does not list me', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc('polls/poll_orphan').set(
        pollDoc({ ownerId: OWNER, visibleTo: ['someone_else'] })
      );
    });
    const db = as(env, OWNER);
    await assertSucceeds(getDocs(query(
      collection(db, 'polls'), where('ownerId', '==', OWNER)
    )));
  });

  it('asking by ownerId for somebody else is still rejected', async () => {
    const db = as(env, FRIEND);
    await assertFails(getDocs(query(
      collection(db, 'polls'), where('ownerId', '==', OWNER)
    )));
  });

  it('the archive query – finished polls I was allowed to see – is allowed', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(getDocs(query(
      collection(db, 'polls'),
      where('visibleTo', 'array-contains', FRIEND),
      where('deadline', '<=', Timestamp.now()),
      orderBy('deadline', 'desc'),
    )));
  });

  it('the archive query cannot be pointed at somebody else’s polls', async () => {
    const db = as(env, FRIEND);
    await assertFails(getDocs(query(
      collection(db, 'polls'),
      where('visibleTo', 'array-contains', STRANGER),
      where('deadline', '<=', Timestamp.now()),
      orderBy('deadline', 'desc'),
    )));
  });

  it('the full-size images of a stranger’s poll cannot be read', async () => {
    const db = as(env, FRIEND);
    await assertFails(getDocs(collection(db, `polls/${PRIVATE_POLL_ID}/images`)));
  });
});
