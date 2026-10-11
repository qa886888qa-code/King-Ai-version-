# King AI — Android

Native Android application built with Kotlin and Jetpack Compose.

## Current integration
- Supabase Auth email/password sign-in and account creation.
- Authenticated `ai-chat` Supabase Edge Function, with JWT verification enabled.
- Gemini 2.5 Flash-Lite provider call from the server only.
- Chat records saved in `public.chats` and `public.messages`; user-scoped RLS is enabled.
- On sign-in, the app restores the most recently updated chat.
- GitHub Actions workflow builds a debug APK artifact.

## One-time Gemini secret setup
1. Create a Gemini API key in [Google AI Studio](https://aistudio.google.com/apikey).
2. Open the Supabase dashboard for project `King2` (ref: `oyjemlbsmffvofefgxvd`).
3. Go to **Edge Functions → Secrets** and add the secret name `GEMINI_API_KEY` with the API key as its value.
4. Do not put the Gemini key in Android code, Gradle properties, GitHub, or this README. Do not send it in chat.
5. Confirm email/password sign-in settings in Supabase Auth. If email confirmation is enabled, users must verify their email before signing in.

Gemini free-tier usage has quotas and limits, and provider free-tier data-use terms may differ from paid service terms. Review the current [Gemini API pricing and data-use details](https://ai.google.dev/gemini-api/docs/pricing) before using sensitive information.

## Android configuration
The app uses the Supabase project's public publishable key and the HTTPS Edge Function URL in `gradle.properties`. The publishable key is intended for client use; never substitute a Supabase secret/service-role key.

## Security and production checklist
- Edge Function requires a valid signed-in Supabase user JWT.
- The function checks chat ownership and uses the caller's user-scoped Supabase client, so database RLS remains active.
- Message length is capped, provider calls have a timeout, and the Gemini key stays in a server-side header.
- Before public release, add a reliable per-user/IP rate limit and usage budget, test signup/email-confirmation flows on a real Android device, review privacy/retention requirements, and test the APK build.
- The access token currently lives in app memory only; users sign in again after restarting the app.

## Build
Open in Android Studio with Android SDK 35 and JDK 17, then run `gradle assembleDebug`. GitHub Actions can produce a `KingAI-debug-APK` artifact from the repository's Actions page.
