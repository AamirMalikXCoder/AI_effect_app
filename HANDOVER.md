# AI Effect App — Tumhare Steps (Handover)

Ye wo kaam hain jo **sirf tum** kar sakte ho — main tumhare accounts me login nahi kar sakta.
App ka code (native Android/Kotlin) + backend functions ka code maine likh diya hai.

## 1. Firebase project (backend: login, credits, AI proxy)
- [ ] https://console.firebase.google.com → **Add project** → naam: `ai-effect-app`
- [ ] **Authentication** → Sign-in method → **Anonymous** enable karo (Google optional)
- [ ] **Firestore Database** → Create database → production mode → region: `asia-south1`
- [ ] **Storage** → Get started → region: `asia-south1`
- [ ] **Functions** → **Blaze plan** pe upgrade karo (pay-as-you-go; free tier ke baad hi charge)
- [ ] Project settings se ye values nikalo → `app/.../Config.kt` me bhardo:
      `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, `FIREBASE_STORAGE_BUCKET`
- [ ] (Optional, cleaner) `google-services.json` download karke `app/` folder me rakho —
      phir `AiEffectApp.kt` ka manual-init block hata dena

## 2. Luma API key (video effects: hug/kiss/dance — $0.30/video)
- [ ] https://lumalabs.ai → Dream Machine **API** → API key generate karo
- [ ] Key **mujhe chat me mat bhejo** — deploy ke time secure tarike se dunga/dungi instructions

## 3. OpenAI API key (photo-to-anime — $0.011/image)
- [ ] https://platform.openai.com → API keys → nayi key
- [ ] Billing me payment method add karna padega (~$5 se shuru)
- [ ] Key mujhe chat me mat bhejo

## 4. Backend deploy (terminal me, ek baar)
```bash
cd backend/functions && npm install
firebase use --add   # tumhara project select karo
firebase functions:config:set luma.key="LUMA_KEY" luma.model="ray-3.2" openai.key="OPENAI_KEY"
firebase deploy --only functions,firestore:rules
```

## 5. AdMob (apps.admob.com)
- [ ] App add karo → **App ID** nikalo → `Config.ADMOB_APP_ID` me daalo
      (Manifest ka meta-data bhi wahi value use karega — TODO lage hain)
- [ ] Rewarded + Interstitial **ad unit IDs** banao → `Config` me daalo

## 6. Play Billing (Play Console → Monetize)
- [ ] Subscriptions banao: `pro_weekly`, `pro_yearly` (IDs `Config` se match honi chahiye)
- [ ] Managed product banao: `credits_10` (10 video credits)
- [ ] Closed testing: naye accounts ko **14 din / 12+ testers** chahiye production se pehle

## 7. Play policy (code me pehle se hai)
- Har AI output pe "AI-generated" label + report button ✅
- Pehle upload se pehle data-sharing disclosure ✅
- Play Console → App content → **AI-generated content** declaration bharna mat bhoolna

## Paise ka hisaab (yaad rahe)
- Video effects **paid-only** (1 free trial) — ads se video ka kharcha nahi nikalta
- Anime images: 3/day free (ad se), uske baad paid
- API keys **sirf Cloud Functions me** — app me kabhi nahi

## Repo structure
```
AI_effect_app/
├── app/                 # Native Android app (Kotlin + Compose)
│   └── src/main/java/com/malik/aieffectapp/
│       ├── MainActivity.kt        # Navigation (home/effect/result/paywall/creations)
│       ├── AiEffectApp.kt         # Firebase + AdMob init, anonymous auth
│       ├── Config.kt              # ← TUMHARI VALUES YAHAN
│       ├── data/                  # Effect, AiRepository, BillingRepository, AdsManager
│       └── ui/                    # home, effect, result, paywall, creations, components
├── backend/
│   ├── functions/       # Cloud Functions: generateVideo (Luma), generateImage (OpenAI)
│   └── firestore.rules
└── HANDOVER.md          # ye file
```
