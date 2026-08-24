/**
 * ספירת ההצבעות – the counters on the poll document itself.
 *
 * The vote document is write-once, but `voteCount` and `tally` live on the poll and
 * an audience member has to be able to move them. These tests pin down how far
 * that permission goes: one vote at a time, and only while the poll is open.
 */
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, doc, increment, runTransaction, setDoc, updateDoc } from 'firebase/firestore';
import {
  CLOSED_POLL_ID, FRIEND, OWNER, POLL_ID, STRANGER,
  as, makeTestEnv, seed,
} from './helpers.mjs';

describe('ספירת ההצבעות', () => {
  let env;
  before(async () => { env = await makeTestEnv('counters'); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => { await seed(env); });

  it('an audience member may register exactly one vote', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(updateDoc(doc(db, `polls/${POLL_ID}`), {
      voteCount: increment(1),
      'tally.img_0': increment(1),
    }));
  });

  it('the owner may still mark a finished poll as cleaned up', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(updateDoc(doc(db, `polls/${CLOSED_POLL_ID}`), {
      imagesDeleted: true,
      images: [
        { id: 'img_0', label: 'A', thumb: '' },
        { id: 'img_1', label: 'B', thumb: '' },
      ],
    }));
  });

  it('an audience member may not rewrite the question', async () => {
    const db = as(env, FRIEND);
    await assertFails(updateDoc(doc(db, `polls/${POLL_ID}`), {
      question: 'שאלה אחרת',
    }));
  });

  it('stuffing the ballot – more than one vote in a single write – is rejected', async () => {
    const db = as(env, FRIEND);
    await assertFails(updateDoc(doc(db, `polls/${POLL_ID}`), {
      voteCount: increment(100),
      'tally.img_0': increment(100),
    }));
  });

  it('moving the tally without registering a vote is rejected', async () => {
    const db = as(env, FRIEND);
    await assertFails(updateDoc(doc(db, `polls/${POLL_ID}`), {
      'tally.img_0': increment(50),
    }));
  });

  it('lowering the vote count is rejected', async () => {
    const db = as(env, FRIEND);
    await assertFails(updateDoc(doc(db, `polls/${POLL_ID}`), {
      voteCount: increment(-1),
    }));
  });

  it('the counters may not move once the deadline has passed', async () => {
    const db = as(env, FRIEND);
    await assertFails(updateDoc(doc(db, `polls/${CLOSED_POLL_ID}`), {
      voteCount: increment(1),
      'tally.img_0': increment(1),
    }));
  });

  it('a vote document cannot be created after the deadline either', async () => {
    const db = as(env, FRIEND);
    await assertFails(setDoc(doc(db, `polls/${CLOSED_POLL_ID}/votes/${FRIEND}`), {
      userName: 'noa', choiceImageId: 'img_0', ratings: {}, createdAt: Timestamp.now(),
    }));
  });

  // The two tests below replay exactly what PollRepository.castVote does, rather
  // than an approximation of it, so the hardened rules are checked against the
  // real write path the app uses.
  it('the app’s own vote transaction still goes through – single-choice mode', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(runTransaction(db, async (tx) => {
      const pollRef = doc(db, `polls/${POLL_ID}`);
      await tx.get(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`));
      await tx.get(pollRef);
      tx.set(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`), {
        userName: 'noa', choiceImageId: 'img_0', ratings: {}, createdAt: Timestamp.now(),
      });
      tx.update(pollRef, { voteCount: increment(1), 'tally.img_0': increment(1) });
      tx.update(doc(db, `users/${FRIEND}`), { votesGiven: increment(1) });
    }));
  });

  it('the app’s own vote transaction still goes through – star-rating mode', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(runTransaction(db, async (tx) => {
      const pollRef = doc(db, `polls/${POLL_ID}`);
      await tx.get(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`));
      await tx.get(pollRef);
      tx.set(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`), {
        userName: 'noa', choiceImageId: '',
        ratings: { img_0: 5, img_1: 3 }, createdAt: Timestamp.now(),
      });
      // one voter, but several stars per image – voteCount still moves by one
      tx.update(pollRef, {
        voteCount: increment(1),
        'tally.img_0': increment(5),
        'tally.img_1': increment(3),
      });
      tx.update(doc(db, `users/${FRIEND}`), { votesGiven: increment(1) });
    }));
  });

  it('somebody outside the audience may not touch the counters', async () => {
    const db = as(env, STRANGER);
    await assertFails(updateDoc(doc(db, `polls/${POLL_ID}`), {
      voteCount: increment(1),
      'tally.img_0': increment(1),
    }));
  });
});
