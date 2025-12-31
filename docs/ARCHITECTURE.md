# アーキテクチャ詳細設計書

## 1. システム概要

### 1.1 目的
Android Automotive OS（車載システム）でのGoogleアカウント認証において、物理キーボードやタッチスクリーンでの入力が困難な環境でも、ユーザーのスマートフォンを活用してシームレスな認証体験を提供する。

### 1.2 対象ユースケース

1. **新規セットアップ**: 車両購入時の初期Googleアカウント連携
2. **アカウント追加**: 複数ユーザーのアカウント追加
3. **再認証**: トークン失効時の再認証
4. **ゲストログイン**: 一時的なアカウント利用

## 2. 認証フロー詳細

### 2.1 Device Authorization Grant フロー

```
┌─────────────┐                                    ┌─────────────┐
│  Automotive │                                    │   Google    │
│    App      │                                    │   OAuth     │
└──────┬──────┘                                    └──────┬──────┘
       │                                                  │
       │  POST /device/code                               │
       │  client_id, scope                                │
       │ ───────────────────────────────────────────────► │
       │                                                  │
       │  200 OK                                          │
       │  device_code, user_code, verification_url        │
       │ ◄─────────────────────────────────────────────── │
       │                                                  │
       │  (Display QR Code with verification_url)         │
       │                                                  │
       │                    ┌─────────────┐               │
       │                    │  Companion  │               │
       │                    │    App      │               │
       │                    └──────┬──────┘               │
       │                           │                      │
       │      (Scan QR Code)       │                      │
       │ ◄ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│                      │
       │                           │                      │
       │                           │  GET verification_url│
       │                           │ ───────────────────► │
       │                           │                      │
       │                           │  (User enters code)  │
       │                           │  (User grants access)│
       │                           │ ───────────────────► │
       │                           │                      │
       │  POST /token (polling)    │                      │
       │  device_code, grant_type  │                      │
       │ ───────────────────────────────────────────────► │
       │                                                  │
       │  (While authorization_pending)                   │
       │ ◄─────────────────────────────────────────────── │
       │                                                  │
       │  POST /token (polling)                           │
       │ ───────────────────────────────────────────────► │
       │                                                  │
       │  200 OK                                          │
       │  access_token, refresh_token                     │
       │ ◄─────────────────────────────────────────────── │
       │                                                  │
       ▼                                                  ▼
```

### 2.2 状態遷移図

```
                    ┌───────────┐
                    │   IDLE    │
                    └─────┬─────┘
                          │
                          │ startAuth()
                          ▼
                    ┌───────────┐
                    │ REQUESTING│◄─────────────┐
                    │   CODE    │              │
                    └─────┬─────┘              │
                          │                    │
                          │ success            │ retry (network error)
                          ▼                    │
                    ┌───────────┐              │
                    │ DISPLAYING│──────────────┘
                    │   QR_CODE │
                    └─────┬─────┘
                          │
                          │ startPolling()
                          ▼
                    ┌───────────┐
               ┌────│  POLLING  │────┐
               │    └─────┬─────┘    │
               │          │          │
   authorization_pending  │ success  │ access_denied
               │          │          │
               │          ▼          │
               │    ┌───────────┐    │
               └───►│AUTHENTICATED│   │
                    └───────────┘    │
                                     │
                          ┌──────────┘
                          ▼
                    ┌───────────┐
                    │   ERROR   │
                    └───────────┘
```

## 3. コンポーネント設計

### 3.1 車載アプリ (Automotive App)

#### 3.1.1 モジュール構成

```
automotive-app/
├── app/
│   └── src/main/
│       ├── java/com/example/carauth/
│       │   ├── CarAuthApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   ├── screens/
│       │   │   │   ├── AuthScreen.kt
│       │   │   │   ├── QRCodeScreen.kt
│       │   │   │   └── HomeScreen.kt
│       │   │   └── components/
│       │   │       ├── QRCodeView.kt
│       │   │       └── StatusIndicator.kt
│       │   ├── auth/
│       │   │   ├── DeviceAuthManager.kt
│       │   │   ├── TokenManager.kt
│       │   │   └── AuthState.kt
│       │   ├── network/
│       │   │   ├── OAuthService.kt
│       │   │   └── ApiClient.kt
│       │   └── data/
│       │       ├── repository/
│       │       │   └── AuthRepository.kt
│       │       └── model/
│       │           ├── DeviceCodeResponse.kt
│       │           └── TokenResponse.kt
│       ├── res/
│       └── AndroidManifest.xml
└── build.gradle.kts
```

#### 3.1.2 主要クラス

**DeviceAuthManager**
```kotlin
class DeviceAuthManager(
    private val oAuthService: OAuthService,
    private val tokenManager: TokenManager
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    suspend fun requestDeviceCode(): DeviceCodeResponse
    suspend fun pollForToken(deviceCode: String): TokenResponse
    fun startPolling(deviceCode: String, interval: Int)
    fun cancelAuth()
}
```

**AuthState**
```kotlin
sealed class AuthState {
    object Idle : AuthState()
    object RequestingCode : AuthState()
    data class DisplayingQR(
        val qrCodeBitmap: Bitmap,
        val userCode: String,
        val expiresIn: Int
    ) : AuthState()
    object Polling : AuthState()
    data class Authenticated(val accessToken: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
```

### 3.2 スマートフォンアプリ (Companion App)

#### 3.2.1 モジュール構成

```
companion-app/
├── app/
│   └── src/main/
│       ├── java/com/example/carcompanion/
│       │   ├── CompanionApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   ├── screens/
│       │   │   │   ├── ScanScreen.kt
│       │   │   │   ├── AuthWebScreen.kt
│       │   │   │   └── SuccessScreen.kt
│       │   │   └── components/
│       │   │       ├── CameraPreview.kt
│       │   │       └── QRScanner.kt
│       │   ├── scanner/
│       │   │   ├── QRCodeAnalyzer.kt
│       │   │   └── ScanResult.kt
│       │   └── navigation/
│       │       └── NavGraph.kt
│       ├── res/
│       └── AndroidManifest.xml
└── build.gradle.kts
```

#### 3.2.2 主要クラス

**QRCodeAnalyzer**
```kotlin
class QRCodeAnalyzer(
    private val onQRCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy)
}
```

## 4. API 仕様

### 4.1 Google OAuth Endpoints

#### Device Code Request
```http
POST https://oauth2.googleapis.com/device/code
Content-Type: application/x-www-form-urlencoded

client_id=YOUR_CLIENT_ID
&scope=openid%20email%20profile
```

**Response:**
```json
{
    "device_code": "4/4-GMMhmHCXhWEzkobqIHGG_EnNYYsAkukHspe...",
    "user_code": "GQVQ-JKEC",
    "verification_url": "https://www.google.com/device",
    "expires_in": 1800,
    "interval": 5
}
```

#### Token Request (Polling)
```http
POST https://oauth2.googleapis.com/token
Content-Type: application/x-www-form-urlencoded

client_id=YOUR_CLIENT_ID
&device_code=DEVICE_CODE
&grant_type=urn:ietf:params:oauth:grant-type:device_code
```

**Success Response:**
```json
{
    "access_token": "ya29.a0AfH6SMBx...",
    "expires_in": 3599,
    "refresh_token": "1//0eVjBK...",
    "scope": "openid email profile",
    "token_type": "Bearer",
    "id_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

**Pending Response:**
```json
{
    "error": "authorization_pending"
}
```

### 4.2 QRコードデータ形式

```
carauth://auth?url=https://www.google.com/device&code=GQVQ-JKEC
```

**パラメータ:**
- `url`: Google認証URL
- `code`: ユーザーコード（表示用）

## 5. セキュリティ設計

### 5.1 トークン保存

```kotlin
// EncryptedSharedPreferencesを使用
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "auth_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### 5.2 ネットワークセキュリティ

```xml
<!-- network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">googleapis.com</domain>
        <pin-set>
            <pin digest="SHA-256">...</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

### 5.3 セキュリティチェックリスト

| 項目 | 実装方法 | 優先度 |
|-----|---------|-------|
| トークン暗号化 | EncryptedSharedPreferences | 必須 |
| 通信の暗号化 | TLS 1.3 | 必須 |
| 証明書ピニング | OkHttp CertificatePinner | 推奨 |
| コード難読化 | R8/ProGuard | 推奨 |
| Root検出 | SafetyNet/Play Integrity | オプション |

## 6. エラーハンドリング

### 6.1 エラーコード一覧

| エラー | 説明 | 対処 |
|-------|-----|------|
| `authorization_pending` | ユーザー認証待ち | ポーリング継続 |
| `slow_down` | ポーリング間隔が短い | interval増加 |
| `access_denied` | ユーザーが拒否 | フロー終了 |
| `expired_token` | device_code期限切れ | 最初からやり直し |

### 6.2 リトライ戦略

```kotlin
// Exponential Backoff
val baseInterval = 5000L // 5秒
val maxInterval = 30000L // 30秒
var currentInterval = baseInterval

fun getNextInterval(): Long {
    currentInterval = minOf(currentInterval * 2, maxInterval)
    return currentInterval
}
```

## 7. テスト戦略

### 7.1 ユニットテスト

- `DeviceAuthManager` のロジックテスト
- `TokenManager` のトークン管理テスト
- `QRCodeAnalyzer` のパーステスト

### 7.2 インテグレーションテスト

- OAuth フロー全体のE2Eテスト
- ネットワークエラー時のリカバリテスト

### 7.3 UIテスト

- QRコード表示の確認
- エラー状態のUI表示
- カメラプレビューの動作確認

## 8. 制限事項と考慮事項

### 8.1 既知の制限

1. **オフライン**: デバイス認証フローはインターネット接続必須
2. **タイムアウト**: device_codeは30分で期限切れ
3. **同時認証**: 複数デバイスでの同時認証は非推奨

### 8.2 将来の拡張

1. **Bluetooth連携**: BLE経由でのデバイス検出
2. **NFC対応**: NFCタップでの認証開始
3. **マルチアカウント**: 複数Googleアカウントの管理
