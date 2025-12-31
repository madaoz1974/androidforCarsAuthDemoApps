# Claude Code プロンプト: Android Automotive 車載認証アプリ

## 概要

このプロンプトを使用して、Android Automotive OS向けのGoogle認証アプリを生成してください。OAuth 2.0 Device Authorization Flowを使用し、QRコードを表示してスマートフォンでの認証を可能にします。

---

## プロンプト

```
Android Automotive OS向けのGoogle OAuth認証アプリを作成してください。

## 要件

### 機能要件
1. OAuth 2.0 Device Authorization Flow の実装
   - Google OAuth サーバーへの device_code リクエスト
   - QRコード生成（verification_url + user_code をエンコード）
   - トークンポーリング処理
   - アクセストークンの取得と保存

2. UI/UX
   - Jetpack Compose for Automotive 使用
   - 大画面（10-15インチ）向けのレイアウト
   - 視認性の高いQRコード表示（最小300x300dp）
   - ユーザーコードの大きな表示
   - 認証状態のプログレス表示
   - 運転中の安全性を考慮したUI

3. 状態管理
   - 認証フロー全体の状態管理（StateFlow使用）
   - エラーハンドリングとリトライロジック
   - タイムアウト管理（device_code有効期限）

### 技術仕様

**プロジェクト構成:**
- Package名: com.example.carauth
- 最小SDK: 29 (Android Automotive要件)
- ターゲットSDK: 34
- Kotlin 1.9.x
- Compose BOM 2024.x

**アーキテクチャ:**
- MVVM + Clean Architecture
- Hilt for DI
- Retrofit + OkHttp for networking
- Kotlin Coroutines + Flow

**依存関係:**
- androidx.car.app:app-automotive (Android for Cars App Library)
- io.coil-kt:coil-compose (画像/QR表示)
- com.google.zxing:core (QRコード生成)
- androidx.security:security-crypto (トークン暗号化保存)
- com.squareup.retrofit2:retrofit
- com.squareup.okhttp3:okhttp
- com.google.dagger:hilt-android

**ディレクトリ構造:**
```
automotive-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/carauth/
│   │   │   ├── CarAuthApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── di/
│   │   │   │   └── AppModule.kt
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── screens/
│   │   │   │   │   ├── AuthScreen.kt
│   │   │   │   │   ├── QRCodeScreen.kt
│   │   │   │   │   └── HomeScreen.kt
│   │   │   │   └── components/
│   │   │   │       ├── QRCodeImage.kt
│   │   │   │       ├── UserCodeDisplay.kt
│   │   │   │       └── AuthStatusIndicator.kt
│   │   │   ├── viewmodel/
│   │   │   │   └── AuthViewModel.kt
│   │   │   ├── auth/
│   │   │   │   ├── DeviceAuthManager.kt
│   │   │   │   ├── TokenManager.kt
│   │   │   │   └── AuthState.kt
│   │   │   ├── network/
│   │   │   │   ├── OAuthApi.kt
│   │   │   │   └── NetworkModule.kt
│   │   │   ├── data/
│   │   │   │   ├── repository/
│   │   │   │   │   └── AuthRepository.kt
│   │   │   │   └── model/
│   │   │   │       ├── DeviceCodeResponse.kt
│   │   │   │       ├── TokenResponse.kt
│   │   │   │       └── ErrorResponse.kt
│   │   │   └── util/
│   │   │       ├── QRCodeGenerator.kt
│   │   │       └── Constants.kt
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── xml/
│   │   │       └── automotive_app_desc.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### 実装詳細

**1. DeviceCodeResponse.kt:**
```kotlin
data class DeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("verification_url") val verificationUrl: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("interval") val interval: Int
)
```

**2. AuthState.kt:**
```kotlin
sealed class AuthState {
    object Idle : AuthState()
    object RequestingCode : AuthState()
    data class DisplayingQR(
        val qrCodeBitmap: android.graphics.Bitmap,
        val userCode: String,
        val verificationUrl: String,
        val expiresAt: Long
    ) : AuthState()
    data class Polling(val progress: Float) : AuthState()
    data class Authenticated(
        val accessToken: String,
        val userEmail: String?
    ) : AuthState()
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : AuthState()
}
```

**3. OAuthApi.kt:**
```kotlin
interface OAuthApi {
    @FormUrlEncoded
    @POST("device/code")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String = "openid email profile"
    ): DeviceCodeResponse

    @FormUrlEncoded
    @POST("token")
    suspend fun pollToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
    ): TokenResponse
}
```

**4. QRコード生成ロジック:**
- URL形式: `carauth://auth?url={verification_url}&code={user_code}`
- QRコードサイズ: 400x400ピクセル
- エラー訂正レベル: H（高）

**5. AndroidManifest.xml の重要な設定:**
```xml
<manifest>
    <uses-feature android:name="android.hardware.type.automotive" android:required="true"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    
    <application>
        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc"/>
        
        <activity android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.CarAuth">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**6. automotive_app_desc.xml:**
```xml
<automotiveApp>
    <uses name="template"/>
</automotiveApp>
```

### UIデザイン要件

1. **QRコード画面:**
   - 画面中央にQRコード（最小300dp）
   - QRコード下にユーザーコード（48sp以上のフォント）
   - 「スマートフォンでスキャンしてください」の説明文
   - 残り時間のカウントダウン表示
   - キャンセルボタン

2. **認証完了画面:**
   - 成功アイコン（チェックマーク）
   - ログインしたアカウント情報表示
   - 「続ける」ボタン

3. **エラー画面:**
   - エラーメッセージ
   - 「再試行」ボタン
   - 「キャンセル」ボタン

### テスト

以下のテストを含めてください:
- DeviceAuthManagerのユニットテスト
- QRCodeGeneratorのテスト
- ViewModelのテスト（MockKまたはMockito使用）

### 環境変数

```kotlin
object Constants {
    const val GOOGLE_CLIENT_ID = BuildConfig.GOOGLE_CLIENT_ID
    const val OAUTH_BASE_URL = "https://oauth2.googleapis.com/"
    const val DEFAULT_SCOPE = "openid email profile"
}
```

build.gradle.kts でBuildConfigフィールドを設定:
```kotlin
buildTypes {
    debug {
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"YOUR_CLIENT_ID\"")
    }
}
```

## 出力形式

完全に動作するAndroidプロジェクトを生成してください。すべてのファイルに適切なKotlinコードを含め、ビルドして実行できる状態にしてください。

## 注意事項

1. Android Automotive エミュレータまたは実機でテストできること
2. Google OAuth Client IDは環境変数で管理
3. セキュアなトークン保存（EncryptedSharedPreferences）
4. 適切なエラーハンドリングとユーザーフィードバック
5. 運転中の安全性を考慮したUI（大きなボタン、明確な視覚フィードバック）
```

---

## 使用方法

1. 上記のプロンプトをClaude Codeにコピー&ペースト
2. 生成されたコードを `automotive-app/` ディレクトリに配置
3. Google Cloud ConsoleでOAuth Client IDを取得
4. `build.gradle.kts` にClient IDを設定
5. Android Studioでプロジェクトを開き、ビルド
6. Automotive エミュレータで実行

## 生成後のチェックリスト

- [ ] すべてのファイルが正しい場所に配置されているか
- [ ] build.gradle.ktsの依存関係が正しいか
- [ ] AndroidManifest.xmlにautomotive機能が宣言されているか
- [ ] Google Client IDが設定されているか
- [ ] QRコードが正しく生成されるか
- [ ] トークンポーリングが動作するか
- [ ] エラーハンドリングが適切か
