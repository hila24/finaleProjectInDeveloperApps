# SnapVote

אפליקציה חברתית לסקרי תמונות — העלו · דרגו · היעלמו.
פרויקט גמר, Android (Kotlin + XML) עם Firebase.

> **[HESBER.md](HESBER.md)** — סקירה מלאה של הפרויקט: המסע באפליקציה, הארכיטקטורה,
> מודל הנתונים, האבטחה, הבדיקות ותשובות לשאלות שעלולות לצוץ בהצגה.
> **[BEDIKOT.md](BEDIKOT.md)** — רשימת הבדיקות הידניות לפני ההגשה.

## מה האפליקציה עושה

מעלים כמה תמונות, קובעים דדליין, והחברים מצביעים — בחירת תמונה אחת או דירוג בכוכבים.
התוצאות מתעדכנות בזמן אמת, ובתום הסקר התמונות נמחקות אוטומטית ונשארות רק התוצאות.

## מסכים

| מסך | קובץ |
|-----|------|
| התחברות / הרשמה | `ui/auth/LoginFragment.kt`, `RegisterFragment.kt` |
| פיד סקרים | `ui/feed/FeedFragment.kt` |
| יצירת סקר | `ui/create/CreatePollFragment.kt` |
| הצבעה / דירוג | `ui/vote/VoteFragment.kt` |
| תוצאות | `ui/results/ResultsFragment.kt` |
| פרופיל והיסטוריה | `ui/profile/ProfileFragment.kt` |
| רשימת חברים | `ui/friends/FriendsFragment.kt` |
| מי הצביע (ליוצרת הסקר) | `ui/voters/VotersFragment.kt` |

## ארכיטקטורה

```
ui/<screen>/  Fragment  →  ViewModel (LiveData)  →  Repository  →  Firebase
data/model/   Poll, PollImage, Vote, User
data/repository/  AuthRepository, PollRepository
util/         ImageCompressor, TimeFormat, loadBase64
```

ניווט: Navigation Component עם גרף אחד (`res/navigation/nav_graph.xml`) ו‑Activity יחיד.
כל המסכים משתמשים ב‑ViewBinding.

## מודל הנתונים ב‑Firestore

```
usernames/{lowercase name}
  uid, username     ← טבלת ייחודיות; חברים נמצאים דרכה

users/{uid}
  username, usernameLower, email, createdAt, pollsCreated, votesGiven

users/{uid}/friends/{friendUid}
  username, since   ← חברות הדדית, נכתבת לשני הצדדים

polls/{pollId}
  question, ownerId, ownerName, mode ("SINGLE" | "RATING"),
  createdAt, deadline, voteCount, tally: {imageId: score},
  visibleTo: [uid, ...],   ← מי רשאי לראות את הסקר
  imagesDeleted, images: [{ id, label, thumb }]

polls/{pollId}/images/{imageId}
  data           ← התמונה המלאה כ‑Base64

polls/{pollId}/votes/{uid}
  userName, choiceImageId, ratings: {imageId: stars}, createdAt

polls/{pollId}/comments/{commentId}
  userId, userName, text, createdAt
```

### למה התמונות ב‑Firestore ולא ב‑Cloud Storage

פרויקטים חדשים ב‑Firebase (מאוקטובר 2024) דורשים תוכנית Blaze בתשלום כדי להפעיל
Cloud Storage. כדי שהפרויקט יעבוד לגמרי בתוכנית החינמית, כל תמונה נשמרת כ‑Base64:

* **תמונה ממוזערת** (320px, ~20KB) יושבת על מסמך הסקר — כך הפיד עולה בקריאה אחת.
* **התמונה המלאה** (1080px, עד 700KB) במסמך נפרד — מתחת למגבלת ה‑1MB של Firestore.

המעבר ל‑Cloud Storage בעתיד נוגע רק ל‑`PollRepository` ול‑`ImageCompressor`.

### חברים ופרטיות הפיד

שם המשתמש ייחודי (`usernames/{lowercase}` נוצר פעם אחת בלבד, ואי אפשר לדרוס אותו),
וכך אפשר למצוא חברה לפי שם. חברות היא הדדית ומיידית — הוספה כותבת מסמך בשתי
רשימות החברים.

כשנוצר סקר נשמר בו `visibleTo` = הבעלים + החברים שלו באותו רגע. הפיד שואל
`whereArrayContains("visibleTo", myUid)` יחד עם `deadline > now`, ואותו תנאי בדיוק
נאכף גם בכללי האבטחה — כך שמי שאינו חבר לא רק שלא רואה את הסקר במסך, הוא גם מקבל
`PERMISSION_DENIED` אם ינסה לקרוא אותו ישירות. השאילתה נשענת על אינדקס מורכב
המוגדר ב‑`firestore.indexes.json`.

### שיתוף בקישור (App Links)

כפתור השיתוף שולח קישור `https://snapvote-hila-2026.web.app/poll/{id}` — כתובת https
אמיתית, ולכן וואטסאפ הופך אותה לקישור לחיץ. הקישור מאומת כ‑App Link דרך
`public/.well-known/assetlinks.json` המכיל את טביעת האצבע של מפתח החתימה, כך שהלחיצה
פותחת ישירות את האפליקציה ולא את הדפדפן. מי שהאפליקציה לא מותקנת אצלה מגיעה לדף נחיתה.

אם עוד לא התחברת, מזהה הסקר נשמר ב‑`PendingPoll` ונפתח מיד אחרי ההתחברות.
סקר שכבר הצבעת בו, שנסגר או שהוא שלך — נפתח ישירות במסך התוצאות.

הערה: טביעת האצבע ב‑`assetlinks.json` היא של מפתח ה‑debug. אם תיצרי בעתיד גרסת
release חתומה במפתח אחר, צריך להוסיף גם אותה לקובץ ולפרוס מחדש.

### תזכורות

בעת יצירת סקר נקבעות שתי משימות WorkManager: אחת שעה לפני הדדליין ואחת בסגירה.
ההתראה מקומית לגמרי — אין שרת ואין עלות. לחיצה עליה פותחת את הסקר דרך אותו deep link.

### ספירת הצבעות

`PollRepository.castVote` רץ בתוך `runTransaction` עם `FieldValue.increment`, כך ששני
אנשים שמצביעים באותו רגע לא דורסים אחד את הספירה של השני. מסמך ההצבעה נוצר פעם אחת
בלבד (`allow update: if false` בכללים), כך שאי אפשר להצביע פעמיים.

### מחיקה אוטומטית

`PollRepository.cleanupExpiredPollsOf` נקרא מהפיד: עבור כל סקר של המשתמש שהדדליין שלו
עבר, נמחקים מסמכי התמונות והממוזערות, ו‑`imagesDeleted` מסומן — התוצאות נשארות.

## בדיקות

בדיקות יחידה על לוגיקת החישוב של הסקר — מנצחת, אחוזים, ממוצע דירוג ומצב הדדליין:

```bash
./gradlew :app:testDebugUnitTest
# דוח: app/build/reports/tests/testDebugUnitTest/index.html
```

הקובץ `app/src/test/java/com/hila/snapvote/data/model/PollTest.kt` מכיל 14 בדיקות.
נבחרה דווקא הלוגיקה הזו כי טעות באחוזים או בבחירת המנצחת היא הדבר שהכי קשה
לתפוס בעין בזמן שימוש רגיל.

בדיקות ידניות שדורשות מכשיר אמיתי (קישורים, התראות, סנכרון בין שני משתמשים)
מרוכזות ב‑[BEDIKOT.md](BEDIKOT.md).

## הרצה

1. פותחים את התיקייה `SnapVote` ב‑Android Studio ומחכים ל‑Gradle sync.
2. הקובץ `app/google-services.json` כבר במקום (פרויקט `snapvote-hila-2026`).
3. Run ▶ על מכשיר או אמולטור עם Android 7.0 (API 24) ומעלה.

בנייה מהטרמינל:

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## הגדרות Firebase

* **Firestore** — נוצר באזור `eur3`, הכללים ב‑`firestore.rules`.
* **Authentication** — Email/Password. אם ההרשמה מחזירה שגיאה, יש להפעיל את הספק
  בקונסולה: Authentication → Get started → Email/Password → Enable.

פריסת כללי אבטחה מחדש:

```bash
firebase deploy --only firestore:rules --project snapvote-hila-2026
```
