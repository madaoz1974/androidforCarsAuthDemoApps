# Quick Start Guide / クイックスタートガイド

This guide explains how to configure, build, and run the Multi-IDP Auth Demo apps.
このガイドでは、マルチIDP認証デモアプリの設定、ビルド、実行方法について説明します。

## Prerequisites / 前提条件

- **Android Studio**: Iguana or later is recommended.
- **JDK**: JDK 17.
- **Emulators/Devices**:
  - Android Automotive OS Emulator (Api 32+).
  - Android Phone (or Emulator) with Camera support.

## 1. Google OAuth Setup / Google OAuthのセットアップ

Detailed steps to obtain the necessary Client IDs from Google Cloud Console.
Google Cloud Consoleから必要なClient IDを取得するための詳細手順です。

### Step 1.1: Create Project & Configure Consent Screen
### Step 1.1: プロジェクト作成と同意画面の設定

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
   [Google Cloud Console](https://console.cloud.google.com/) にアクセスします。
2. Create a new project (e.g., "Car Auth Demo").
   新しいプロジェクトを作成します（例: "Car Auth Demo"）。
3. Navigate to **APIs & Services** > **OAuth consent screen**.
   **APIs & Services** > **OAuth consent screen** に移動します。
4. Select **External** (unless you have a Google Workspace organization) and click **Create**.
   **External**（Google Workspace組織がない場合）を選択し、**Create** をクリックします。
5. Fill in the required fields:
   必須項目を入力します:
   - **App name**: "Car Auth Demo"
   - **User support email**: Your email
   - **Developer contact information**: Your email
6. Click **Save and Continue**.
   **Save and Continue** をクリックします。
7. (Optional) Add **Scopes**: Add `userinfo.email` and `userinfo.profile`.
   (任意) **Scopes** を追加: `userinfo.email` と `userinfo.profile` を追加します。
8. **Test users**: Add your own Google email address to test the app.
   **Test users**: アプリをテストするために、ご自身のGoogleメールアドレスを追加します。

### Step 1.2: Create Client ID for Automotive App
### Step 1.2: 車載アプリ用 Client ID の作成

1. Navigate to **APIs & Services** > **Credentials**.
   **APIs & Services** > **Credentials** に移動します。
2. Click **Create Credentials** > **OAuth client ID**.
   **Create Credentials** > **OAuth client ID** をクリックします。
3. Select Application type: **TVs and Limited Input devices**.
   Application type で **TVs and Limited Input devices** を選択します。
4. Name: "Automotive App".
   名前を "Automotive App" とします。
5. Click **Create**.
   **Create** をクリックします。
6. **Copy the Client ID**. This will be your `GOOGLE_CLIENT_ID` for the Automotive app.
   **Client ID をコピー**します。これが車載アプリの `GOOGLE_CLIENT_ID` になります。

### Step 1.3: Create Client ID for Companion App
### Step 1.3: スマホアプリ用 Client ID の作成

1. Click **Create Credentials** > **OAuth client ID** again.
   再度 **Create Credentials** > **OAuth client ID** をクリックします。
2. Select Application type: **Android**.
   Application type で **Android** を選択します。
3. Name: "Companion App".
   名前を "Companion App" とします。
4. **Package name**: `com.example.carcompanion`.
   **Package name** に `com.example.carcompanion` と入力します。
5. **SHA-1 certificate fingerprint**:
   **SHA-1 certificate fingerprint** を入力します:
   
   Run the following command in your terminal to get the debug keystore fingerprint:
   ターミナルで以下のコマンドを実行し、デバッグ用キーストアのフィンガープリントを取得します:
   
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   
   *Wait, if you don't have Java installed, you can use the Gradle wrapper in the project:*
   *Javaがインストールされていない場合は、プロジェクト内のGradle wrapperを使用できます:*
   
   ```bash
   ./gradlew signingReport
   ```
   
   Look for the `SHA1` under `:companion-app:signingReport` (Variant: debug).
   `:companion-app:signingReport` (Variant: debug) の下にある `SHA1` を探してください。

6. Click **Create**.
   **Create** をクリックします。
7. You don't strictly need this Client ID in your code for the *Device Flow* initiated by the car, but registering the Android app establishes the trust for the package name.
   車側主導のDevice Flowでは、このClient IDをコードに埋め込む必要は厳密にはありませんが、Androidアプリとしてパッケージ名を登録することで信頼性を確立します。

---

## 2. Configure Credentials in Code / コードへの認証情報設定

```kotlin
// automotive-app/app/build.gradle.kts

buildTypes {
    debug {
        // Replace with your actual Google Client ID
        // 実際のGoogle Client IDに置き換えてください
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com\"")
        
        // Replace with your Azure AD Tenant ID
        // 実際のAzure AD Tenant IDに置き換えてください
        buildConfigField("String", "AZURE_TENANT_ID", "\"YOUR_TEANT_ID\"")
        
        // Replace with your Azure AD Client ID (Application ID)
        // 実際のAzure AD Client ID (Application ID)に置き換えてください
        buildConfigField("String", "AZURE_CLIENT_ID", "\"YOUR_CLIENT_ID\"")
        
        // Set to "true" if using a CIAM tenant, "false" for standard Azure AD
        // CIAMテナントを使用する場合は "true"、通常のAzure ADの場合は "false"
        buildConfigField("String", "AZURE_USE_CIAM", "\"false\"")
    }
}
```

> [!IMPORTANT]
> Do not commit real credentials to version control! For a production app, use `local.properties` or a secure secrets management solution.
> 本物の認証情報はバージョン管理にコミットしないでください！本番アプリでは `local.properties` の使用や、安全なシークレット管理ソリューションを検討してください。

## 3. Build and Run / ビルドと実行

### Step 1: Automotive App
1. Open the project in Android Studio.
   Android Studioでプロジェクトを開きます。
2. Select the **`automotive-app`** configuration.
   **`automotive-app`** の設定を選択します。
3. Select an **Android Automotive Emulator**.
   **Android Automotive Emulator** を選択します。
4. Click **Run** (▶️).
   **Run** (▶️) をクリックします。

### Step 2: Companion App
1. Select the **`companion-app`** configuration.
   **`companion-app`** の設定を選択します。
2. Connect an Android Phone or select a Phone Emulator (Webcam support required for QR scanning).
   Androidスマホを接続するか、Phone Emulator（QRスキャンのためにWebカメラ対応が必要）を選択します。
3. Click **Run** (▶️).
   **Run** (▶️) をクリックします。

## 4. Testing the Flow / 動作確認手順

1. **Automotive App**: Launch the app. You will see the "Select Sign-In Method" screen.
   **Automotive App**: アプリを起動します。「Select Sign-In Method」画面が表示されます。
2. **Automotive App**: Click "Google" or "Microsoft". A QR code and a User Code (e.g., `ABCD-1234`) will appear.
   **Automotive App**: IDプロバイダーを選択します。QRコードとユーザーコードが表示されます。
3. **Companion App**: Launch the app and tap "Start Scan".
   **Companion App**: アプリを起動し、「Start Scan」をタップします。
4. **Companion App**: Scan the QR code displayed on the Automotive screen.
   **Companion App**: Automotive画面のQRコードをスキャンします。
5. **Companion App**: Verify the User Code matches on both screens, then tap "Open Auth Page".
   **Companion App**: ユーザーコードが一致することを確認し、「Open Auth Page」をタップします。
6. **Browser**: Sign in with your credentials and approve the device.
   **Browser**: 認証情報でサインインし、デバイスを承認します。
7. **Automotive App**: Once approved, the screen will automatically update to the "Authentication Successful" state.
   **Automotive App**: 承認されると、画面が自動的に「Authentication Successful」に更新されます。
