# Claude Code プロンプト: マルチIDプロバイダー対応 車載認証アプリ

## 概要

このプロンプトを使用して、Google OAuth と Azure AD External ID の両方に対応した Android Automotive OS向け認証アプリを生成してください。

---

## プロンプト

```
Android Automotive OS向けのマルチIDプロバイダー対応認証アプリを作成してください。
Google OAuth と Azure AD External ID (Microsoft Entra External ID) の両方をサポートします。

## 要件

### 機能要件

1. **マルチIDプロバイダー対応**
   - Google OAuth 2.0 Device Flow
   - Azure AD External ID Device Flow
   - プロバイダー選択UI
   - プロバイダー抽象化レイヤー

2. **OAuth 2.0 Device Authorization Flow**
   - 各プロバイダーへの device_code リクエスト
   - QRコード生成（プロバイダー情報含む）
   - トークンポーリング処理
   - アクセストークンの取得と保存

3. **UI/UX**
   - Jetpack Compose for Automotive
   - プロバイダー選択画面（アイコン付きボタン）
   - 大画面向けQRコード表示
   - 認証状態のプログレス表示

### プロバイダー設定

**Google OAuth:**
```kotlin
object GoogleConfig {
    const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    const val SCOPE = "openid email profile"
}
```

**Azure AD External ID:**
```kotlin
object AzureConfig {
    // CIAM (External ID) エンドポイント
    fun getDeviceCodeUrl(tenantId: String) = 
        "https://{tenant}.ciamlogin.com/$tenantId/oauth2/v2.0/devicecode"
    fun getTokenUrl(tenantId: String) = 
        "https://{tenant}.ciamlogin.com/$tenantId/oauth2/v2.0/token"
    
    // 標準 Azure AD エンドポイント（オプション）
    fun getStandardDeviceCodeUrl(tenantId: String) = 
        "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/devicecode"
    fun getStandardTokenUrl(tenantId: String) = 
        "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token"
    
    const val SCOPE = "openid profile email offline_access"
}
```

### 技術仕様

**プロジェクト構成:**
- Package名: com.example.carauth
- 最小SDK: 29
- ターゲットSDK: 34
- Kotlin 1.9.x
- Compose BOM 2024.x

**アーキテクチャ:**
- MVVM + Clean Architecture
- Hilt for DI
- Retrofit + OkHttp
- Kotlin Coroutines + Flow
- Strategy Pattern for providers

**ディレクトリ構造:**
```
automotive-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/carauth/
│   │   │   ├── CarAuthApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── di/
│   │   │   │   ├── AppModule.kt
│   │   │   │   └── AuthModule.kt
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── screens/
│   │   │   │   │   ├── ProviderSelectionScreen.kt
│   │   │   │   │   ├── QRCodeScreen.kt
│   │   │   │   │   ├── AuthProgressScreen.kt
│   │   │   │   │   └── HomeScreen.kt
│   │   │   │   └── components/
│   │   │   │       ├── ProviderButton.kt
│   │   │   │       ├── QRCodeImage.kt
│   │   │   │       ├── UserCodeDisplay.kt
│   │   │   │       └── AuthStatusIndicator.kt
│   │   │   ├── viewmodel/
│   │   │   │   ├── AuthViewModel.kt
│   │   │   │   └── ProviderSelectionViewModel.kt
│   │   │   ├── auth/
│   │   │   │   ├── AuthProvider.kt           # インターフェース
│   │   │   │   ├── GoogleAuthProvider.kt     # Google実装
│   │   │   │   ├── AzureAuthProvider.kt      # Azure実装
│   │   │   │   ├── AuthProviderFactory.kt    # ファクトリ
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
│   │   │   │       ├── ProviderType.kt
│   │   │   │       └── ErrorResponse.kt
│   │   │   ├── navigation/
│   │   │   │   ├── NavGraph.kt
│   │   │   │   └── Screen.kt
│   │   │   └── util/
│   │   │       ├── QRCodeGenerator.kt
│   │   │       └── Constants.kt
│   │   ├── res/
│   │   │   ├── drawable/
│   │   │   │   ├── ic_google.xml
│   │   │   │   └── ic_microsoft.xml
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

**1. AuthProvider.kt (インターフェース):**
```kotlin
interface AuthProvider {
    val providerId: String
    val displayName: String
    val displayNameResId: Int
    val iconResId: Int
    val brandColor: Color
    
    suspend fun requestDeviceCode(): Result<DeviceCodeResponse>
    suspend fun pollToken(deviceCode: String): Result<TokenResponse>
    fun buildQRCodeContent(response: DeviceCodeResponse): String
    fun getPollingInterval(response: DeviceCodeResponse): Long
}
```

**2. ProviderType.kt:**
```kotlin
enum class ProviderType(
    val id: String,
    val displayName: String
) {
    GOOGLE("google", "Google"),
    AZURE("azure", "Microsoft");
    
    companion object {
        fun fromId(id: String): ProviderType? = 
            values().find { it.id == id }
    }
}
```

**3. GoogleAuthProvider.kt:**
```kotlin
class GoogleAuthProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    @Named("googleClientId") private val clientId: String
) : AuthProvider {
    
    override val providerId = "google"
    override val displayName = "Google"
    override val displayNameResId = R.string.provider_google
    override val iconResId = R.drawable.ic_google
    override val brandColor = Color(0xFF4285F4)
    
    private val deviceCodeUrl = "https://oauth2.googleapis.com/device/code"
    private val tokenUrl = "https://oauth2.googleapis.com/token"
    private val scope = "openid email profile"
    
    override suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url(deviceCodeUrl)
                        .post(FormBody.Builder()
                            .add("client_id", clientId)
                            .add("scope", scope)
                            .build())
                        .build()
                ).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val deviceCode = parseDeviceCodeResponse(body)
                    Result.success(deviceCode)
                } else {
                    Result.failure(AuthException("Failed to get device code"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    override suspend fun pollToken(deviceCode: String): Result<TokenResponse> {
        // 実装
    }
    
    override fun buildQRCodeContent(response: DeviceCodeResponse): String {
        return "carauth://auth?provider=google&url=${
            URLEncoder.encode(response.verificationUrl, "UTF-8")
        }&code=${response.userCode}"
    }
    
    override fun getPollingInterval(response: DeviceCodeResponse): Long = 
        response.interval * 1000L
}
```

**4. AzureAuthProvider.kt:**
```kotlin
class AzureAuthProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    @Named("azureTenantId") private val tenantId: String,
    @Named("azureClientId") private val clientId: String,
    @Named("azureUseCiam") private val useCiam: Boolean
) : AuthProvider {
    
    override val providerId = "azure"
    override val displayName = "Microsoft"
    override val displayNameResId = R.string.provider_microsoft
    override val iconResId = R.drawable.ic_microsoft
    override val brandColor = Color(0xFF00A4EF)
    
    private val baseUrl = if (useCiam) {
        "https://${tenantId.substringBefore(".")}.ciamlogin.com/$tenantId/oauth2/v2.0"
    } else {
        "https://login.microsoftonline.com/$tenantId/oauth2/v2.0"
    }
    
    private val deviceCodeUrl = "$baseUrl/devicecode"
    private val tokenUrl = "$baseUrl/token"
    private val scope = "openid profile email offline_access"
    
    override suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url(deviceCodeUrl)
                        .post(FormBody.Builder()
                            .add("client_id", clientId)
                            .add("scope", scope)
                            .build())
                        .build()
                ).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    // Azure は verification_uri を使用
                    val deviceCode = parseAzureDeviceCodeResponse(body)
                    Result.success(deviceCode)
                } else {
                    Result.failure(AuthException("Failed to get device code"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    private fun parseAzureDeviceCodeResponse(json: String?): DeviceCodeResponse {
        val jsonObject = JSONObject(json ?: "{}")
        return DeviceCodeResponse(
            deviceCode = jsonObject.getString("device_code"),
            userCode = jsonObject.getString("user_code"),
            // Azure は verification_uri を使用
            verificationUrl = jsonObject.getString("verification_uri"),
            expiresIn = jsonObject.getInt("expires_in"),
            interval = jsonObject.getInt("interval")
        )
    }
    
    override suspend fun pollToken(deviceCode: String): Result<TokenResponse> {
        // 実装
    }
    
    override fun buildQRCodeContent(response: DeviceCodeResponse): String {
        return "carauth://auth?provider=azure&url=${
            URLEncoder.encode(response.verificationUrl, "UTF-8")
        }&code=${response.userCode}"
    }
    
    override fun getPollingInterval(response: DeviceCodeResponse): Long = 
        response.interval * 1000L
}
```

**5. AuthProviderFactory.kt:**
```kotlin
class AuthProviderFactory @Inject constructor(
    private val googleProvider: GoogleAuthProvider,
    private val azureProvider: AzureAuthProvider
) {
    private val providers = mapOf(
        ProviderType.GOOGLE to googleProvider,
        ProviderType.AZURE to azureProvider
    )
    
    fun getProvider(type: ProviderType): AuthProvider = 
        providers[type] ?: throw IllegalArgumentException("Unknown provider: $type")
    
    fun getAllProviders(): List<AuthProvider> = providers.values.toList()
    
    fun getProviderById(id: String): AuthProvider? = 
        providers.entries.find { it.key.id == id }?.value
}
```

**6. AuthState.kt:**
```kotlin
sealed class AuthState {
    object Idle : AuthState()
    object SelectingProvider : AuthState()
    data class RequestingCode(val provider: AuthProvider) : AuthState()
    data class DisplayingQR(
        val provider: AuthProvider,
        val qrCodeBitmap: Bitmap,
        val userCode: String,
        val verificationUrl: String,
        val expiresAt: Long
    ) : AuthState()
    data class Polling(
        val provider: AuthProvider,
        val progress: Float
    ) : AuthState()
    data class Authenticated(
        val provider: AuthProvider,
        val accessToken: String,
        val userEmail: String?,
        val userName: String?
    ) : AuthState()
    data class Error(
        val provider: AuthProvider?,
        val message: String,
        val canRetry: Boolean = true
    ) : AuthState()
}
```

**7. ProviderSelectionScreen.kt:**
```kotlin
@Composable
fun ProviderSelectionScreen(
    providers: List<AuthProvider>,
    onProviderSelected: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.select_sign_in_method),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        providers.forEach { provider ->
            ProviderButton(
                provider = provider,
                onClick = { onProviderSelected(provider) },
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = stringResource(R.string.sign_in_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

@Composable
fun ProviderButton(
    provider: AuthProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .width(360.dp)
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = provider.brandColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(provider.iconResId),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(
                    R.string.sign_in_with_provider,
                    provider.displayName
                ),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}
```

**8. AuthModule.kt (Hilt DI):**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    
    @Provides
    @Named("googleClientId")
    fun provideGoogleClientId(): String = BuildConfig.GOOGLE_CLIENT_ID
    
    @Provides
    @Named("azureTenantId")
    fun provideAzureTenantId(): String = BuildConfig.AZURE_TENANT_ID
    
    @Provides
    @Named("azureClientId")
    fun provideAzureClientId(): String = BuildConfig.AZURE_CLIENT_ID
    
    @Provides
    @Named("azureUseCiam")
    fun provideAzureUseCiam(): Boolean = BuildConfig.AZURE_USE_CIAM.toBoolean()
    
    @Provides
    @Singleton
    fun provideGoogleAuthProvider(
        httpClient: OkHttpClient,
        @Named("googleClientId") clientId: String
    ): GoogleAuthProvider = GoogleAuthProvider(httpClient, clientId)
    
    @Provides
    @Singleton
    fun provideAzureAuthProvider(
        httpClient: OkHttpClient,
        @Named("azureTenantId") tenantId: String,
        @Named("azureClientId") clientId: String,
        @Named("azureUseCiam") useCiam: Boolean
    ): AzureAuthProvider = AzureAuthProvider(httpClient, tenantId, clientId, useCiam)
}
```

**9. build.gradle.kts:**
```kotlin
android {
    namespace = "com.example.carauth"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.example.carauth"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    
    buildTypes {
        debug {
            // Google OAuth
            buildConfigField("String", "GOOGLE_CLIENT_ID", "\"YOUR_GOOGLE_CLIENT_ID\"")
            
            // Azure AD External ID
            buildConfigField("String", "AZURE_TENANT_ID", "\"YOUR_AZURE_TENANT_ID\"")
            buildConfigField("String", "AZURE_CLIENT_ID", "\"YOUR_AZURE_CLIENT_ID\"")
            buildConfigField("String", "AZURE_USE_CIAM", "\"true\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 本番環境の設定
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Automotive
    implementation("androidx.car.app:app-automotive:1.4.0")
    
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // DI
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // QR Code
    implementation("com.google.zxing:core:3.5.2")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
```

**10. strings.xml:**
```xml
<resources>
    <string name="app_name">Car Auth</string>
    
    <!-- Provider Selection -->
    <string name="select_sign_in_method">サインイン方法を選択</string>
    <string name="sign_in_with_provider">%s でサインイン</string>
    <string name="sign_in_disclaimer">サインインすることで、利用規約とプライバシーポリシーに同意したものとみなされます。</string>
    
    <!-- Provider Names -->
    <string name="provider_google">Google</string>
    <string name="provider_microsoft">Microsoft</string>
    
    <!-- QR Code Screen -->
    <string name="scan_qr_code">スマートフォンでQRコードをスキャン</string>
    <string name="or_enter_code">または以下のコードを入力:</string>
    <string name="expires_in">残り時間: %1$d:%2$02d</string>
    <string name="cancel">キャンセル</string>
    
    <!-- Status -->
    <string name="waiting_for_auth">認証待機中...</string>
    <string name="auth_success">認証成功</string>
    <string name="auth_failed">認証失敗</string>
    <string name="retry">再試行</string>
    
    <!-- Errors -->
    <string name="error_network">ネットワークエラー</string>
    <string name="error_timeout">タイムアウト</string>
    <string name="error_access_denied">アクセスが拒否されました</string>
</resources>
```

### アイコンリソース

**ic_google.xml:**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12.545,10.239v3.821h5.445c-0.712,2.315 -2.647,3.972 -5.445,3.972c-3.332,0 -6.033,-2.701 -6.033,-6.032s2.701,-6.032 6.033,-6.032c1.498,0 2.866,0.549 3.921,1.453l2.814,-2.814C17.503,2.988 15.139,2 12.545,2C7.021,2 2.543,6.477 2.543,12s4.478,10 10.002,10c8.396,0 10.249,-7.85 9.426,-11.748L12.545,10.239z"/>
</vector>
```

**ic_microsoft.xml:**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M3,3h8v8H3V3zM13,3h8v8h-8V3zM3,13h8v8H3v-8zM13,13h8v8h-8v-8z"/>
</vector>
```

### テスト

```kotlin
@Test
fun `AuthProviderFactory should return correct provider for type`() {
    val factory = AuthProviderFactory(mockGoogleProvider, mockAzureProvider)
    
    assertEquals(mockGoogleProvider, factory.getProvider(ProviderType.GOOGLE))
    assertEquals(mockAzureProvider, factory.getProvider(ProviderType.AZURE))
}

@Test
fun `AzureAuthProvider should use CIAM endpoint when configured`() {
    val provider = AzureAuthProvider(
        httpClient = mockHttpClient,
        tenantId = "test-tenant",
        clientId = "test-client",
        useCiam = true
    )
    
    // CIAM URL が使用されていることを確認
    assertTrue(provider.deviceCodeUrl.contains("ciamlogin.com"))
}

@Test
fun `QR code content should include provider identifier`() {
    val response = DeviceCodeResponse(
        deviceCode = "test",
        userCode = "CODE123",
        verificationUrl = "https://test.com",
        expiresIn = 900,
        interval = 5
    )
    
    val googleContent = googleProvider.buildQRCodeContent(response)
    assertTrue(googleContent.contains("provider=google"))
    
    val azureContent = azureProvider.buildQRCodeContent(response)
    assertTrue(azureContent.contains("provider=azure"))
}
```

## 出力形式

完全に動作するAndroidプロジェクトを生成してください。すべてのファイルに適切なKotlinコードを含め、ビルドして実行できる状態にしてください。

## 注意事項

1. プロバイダー抽象化により将来の拡張（Apple ID等）が容易
2. 各プロバイダーのAPIレスポンス形式の違いを吸収
3. Azure AD の verification_uri と Google の verification_url の違いに対応
4. プロバイダー固有のエラーコードのハンドリング
5. ブランドガイドラインに準拠したボタンデザイン
```

---

## Azure AD External ID 設定チェックリスト

### Microsoft Entra 管理センター

- [ ] External ID テナントを作成（または既存を使用）
- [ ] App registration を作成
- [ ] Allow public client flows を有効化
- [ ] 必要なスコープを追加（openid, profile, email, offline_access）
- [ ] テナントID と クライアントID をメモ

### build.gradle.kts 設定

```kotlin
buildConfigField("String", "AZURE_TENANT_ID", "\"your-tenant-id\"")
buildConfigField("String", "AZURE_CLIENT_ID", "\"your-client-id\"")
buildConfigField("String", "AZURE_USE_CIAM", "\"true\"")  // CIAMの場合true
```

## 生成後のチェックリスト

- [ ] すべてのファイルが正しく配置されているか
- [ ] プロバイダー選択画面が表示されるか
- [ ] Google認証フローが動作するか
- [ ] Azure AD認証フローが動作するか
- [ ] QRコードにプロバイダー情報が含まれるか
- [ ] エラーハンドリングが適切か
- [ ] 各プロバイダーのアイコンが表示されるか
