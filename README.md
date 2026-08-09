# Teacher Attendance & Notification System (حضور و اطلاع‌رسانی معلم)

An Android application designed for school teachers to manage student attendance (Arrival, Departure, Absence) and send automated or manual notifications to parents via SMS and WhatsApp.

## Key Features

- **Student Management**: Add, edit, soft-delete, and search students.
- **Attendance Tracking**: Record Arrival (ورود), Departure (خروج), and Absence (غیبت).
- **Automated Messaging**: Queue SMS notifications via background WorkManager service.
- **WhatsApp Integration**: Support for WhatsApp messaging with `ACTION_REQUIRED` status for direct user dispatch.
- **Deduplication Engine**: Prevent accidental duplicate status notifications on the same date for the same event and channel.
- **Offline First**: Local persistence using Android Room database.

## Technical Specifications

- **Min SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 36
- **Language**: Kotlin 2.2
- **UI Framework**: Jetpack Compose (Material Design 3, RTL Layout)
- **Local Persistence**: Room Database
- **Background Execution**: WorkManager
- **Architecture**: MVVM with Repository Pattern & Kotlin Flow StateFlow

## How to Build Locally

Ensure you have Java 17+ installed.

```bash
# Make gradlew executable
chmod +x gradlew

# Build Debug APK
./gradlew assembleDebug
```

The compiled Debug APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## CI/CD Pipeline (GitHub Actions)

The repository includes an automated GitHub Actions workflow (`.github/workflows/android-build.yml`) that automatically builds the project on every push to `main`/`master` or Pull Request.

- **Workflow File**: `.github/workflows/android-build.yml`
- **Build Command**: `./gradlew assembleDebug`
- **Artifact Name**: `teacher-attendance-debug-apk`
- **Artifact Location**: `app/build/outputs/apk/debug/app-debug.apk`
