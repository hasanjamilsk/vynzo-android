# VYNZO Android (মিশ্র নেটিভ অ্যাপ)

ওয়েবসাইট WebView-তে চলে, আর কল এলে নেটিভ ফুল-স্ক্রিন রিং + রিংটোন।

## ধাপ
1. GitHub-এ public repo `vynzo-android` বানিয়ে এই ফোল্ডারের সব ফাইল আপলোড করো (`.github` ফোল্ডারসহ)।
2. Firebase প্রজেক্টে Android অ্যাপ যোগ করো, package: `online.malaysiagrowthagency.vynzo` → `google-services.json` নামাও।
3. Firebase → Project settings → Service accounts → Generate new private key → JSON ফাইলটি সার্ভারে `data/fcm-service-account.json` নামে রাখো (কখনো GitHub-এ নয়)।
4. GitHub → Settings → Secrets → Actions-এ যোগ করো:
   - GOOGLE_SERVICES_JSON (google-services.json-এর পুরো লেখা)
   - KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD (PWABuilder-এর signing key)
5. Actions → Build → Run workflow → শেষে Artifacts থেকে VYNZO-apk নামাও, আগের অ্যাপের ওপর ইনস্টল করো।
