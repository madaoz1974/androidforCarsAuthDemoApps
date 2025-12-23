# マルチIDプロバイダー対応 拡張アーキテクチャ

## 概要

本ドキュメントでは、Google OAuth に加えて **Azure AD External ID（Microsoft Entra External ID）** を認証プロバイダーとして追加する拡張設計を説明します。

## 対応IDプロバイダー

| プロバイダー | 用途 | Device Flow対応 |
|-------------|------|----------------|
| Google OAuth 2.0 | 一般消費者向け | ✅ |
| Azure AD External ID | 企業・B2C向け | ✅ |
| (将来拡張) Apple ID | Apple エコシステム | ✅ |

## システムアーキテクチャ（拡張版）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    マルチIDプロバイダー認証フロー                              │
└─────────────────────────────────────────────────────────────────────────────┘

  ┌──────────────────┐                              
  │  Android for Cars │                              
  │  (車載システム)     │                              
  └────────┬─────────┘                              
           │                                         
           │ 1. IDプロバイダー選択画面表示            
           │    ┌─────────────────────┐              
           │    │ ● Google でログイン   │              
           │    │ ● Microsoft でログイン│              
           │    └─────────────────────┘              
           │                                         
           │ 2. 選択されたプロバイダーへ Device Code リクエスト
           │                                         
           ├─────────────────┬───────────────────────┐
           │                 │                       │
           ▼                 ▼                       ▼
  ┌─────────────┐   ┌─────────────┐         ┌─────────────┐
  │   Google    │   │  Azure AD   │         │   (将来)    │
  │   OAuth     │   │ External ID │         │  Apple ID   │
  └──────┬──────┘   └──────┬──────┘         └─────────────┘
         │                 │
         │ device_code     │ device_code
         │ user_code       │ user_code  
         │ verification_url│ verification_uri
         │                 │
         ▼                 ▼
           │                                         
           │ 3. QRコード生成（プロバイダー情報含む）     
           │    ┌─────────────────────────────────┐  
           │    │  QR: carauth://auth?            │  
           │    │      provider=azure&            │  
           │    │      url=...&code=...           │  
           │    └─────────────────────────────────┘  
           │                                         
           │ 4. スマートフォンでスキャン → 認証完了     
           │                                         
           │ 5. トークン取得（プロバイダー別処理）      
           ▼                                         
```

## Azure AD External ID 設定

### 1. Microsoft Entra 管理センターでの設定

```
1. https://entra.microsoft.com にアクセス
2. External Identities → External tenants に移動
3. 新しいテナントを作成（または既存を使用）
4. App registrations → New registration
5. 以下を設定:
   - Name: Car Auth System
   - Supported account types: Accounts in any organizational directory and personal Microsoft accounts
   - Redirect URI: (空欄のまま - Device Flowでは不要)
6. API permissions で以下を追加:
   - openid
   - profile
   - email
   - offline_access
7. Authentication → Advanced settings:
   - Allow public client flows: Yes (Device Code Flow有効化)
```

### 2. エンドポイント

**Azure AD External ID (CIAM) エンドポイント:**
```
Device Code: https://{tenant}.ciamlogin.com/{tenant_id}/oauth2/v2.0/devicecode
Token:       https://{tenant}.ciamlogin.com/{tenant_id}/oauth2/v2.0/token
```

**標準 Azure AD エンドポイント（B2B用）:**
```
Device Code: https://login.microsoftonline.com/{tenant_id}/oauth2/v2.0/devicecode
Token:       https://login.microsoftonline.com/{tenant_id}/oauth2/v2.0/token
```

### 3. API リクエスト/レスポンス

**Device Code リクエスト:**
```http
POST https://{tenant}.ciamlogin.com/{tenant_id}/oauth2/v2.0/devicecode
Content-Type: application/x-www-form-urlencoded

client_id={CLIENT_ID}
&scope=openid profile email offline_access
```

**Device Code レスポンス:**
```json
{
    "device_code": "GMMhmHCXhWEzkobqIHGG_EnNYYsAkukHspeYUk9E8...",
    "user_code": "FKWZJTBX",
    "verification_uri": "https://microsoft.com/devicelogin",
    "expires_in": 900,
    "interval": 5,
    "message": "To sign in, use a web browser to open the page https://microsoft.com/devicelogin and enter the code FKWZJTBX to authenticate."
}
```

**Token リクエスト（ポーリング）:**
```http
POST https://{tenant}.ciamlogin.com/{tenant_id}/oauth2/v2.0/token
Content-Type: application/x-www-form-urlencoded

client_id={CLIENT_ID}
&device_code={DEVICE_CODE}
&grant_type=urn:ietf:params:oauth:grant-type:device_code
```

## プロバイダー抽象化設計

### インターフェース定義

```kotlin
interface AuthProvider {
    val providerId: String
    val displayName: String
    val iconResId: Int
    
    suspend fun requestDeviceCode(): DeviceCodeResponse
    suspend fun pollToken(deviceCode: String): TokenResponse
    fun buildQRCodeContent(deviceCode: DeviceCodeResponse): String
}
```

### Google実装

```kotlin
class GoogleAuthProvider(
    private val httpClient: OkHttpClient
) : AuthProvider {
    override val providerId = "google"
    override val displayName = "Google"
    override val iconResId = R.drawable.ic_google
    
    private val deviceCodeUrl = "https://oauth2.googleapis.com/device/code"
    private val tokenUrl = "https://oauth2.googleapis.com/token"
    
    override suspend fun requestDeviceCode(): DeviceCodeResponse {
        // Google実装
    }
    
    override suspend fun pollToken(deviceCode: String): TokenResponse {
        // Google実装
    }
    
    override fun buildQRCodeContent(response: DeviceCodeResponse): String {
        return "carauth://auth?provider=google&url=${response.verificationUrl}&code=${response.userCode}"
    }
}
```

### Azure AD External ID実装

```kotlin
class AzureAuthProvider(
    private val httpClient: OkHttpClient,
    private val tenantId: String,
    private val clientId: String,
    private val useCiam: Boolean = true  // CIAM (External ID) or standard Azure AD
) : AuthProvider {
    override val providerId = "azure"
    override val displayName = "Microsoft"
    override val iconResId = R.drawable.ic_microsoft
    
    private val baseUrl = if (useCiam) {
        "https://{tenant}.ciamlogin.com/$tenantId/oauth2/v2.0"
    } else {
        "https://login.microsoftonline.com/$tenantId/oauth2/v2.0"
    }
    
    private val deviceCodeUrl = "$baseUrl/devicecode"
    private val tokenUrl = "$baseUrl/token"
    
    override suspend fun requestDeviceCode(): DeviceCodeResponse {
        val response = httpClient.newCall(
            Request.Builder()
                .url(deviceCodeUrl)
                .post(FormBody.Builder()
                    .add("client_id", clientId)
                    .add("scope", "openid profile email offline_access")
                    .build())
                .build()
        ).execute()
        
        // Azure のレスポンスをパース
        // verification_uri (Azureの場合) → verificationUrl に変換
    }
    
    override suspend fun pollToken(deviceCode: String): TokenResponse {
        // Azure Token取得実装
    }
    
    override fun buildQRCodeContent(response: DeviceCodeResponse): String {
        return "carauth://auth?provider=azure&url=${response.verificationUrl}&code=${response.userCode}"
    }
}
```

## QRコードフォーマット（拡張版）

```
carauth://auth?provider={provider}&url={verification_url}&code={user_code}
```

| パラメータ | 説明 | 例 |
|-----------|------|-----|
| provider | IDプロバイダー識別子 | `google`, `azure` |
| url | 認証URL | `https://microsoft.com/devicelogin` |
| code | ユーザーコード | `FKWZJTBX` |

## セキュリティ考慮事項（Azure AD追加分）

### 1. テナント検証
```kotlin
// 許可されたテナントIDのリスト
val allowedTenants = listOf(
    "your-external-id-tenant-id",
    "your-b2b-tenant-id"
)

fun validateTenant(tenantId: String): Boolean {
    return tenantId in allowedTenants
}
```

### 2. トークン検証（Azure AD固有）
```kotlin
// Azure AD トークンの issuer 検証
fun validateAzureToken(idToken: String): Boolean {
    val claims = decodeJwt(idToken)
    val issuer = claims["iss"] as String
    
    // CIAM の場合
    val expectedIssuer = "https://{tenant}.ciamlogin.com/{tenant_id}/v2.0"
    return issuer == expectedIssuer
}
```

### 3. Conditional Access 連携
Azure AD External ID では条件付きアクセスポリシーを設定可能：
- デバイスコンプライアンス要求
- 位置情報ベースのアクセス制御
- リスクベース認証

## エラーハンドリング比較

| エラー | Google | Azure AD | 対処 |
|-------|--------|----------|------|
| 認証待ち | `authorization_pending` | `authorization_pending` | ポーリング継続 |
| レート制限 | `slow_down` | `slow_down` | interval増加 |
| 拒否 | `access_denied` | `access_denied` | フロー終了 |
| 期限切れ | `expired_token` | `expired_token` | 最初からやり直し |
| 不正なクライアント | - | `invalid_client` | 設定確認 |
| テナントエラー | - | `invalid_tenant` | テナントID確認 |

## 環境変数（拡張版）

```kotlin
object AuthConfig {
    // Google
    val GOOGLE_CLIENT_ID = BuildConfig.GOOGLE_CLIENT_ID
    
    // Azure AD External ID
    val AZURE_TENANT_ID = BuildConfig.AZURE_TENANT_ID
    val AZURE_CLIENT_ID = BuildConfig.AZURE_CLIENT_ID
    val AZURE_USE_CIAM = BuildConfig.AZURE_USE_CIAM.toBoolean()
    
    // 共通
    val DEFAULT_PROVIDER = "google"  // デフォルトのプロバイダー
}
```

## build.gradle.kts 設定例

```kotlin
android {
    buildTypes {
        debug {
            buildConfigField("String", "GOOGLE_CLIENT_ID", "\"your-google-client-id\"")
            buildConfigField("String", "AZURE_TENANT_ID", "\"your-azure-tenant-id\"")
            buildConfigField("String", "AZURE_CLIENT_ID", "\"your-azure-client-id\"")
            buildConfigField("String", "AZURE_USE_CIAM", "\"true\"")
        }
        release {
            // 本番環境の設定
        }
    }
}
```

## UI拡張

### プロバイダー選択画面

```kotlin
@Composable
fun ProviderSelectionScreen(
    providers: List<AuthProvider>,
    onProviderSelected: (AuthProvider) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "サインイン方法を選択",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        providers.forEach { provider ->
            ProviderButton(
                provider = provider,
                onClick = { onProviderSelected(provider) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProviderButton(
    provider: AuthProvider,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(300.dp)
            .height(64.dp)
    ) {
        Icon(
            painter = painterResource(provider.iconResId),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            "${provider.displayName} でサインイン",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
```

## テスト戦略（追加）

### プロバイダー別テスト

```kotlin
@Test
fun `Azure AD device code request should return valid response`() {
    val provider = AzureAuthProvider(
        httpClient = mockHttpClient,
        tenantId = "test-tenant",
        clientId = "test-client"
    )
    
    val response = runBlocking { provider.requestDeviceCode() }
    
    assertNotNull(response.deviceCode)
    assertNotNull(response.userCode)
    assertEquals("https://microsoft.com/devicelogin", response.verificationUrl)
}
```

### Mock サーバー設定

```kotlin
// Azure AD Mock レスポンス
val azureMockResponse = """
{
    "device_code": "mock_device_code",
    "user_code": "TESTCODE",
    "verification_uri": "https://microsoft.com/devicelogin",
    "expires_in": 900,
    "interval": 5
}
"""
```

## 移行・互換性

既存のGoogle認証のみのシステムからの移行:

1. **段階的導入**: 既存のGoogle認証はそのまま維持
2. **Feature Flag**: Azure AD対応は Feature Flag で制御
3. **後方互換性**: 古いQRコード形式（provider省略）はGoogleとして処理

```kotlin
fun parseQRCode(content: String): AuthRequest {
    val uri = Uri.parse(content)
    val provider = uri.getQueryParameter("provider") ?: "google"  // 後方互換
    // ...
}
```
