# Android for Cars スマートフォン連携認証システム

## 概要

このプロジェクトは、Android for Cars（Android Automotive OS）でのGoogleアカウント認証を、ユーザーの手持ちスマートフォンで完結させる仕組みを提供します。

車載システムの限られた入力インターフェースでは、複雑なアカウント認証が困難です。本システムでは、OAuth 2.0の「Device Authorization Flow」を活用し、スマートフォンアプリとの連携により、シームレスな認証体験を実現します。

## システムアーキテクチャ

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         認証フロー全体図                                  │
└─────────────────────────────────────────────────────────────────────────┘

  ┌──────────────────┐                              ┌──────────────────┐
  │  Android for Cars │                              │  スマートフォン    │
  │  (車載システム)     │                              │  (Companion App)  │
  └────────┬─────────┘                              └────────┬─────────┘
           │                                                  │
           │ 1. Device Code リクエスト                         │
           │────────────────────────────────────────────────► │
           │                     Google OAuth Server           │
           │ ◄──────────────────────────────────────────────── │
           │ 2. device_code, user_code, verification_url 受信  │
           │                                                  │
           │ 3. QRコード生成・表示                              │
           │    (verification_url + user_code をエンコード)     │
           │                                                  │
           │ ════════════════════════════════════════════════ │
           │         QRコードスキャン or NFC/BLE               │
           │ ════════════════════════════════════════════════ │
           │                                                  │
           │                                    4. QRスキャン   │
           │                                       URL解析     │
           │                                                  │
           │                                    5. Googleログイン
           │                                       user_code入力│
           │                                       (又は自動)   │
           │                                                  │
           │ 6. トークンポーリング                              │
           │────────────────────────────────────────────────► │
           │                     Google OAuth Server           │
           │ ◄──────────────────────────────────────────────── │
           │ 7. Access Token + Refresh Token 取得              │
           │                                                  │
           │ 8. 認証完了・サービス利用開始                       │
           ▼                                                  ▼
```

## コンポーネント構成

### 1. Android Automotive アプリ (車載システム側)
- **場所**: `automotive-app/`
- **役割**: 
  - Device Authorization Flowの開始
  - QRコード生成・表示
  - トークンポーリング
  - 認証状態管理

### 2. Companion Android アプリ (スマートフォン側)
- **場所**: `companion-app/`
- **役割**:
  - QRコードスキャン
  - Google Sign-In WebView/CustomTabs
  - 認証完了通知

## 技術仕様

### 使用技術

| コンポーネント | 技術スタック |
|--------------|-------------|
| 車載アプリ | Android Automotive OS, Kotlin, Jetpack Compose |
| スマホアプリ | Android, Kotlin, Jetpack Compose, CameraX |
| 認証 | OAuth 2.0 Device Authorization Grant (RFC 8628) |
| 通信 | HTTPS, QRコード (ZXing) |

### OAuth 2.0 Device Authorization Flow

```
POST https://oauth2.googleapis.com/device/code
Content-Type: application/x-www-form-urlencoded

client_id={CLIENT_ID}&scope=openid%20email%20profile
```

**レスポンス例:**
```json
{
  "device_code": "4/0AeaYSHBj...",
  "user_code": "GQVQ-JKEC",
  "verification_url": "https://www.google.com/device",
  "expires_in": 1800,
  "interval": 5
}
```

## セットアップ手順

### 前提条件

1. **Google Cloud Console** でプロジェクト作成
2. **OAuth 2.0 クライアントID** を2つ作成:
   - TV and Limited Input devices 用（車載システム）
   - Android 用（スマートフォン）
3. 必要なスコープを設定: `openid`, `email`, `profile`

### Google Cloud Console 設定

1. [Google Cloud Console](https://console.cloud.google.com/) にアクセス
2. 新規プロジェクト作成 または 既存プロジェクト選択
3. **APIs & Services** > **Credentials** に移動
4. **CREATE CREDENTIALS** > **OAuth client ID**
5. Application type: **TVs and Limited Input devices** を選択
6. クライアントIDをメモ

### 環境変数

```bash
# automotive-app/.env
GOOGLE_CLIENT_ID=your-automotive-client-id.apps.googleusercontent.com

# companion-app/.env  
GOOGLE_CLIENT_ID=your-android-client-id.apps.googleusercontent.com
```

## ビルド方法

### 車載アプリ (Android Automotive)

```bash
cd automotive-app
./gradlew assembleDebug

# エミュレータで実行
./gradlew installDebug
```

### スマートフォンアプリ

```bash
cd companion-app
./gradlew assembleDebug

# 実機で実行
./gradlew installDebug
```

## セキュリティ考慮事項

1. **トークンの安全な保存**: EncryptedSharedPreferences使用
2. **PKCE**: Authorization Codeフローの追加保護
3. **証明書ピニング**: Google API通信時
4. **短寿命トークン**: Access Tokenは短時間で失効
5. **ユーザー確認**: デバイス認証時のuser_code確認

## ディレクトリ構成

```
car-auth-system/
├── README.md                              # このファイル
├── prompts/
│   ├── automotive-app-prompt.md           # 車載アプリ生成用プロンプト（Google）
│   ├── companion-app-prompt.md            # スマホアプリ生成用プロンプト（Google）
│   ├── automotive-app-multi-idp-prompt.md # 車載アプリ生成用プロンプト（Google + Azure）
│   └── companion-app-multi-idp-prompt.md  # スマホアプリ生成用プロンプト（Google + Azure）
├── docs/                                  # ドキュメント
│  ├── ARCHITECTURE.md                     # 詳細設計書（Google OAuth）
│  ├── ARCHITECTURE-MULTI-IDP.md           # マルチIDP対応設計書
│  └── QUICKSTART.md                       # クイックスタートガイド
├── automotive-app/                        # 生成される車載アプリ
│   └── (Claude Codeで生成)
└── companion-app/                         # 生成されるスマホアプリ
    └── (Claude Codeで生成)
```

## バージョン

### シングルプロバイダー版（Google OAuth のみ）
- `prompts/automotive-app-prompt.md`
- `prompts/companion-app-prompt.md`

シンプルな構成でGoogle認証のみを実装する場合に使用します。

### マルチIDプロバイダー版（Google + Azure AD External ID）
- `prompts/automotive-app-multi-idp-prompt.md`
- `prompts/companion-app-multi-idp-prompt.md`

企業向けにGoogle と Microsoft アカウントの両方をサポートする場合に使用します。
将来的に Apple ID などの追加も容易な設計になっています。

## ライセンス

MIT License

## 参考資料

- [OAuth 2.0 Device Authorization Grant (RFC 8628)](https://datatracker.ietf.org/doc/html/rfc8628)
- [Google OAuth 2.0 for TV and Limited-Input Devices](https://developers.google.com/identity/protocols/oauth2/limited-input-device)
- [Android Automotive OS Documentation](https://developer.android.com/training/cars)
- [Jetpack Compose for Automotive](https://developer.android.com/training/cars/compose)

## Generated Code Structure / 生成されたコードの構成

The project consists of two main applications: an **Automotive App** (for the car) and a **Companion App** (for the phone).
プロジェクトは、主に**Automotive App**（車載用）と**Companion App**（スマホ用）の2つのアプリケーションで構成されています。

### 1. Automotive App (`automotive-app`)
Run on Android Automotive OS. Handles the authentication request and displays the QR code.
Android Automotive OS上で動作し、認証リクエストの処理とQRコードの表示を行います。

| Path / パス | Description / 説明 |
| :--- | :--- |
| `app/build.gradle.kts` | App-level build config. Contains `buildConfigField` for Client IDs. <br> アプリレベルのビルド設定。Client ID等の設定を含みます。 |
| `auth/AuthProvider.kt` | Interface defining the contract for IDPs (Google, Azure). <br> IDプロバイダー（Google, Azure）の共通インターフェース定義。 |
| `auth/GoogleAuthProvider.kt` | Implementation for Google Sign-In using Device Flow. <br> GoogleのDevice Flow認証の実装。 |
| `auth/AzureAuthProvider.kt` | Implementation for Azure AD External ID. <br> Azure AD External IDの認証実装。 |
| `auth/AuthState.kt` | State holder for the auth flow (Idle, QR Display, Success, Error). <br> 認証フローの状態管理（待機中、QR表示中、成功、エラー等）。 |
| `ui/screens/QRCodeScreen.kt` | UI to display the generated QR code. <br> 生成されたQRコードを表示する画面。 |
| `viewmodel/AuthViewModel.kt` | Manages the auth logic and UI state. <br> 認証ロジックとUIステートを管理するViewModel。 |

### 2. Companion App (`companion-app`)
Runs on an Android Phone. Scans the QR code and launches the authentication page.
Androidスマホ上で動作し、QRコードをスキャンして認証ページを立ち上げます。

| Path / パス | Description / 説明 |
| :--- | :--- |
| `scanner/QRCodeAnalyzer.kt` | ML Kit analyzer to detect QR codes from camera stream. <br> カメラ映像からQRコードを検出するML Kit Analyzer。 |
| `auth/AuthUrlParser.kt` | Parses the custom `carauth://` scheme from the QR code. <br> QRコード内の独自スキーマ `carauth://` を解析します。 |
| `auth/CustomTabsHelper.kt` | Opens the auth URL in a Chrome Custom Tab. <br> 認証URLをChrome Custom Tabsで開きます。 |
| `ui/screens/ScanScreen.kt` | Camera preview screen with overlay. <br> カメラプレビューとオーバーレイを表示する画面。 |
| `ui/screens/ProviderConfirmScreen.kt` | Shows provider details and User Code before redirecting. <br> リダイレクト前にプロバイダー情報とUser Codeを表示・確認する画面。 |

---

## Setup & Build / セットアップとビルド

Please refer to [docs/QUICKSTART.md](docs/QUICKSTART.md) for detailed setup instructions, including:
詳細なセットアップ手順については [docs/QUICKSTART.md](docs/QUICKSTART.md) を参照してください。
- Google OAuth Setup (Google Client IDの取得手順)
- How to configure Client IDs (Client IDの設定方法)
- Build and Debug instructions (ビルドとデバッグの手順)
