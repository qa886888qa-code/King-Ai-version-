# King AI — Android

Native Android application built with Kotlin and Jetpack Compose.

## Current status
- Android chat UI with Roman Urdu greeting and message bubbles.
- Network client for a King AI Supabase Edge Function.
- Android build workflow creates a debug APK artifact.
- Live AI replies are not ready until the backend endpoint and AI provider are configured.

## Configure backend
Supply these Gradle properties locally or through your private build configuration:
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY` (publishable/anon key only; never use a service-role or secret key in the Android app)
- `KING_AI_CHAT_FUNCTION_URL` (HTTPS endpoint for a deployed server function)

The function should accept JSON `{"message":"..."}` and return JSON `{"reply":"..."}`. Keep AI provider secrets on the server. Before public release, add user authentication, per-user rate limits, server-side validation, and persistent chat history.

## Build
Open in Android Studio with Android SDK 35 and JDK 17, then run `gradle assembleDebug`.
