/**
 * זרימה מלאה – registration, poll creation, voting and the counters.
 */
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, increment, setDoc, updateDoc } from 'firebase/firestore';
import {
  FRIEND, OWNER, POLL_ID, STRANGER,
  as, asAnon, hoursFromNow, makeTestEnv, pollDoc, seed,
} from './helpers.mjs';

describe('זרימה מלאה', () => {
  let env;
  before(async () => { env = await makeTestEnv('flow'); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => { await seed(env); });

  it('a user may claim a username document for their own uid', async () => {
    const db = as(env, STRANGER);
    await assertSucceeds(
      setDoc(doc(db, 'usernames/zara'), { uid: STRANGER, username: 'zara' })
    );
  });

  it('claiming a username for somebody else is rejected', async () => {
    const db = as(env, STRANGER);
    await assertFails(
      setDoc(doc(db, 'usernames/zara'), { uid: OWNER, username: 'zara' })
    );
  });

  it('an existing username cannot be overwritten – this is what makes names unique', async () => {
    const db = as(env, STRANGER);
    await assertFails(
      setDoc(doc(db, 'usernames/hila'), { uid: STRANGER, username: 'hila' })
    );
  });

  it('a user may create their own profile', async () => {
    const db = as(env, 'uid_new');
    await assertSucceeds(
      setDoc(doc(db, 'users/uid_new'), {
        username: 'maya', usernameLower: 'maya', email: 'maya@example.com',
        pollsCreated: 0, votesGiven: 0,
      })
    );
  });

  it('a user may not create a profile under another uid', async () => {
    const db = as(env, 'uid_new');
    await assertFails(
      setDoc(doc(db, `users/${OWNER}`), { username: 'stolen' })
    );
  });

  it('any signed-in user may read a profile – names are shown on polls', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(getDoc(doc(db, `users/${OWNER}`)));
  });

  it('a visitor who is not logged in may not read profiles', async () => {
    const db = asAnon(env);
    await assertFails(getDoc(doc(db, `users/${OWNER}`)));
  });

  it('the owner may create a poll that has two images', async () => {
    const db = as(env, OWNER);
    await assertSucceeds(
      setDoc(doc(db, 'polls/poll_new'), pollDoc())
    );
  });

  it('a poll with a single image is rejected', async () => {
    const db = as(env, OWNER);
    await assertFails(
      setDoc(doc(db, 'polls/poll_new'), pollDoc({
        images: [{ id: 'img_0', label: 'A', thumb: 'AAAA' }],
      }))
    );
  });

  it('a poll with an empty question is rejected', async () => {
    const db = as(env, OWNER);
    await assertFails(
      setDoc(doc(db, 'polls/poll_new'), { ...pollDoc(), question: '' })
    );
  });

  it('a poll that starts with votes already on the clock is rejected', async () => {
    const db = as(env, OWNER);
    await assertFails(
      setDoc(doc(db, 'polls/poll_new'), pollDoc({ voteCount: 5 }))
    );
  });

  it('a poll cannot be created in somebody else’s name', async () => {
    const db = as(env, STRANGER);
    await assertFails(
      setDoc(doc(db, 'polls/poll_new'), pollDoc({
        ownerId: OWNER, visibleTo: [OWNER, STRANGER],
      }))
    );
  });

  it('a friend in the audience may cast a vote and move the counters', async () => {
    const db = as(env, FRIEND);
    await assertSucceeds(
      setDoc(doc(db, `polls/${POLL_ID}/votes/${FRIEND}`), {
        userName: 'noa', choiceImageId: 'img_0', ratings: {},
        createdAt: hoursFromNow(0),
      })
    );
    await assertSucceeds(
      updateDoc(doc(db, `polls/${POLL_ID}`), {
        voteCount: increment(1),
        'tally.img_0': increment(1),
      })
    );
  });

  it('voting a second time is rejected – the vote document may never be updated', async () => {
    const db = as(env, FRIEND);
    const voteRef = doc(db, `polls/${POLL_ID}/votes/${FRIEND}`);
    await assertSucceeds(setDoc(voteRef, {
      userName: 'noa', choiceImageId: 'img_0', ratings: {},
      createdAt: hoursFromNow(0),
    }));
    await assertFails(setDoc(voteRef, {
      userName: 'noa', choiceImageId: 'img_1', ratings: {},
      createdAt: hoursFromNow(0),
    }));
  });
});
