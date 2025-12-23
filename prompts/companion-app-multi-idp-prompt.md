# Claude Code プロンプト: マルチIDプロバイダー対応 コンパニオンアプリ

## 概要

このプロンプトを使用して、Google OAuth と Azure AD External ID の両方に対応したスマートフォン用コンパニオンアプリを生成してください。

---

## プロンプト

```
Android スマートフォン向けのマルチIDプロバイダー対応コンパニオンアプリを作成してください。
車載システムのQRコードをスキャンし、Google または Microsoft の認証ページを開きます。

## 要件

### 機能要件

1. **QRコードスキャン**
   - CameraX によるリアルタイムプレビュー
   - ML Kit でのQRコード解析
   - プロバイダー情報の解析（google/azure）
   - カスタムURLスキーム（carauth://）対応

2. **マルチプロバイダー対応**
   - QRコードからプロバイダーを自動判定
   - Google認証ページ表示
   - Microsoft認証ページ表示
   - プロバイダー別のUI表示（アイコン、色）

3. **認証フロー**
   - Chrome Custom Tabs での認証ページ表示
   - ユーザーコードの表示（コピー機能付き）
   - 認証完了の検知
   - 成功/失敗の結果画面

4. **UI/UX**
   - Jetpack Compose
   - プロバイダー別のテーマカラー
   - ダークモード対応
   - 振動フィードバック

### QRコードフォーマット

```
carauth://auth?provider={provider}&url={verification_url}&code={user_code}
```

**パラメータ:**
- `provider`: `google` または `azure`
- `url`: 認証URL（URLエンコード済み）
- `code`: ユーザーコード

### 技術仕様

**プロジェクト構成:**
- Package名: com.example.carcompanion
- 最小SDK: 24
- ターゲットSDK: 34
- Kotlin 1.9.x
- Compose BOM 2024.x

**ディレクトリ構造:**
```
companion-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/carcompanion/
│   │   │   ├── CompanionApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── di/
│   │   │   │   └── AppModule.kt
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── screens/
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   ├── ScanScreen.kt
│   │   │   │   │   ├── ProviderConfirmScreen.kt
│   │   │   │   │   ├── AuthWebScreen.kt
│   │   │   │   │   └── ResultScreen.kt
│   │   │   │   └── components/
│   │   │   │       ├── CameraPreview.kt
│   │   │   │       ├── ScanOverlay.kt
│   │   │   │       ├── ScanGuideFrame.kt
│   │   │   │       ├── ProviderBadge.kt
│   │   │   │       ├── UserCodeCard.kt
│   │   │   │       └── PermissionRequest.kt
│   │   │   ├── viewmodel/
│   │   │   │   ├── ScanViewModel.kt
│   │   │   │   └── AuthViewModel.kt
│   │   │   ├── scanner/
│   │   │   │   ├── QRCodeAnalyzer.kt
│   │   │   │   ├── BarcodeScanner.kt
│   │   │   │   └── ScanResult.kt
│   │   │   ├── auth/
│   │   │   │   ├── AuthUrlParser.kt
│   │   │   │   ├── ProviderInfo.kt
│   │   │   │   └── CustomTabsHelper.kt
│   │   │   ├── navigation/
│   │   │   │   ├── NavGraph.kt
│   │   │   │   └── Screen.kt
│   │   │   └── util/
│   │   │       ├── PermissionUtils.kt
│   │   │       ├── ClipboardUtils.kt
│   │   │       └── VibrationUtils.kt
│   │   ├── res/
│   │   │   ├── drawable/
│   │   │   │   ├── ic_google.xml
│   │   │   │   ├── ic_microsoft.xml
│   │   │   │   └── scan_frame.xml
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── values-night/
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### 実装詳細

**1. ProviderInfo.kt:**
```kotlin
enum class ProviderType(val id: String) {
    GOOGLE("google"),
    AZURE("azure"),
    UNKNOWN("unknown");
    
    companion object {
        fun fromId(id: String): ProviderType = 
            values().find { it.id == id } ?: UNKNOWN
    }
}

data class ProviderInfo(
    val type: ProviderType,
    val displayName: String,
    val iconResId: Int,
    val brandColor: Color,
    val verificationUrl: String,
    val userCode: String
) {
    companion object {
        fun fromParseResult(result: AuthUrlParser.ParseResult.Success): ProviderInfo {
            val type = ProviderType.fromId(result.provider)
            return when (type) {
                ProviderType.GOOGLE -> ProviderInfo(
                    type = type,
                    displayName = "Google",
                    iconResId = R.drawable.ic_google,
                    brandColor = Color(0xFF4285F4),
                    verificationUrl = result.url,
                    userCode = result.code
                )
                ProviderType.AZURE -> ProviderInfo(
                    type = type,
                    displayName = "Microsoft",
                    iconResId = R.drawable.ic_microsoft,
                    brandColor = Color(0xFF00A4EF),
                    verificationUrl = result.url,
                    userCode = result.code
                )
                ProviderType.UNKNOWN -> ProviderInfo(
                    type = type,
                    displayName = "Unknown",
                    iconResId = R.drawable.ic_question,
                    brandColor = Color.Gray,
                    verificationUrl = result.url,
                    userCode = result.code
                )
            }
        }
    }
}
```

**2. AuthUrlParser.kt:**
```kotlin
object AuthUrlParser {
    private const val SCHEME = "carauth"
    private const val HOST = "auth"
    
    fun parse(qrContent: String): ParseResult {
        return try {
            val uri = Uri.parse(qrContent)
            
            // スキームとホストの検証
            if (uri.scheme != SCHEME || uri.host != HOST) {
                return ParseResult.InvalidScheme(qrContent)
            }
            
            val provider = uri.getQueryParameter("provider")
            val url = uri.getQueryParameter("url")
            val code = uri.getQueryParameter("code")
            
            when {
                url == null || code == null -> ParseResult.MissingParameters
                provider == null -> {
                    // 後方互換性: providerがない場合はGoogleとみなす
                    ParseResult.Success(
                        provider = "google",
                        url = URLDecoder.decode(url, "UTF-8"),
                        code = code
                    )
                }
                else -> ParseResult.Success(
                    provider = provider,
                    url = URLDecoder.decode(url, "UTF-8"),
                    code = code
                )
            }
        } catch (e: Exception) {
            ParseResult.InvalidFormat(qrContent, e.message)
        }
    }
    
    sealed class ParseResult {
        data class Success(
            val provider: String,
            val url: String,
            val code: String
        ) : ParseResult()
        
        data class InvalidScheme(val rawValue: String) : ParseResult()
        object MissingParameters : ParseResult()
        data class InvalidFormat(val rawValue: String, val error: String?) : ParseResult()
    }
}
```

**3. ScanResult.kt:**
```kotlin
sealed class ScanResult {
    object Idle : ScanResult()
    object Scanning : ScanResult()
    data class Success(val providerInfo: ProviderInfo) : ScanResult()
    data class UnknownProvider(val provider: String, val url: String, val code: String) : ScanResult()
    data class InvalidQRCode(val rawValue: String) : ScanResult()
    data class Error(val message: String) : ScanResult()
}
```

**4. ScanViewModel.kt:**
```kotlin
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val vibrationUtils: VibrationUtils
) : ViewModel() {
    
    private val _scanResult = MutableStateFlow<ScanResult>(ScanResult.Idle)
    val scanResult: StateFlow<ScanResult> = _scanResult.asStateFlow()
    
    private var lastScannedValue: String? = null
    
    fun onQRCodeScanned(rawValue: String) {
        // 同じQRコードの連続スキャンを防止
        if (rawValue == lastScannedValue) return
        lastScannedValue = rawValue
        
        vibrationUtils.vibrate(50)
        
        when (val parseResult = AuthUrlParser.parse(rawValue)) {
            is AuthUrlParser.ParseResult.Success -> {
                val providerInfo = ProviderInfo.fromParseResult(parseResult)
                if (providerInfo.type == ProviderType.UNKNOWN) {
                    _scanResult.value = ScanResult.UnknownProvider(
                        provider = parseResult.provider,
                        url = parseResult.url,
                        code = parseResult.code
                    )
                } else {
                    _scanResult.value = ScanResult.Success(providerInfo)
                }
            }
            is AuthUrlParser.ParseResult.InvalidScheme -> {
                _scanResult.value = ScanResult.InvalidQRCode(rawValue)
            }
            is AuthUrlParser.ParseResult.MissingParameters -> {
                _scanResult.value = ScanResult.Error("QRコードに必要な情報が含まれていません")
            }
            is AuthUrlParser.ParseResult.InvalidFormat -> {
                _scanResult.value = ScanResult.InvalidQRCode(rawValue)
            }
        }
    }
    
    fun resetScan() {
        lastScannedValue = null
        _scanResult.value = ScanResult.Scanning
    }
    
    fun startScanning() {
        _scanResult.value = ScanResult.Scanning
    }
}
```

**5. ProviderConfirmScreen.kt:**
```kotlin
@Composable
fun ProviderConfirmScreen(
    providerInfo: ProviderInfo,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // プロバイダーアイコン
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = providerInfo.brandColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(providerInfo.iconResId),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "${providerInfo.displayName} でサインイン",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ユーザーコードカード
        UserCodeCard(
            userCode = providerInfo.userCode,
            brandColor = providerInfo.brandColor,
            onCopyClick = {
                clipboardManager.setText(AnnotatedString(providerInfo.userCode))
                Toast.makeText(context, "コードをコピーしました", Toast.LENGTH_SHORT).show()
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "認証ページでこのコードを入力してください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 認証ページを開くボタン
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = providerInfo.brandColor
            )
        ) {
            Text(
                text = "認証ページを開く",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onCancel) {
            Text("キャンセル")
        }
    }
}

@Composable
fun UserCodeCard(
    userCode: String,
    brandColor: Color,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = brandColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "認証コード",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = userCode,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
            
            IconButton(onClick = onCopyClick) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "コピー"
                )
            }
        }
    }
}
```

**6. CustomTabsHelper.kt:**
```kotlin
class CustomTabsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openAuthUrl(providerInfo: ProviderInfo) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(providerInfo.brandColor.toArgb())
                    .build()
            )
            .build()
        
        try {
            customTabsIntent.launchUrl(
                context,
                Uri.parse(providerInfo.verificationUrl)
            )
        } catch (e: Exception) {
            // フォールバック: 通常のブラウザで開く
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(providerInfo.verificationUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
```

**7. NavGraph.kt:**
```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scan : Screen("scan")
    object ProviderConfirm : Screen("provider_confirm")
    object Result : Screen("result/{success}") {
        fun createRoute(success: Boolean) = "result/$success"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    val scanViewModel: ScanViewModel = hiltViewModel()
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartScan = {
                    scanViewModel.startScanning()
                    navController.navigate(Screen.Scan.route)
                }
            )
        }
        
        composable(Screen.Scan.route) {
            val scanResult by scanViewModel.scanResult.collectAsState()
            
            ScanScreen(
                scanResult = scanResult,
                onQRCodeScanned = { scanViewModel.onQRCodeScanned(it) },
                onClose = { navController.popBackStack() }
            )
            
            // スキャン成功時に確認画面へ遷移
            LaunchedEffect(scanResult) {
                if (scanResult is ScanResult.Success) {
                    navController.navigate(Screen.ProviderConfirm.route)
                }
            }
        }
        
        composable(Screen.ProviderConfirm.route) {
            val scanResult by scanViewModel.scanResult.collectAsState()
            val providerInfo = (scanResult as? ScanResult.Success)?.providerInfo
            
            if (providerInfo != null) {
                val customTabsHelper: CustomTabsHelper = hiltViewModel<AuthViewModel>().customTabsHelper
                
                ProviderConfirmScreen(
                    providerInfo = providerInfo,
                    onConfirm = {
                        customTabsHelper.openAuthUrl(providerInfo)
                        navController.navigate(Screen.Result.createRoute(true))
                    },
                    onCancel = {
                        scanViewModel.resetScan()
                        navController.popBackStack(Screen.Home.route, false)
                    }
                )
            }
        }
        
        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("success") { type = NavType.BoolType })
        ) { backStackEntry ->
            val success = backStackEntry.arguments?.getBoolean("success") ?: false
            
            ResultScreen(
                success = success,
                onDone = {
                    scanViewModel.resetScan()
                    navController.popBackStack(Screen.Home.route, false)
                },
                onRetry = {
                    scanViewModel.resetScan()
                    navController.popBackStack(Screen.Scan.route, false)
                }
            )
        }
    }
}
```

**8. ScanScreen.kt:**
```kotlin
@Composable
fun ScanScreen(
    scanResult: ScanResult,
    onQRCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // カメラプレビュー
        CameraPreview(
            onQRCodeScanned = onQRCodeScanned,
            modifier = Modifier.fillMaxSize()
        )
        
        // スキャンオーバーレイ
        ScanOverlay(
            scanResult = scanResult,
            modifier = Modifier.fillMaxSize()
        )
        
        // 上部バー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "閉じる",
                    tint = Color.White
                )
            }
            
            Text(
                text = "QRコードをスキャン",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.size(48.dp))
        }
        
        // 下部の説明
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "車載画面のQRコードを\nフレーム内に合わせてください",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 対応プロバイダーのアイコン
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ProviderBadge(
                    iconResId = R.drawable.ic_google,
                    name = "Google",
                    color = Color(0xFF4285F4)
                )
                ProviderBadge(
                    iconResId = R.drawable.ic_microsoft,
                    name = "Microsoft",
                    color = Color(0xFF00A4EF)
                )
            }
        }
    }
}

@Composable
fun ProviderBadge(
    iconResId: Int,
    name: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
```

**9. strings.xml:**
```xml
<resources>
    <string name="app_name">Car Companion</string>
    
    <!-- Home Screen -->
    <string name="home_title">車載認証アシスタント</string>
    <string name="home_description">車載画面に表示されたQRコードをスキャンして、Googleまたはマイクロソフトアカウントでサインインできます。</string>
    <string name="start_scan">QRコードをスキャン</string>
    
    <!-- Scan Screen -->
    <string name="scan_title">QRコードをスキャン</string>
    <string name="scan_instruction">車載画面のQRコードを\nフレーム内に合わせてください</string>
    <string name="close">閉じる</string>
    
    <!-- Provider Confirm Screen -->
    <string name="sign_in_with">%s でサインイン</string>
    <string name="auth_code">認証コード</string>
    <string name="enter_code_instruction">認証ページでこのコードを入力してください</string>
    <string name="open_auth_page">認証ページを開く</string>
    <string name="cancel">キャンセル</string>
    <string name="code_copied">コードをコピーしました</string>
    
    <!-- Result Screen -->
    <string name="auth_success">認証を開始しました</string>
    <string name="auth_success_message">ブラウザで認証を完了してください。\n完了後、車載画面が自動的に更新されます。</string>
    <string name="auth_failed">エラーが発生しました</string>
    <string name="done">完了</string>
    <string name="retry">再試行</string>
    
    <!-- Errors -->
    <string name="error_invalid_qr">このQRコードは対応していません</string>
    <string name="error_unknown_provider">不明なプロバイダー: %s</string>
    <string name="error_camera_permission">カメラの使用を許可してください</string>
    
    <!-- Permission -->
    <string name="permission_camera_title">カメラへのアクセス</string>
    <string name="permission_camera_rationale">QRコードをスキャンするためにカメラへのアクセスが必要です。</string>
    <string name="grant_permission">許可する</string>
</resources>
```

**10. build.gradle.kts:**
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.carcompanion"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.example.carcompanion"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // Chrome Custom Tabs
    implementation("androidx.browser:browser:1.7.0")
    
    // DI
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Accompanist (Permissions)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

### テスト

```kotlin
class AuthUrlParserTest {
    
    @Test
    fun `parse valid Google QR code`() {
        val qrContent = "carauth://auth?provider=google&url=https%3A%2F%2Fwww.google.com%2Fdevice&code=ABCD-1234"
        
        val result = AuthUrlParser.parse(qrContent)
        
        assertTrue(result is AuthUrlParser.ParseResult.Success)
        val success = result as AuthUrlParser.ParseResult.Success
        assertEquals("google", success.provider)
        assertEquals("https://www.google.com/device", success.url)
        assertEquals("ABCD-1234", success.code)
    }
    
    @Test
    fun `parse valid Azure QR code`() {
        val qrContent = "carauth://auth?provider=azure&url=https%3A%2F%2Fmicrosoft.com%2Fdevicelogin&code=FKWZJTBX"
        
        val result = AuthUrlParser.parse(qrContent)
        
        assertTrue(result is AuthUrlParser.ParseResult.Success)
        val success = result as AuthUrlParser.ParseResult.Success
        assertEquals("azure", success.provider)
        assertEquals("https://microsoft.com/devicelogin", success.url)
        assertEquals("FKWZJTBX", success.code)
    }
    
    @Test
    fun `parse legacy QR code without provider defaults to Google`() {
        val qrContent = "carauth://auth?url=https%3A%2F%2Fwww.google.com%2Fdevice&code=TEST-CODE"
        
        val result = AuthUrlParser.parse(qrContent)
        
        assertTrue(result is AuthUrlParser.ParseResult.Success)
        val success = result as AuthUrlParser.ParseResult.Success
        assertEquals("google", success.provider)
    }
    
    @Test
    fun `parse invalid scheme returns error`() {
        val qrContent = "https://example.com"
        
        val result = AuthUrlParser.parse(qrContent)
        
        assertTrue(result is AuthUrlParser.ParseResult.InvalidScheme)
    }
}

class ProviderInfoTest {
    
    @Test
    fun `fromParseResult creates correct Google provider info`() {
        val parseResult = AuthUrlParser.ParseResult.Success(
            provider = "google",
            url = "https://www.google.com/device",
            code = "TEST-CODE"
        )
        
        val info = ProviderInfo.fromParseResult(parseResult)
        
        assertEquals(ProviderType.GOOGLE, info.type)
        assertEquals("Google", info.displayName)
    }
    
    @Test
    fun `fromParseResult creates correct Azure provider info`() {
        val parseResult = AuthUrlParser.ParseResult.Success(
            provider = "azure",
            url = "https://microsoft.com/devicelogin",
            code = "TEST-CODE"
        )
        
        val info = ProviderInfo.fromParseResult(parseResult)
        
        assertEquals(ProviderType.AZURE, info.type)
        assertEquals("Microsoft", info.displayName)
    }
}
```

## 出力形式

完全に動作するAndroidプロジェクトを生成してください。すべてのファイルに適切なKotlinコードを含め、ビルドして実行できる状態にしてください。

## 注意事項

1. プロバイダーに応じた適切なUI表示（色、アイコン）
2. 後方互換性（providerパラメータなしのQRコード対応）
3. 不明なプロバイダーの適切なエラーハンドリング
4. ユーザーコードのコピー機能
5. Chrome Custom Tabsのフォールバック処理
6. ダークモード対応
```

---

## テスト用QRコードデータ

### Google用
```
carauth://auth?provider=google&url=https%3A%2F%2Fwww.google.com%2Fdevice&code=GQVQ-JKEC
```

### Azure AD用
```
carauth://auth?provider=azure&url=https%3A%2F%2Fmicrosoft.com%2Fdevicelogin&code=FKWZJTBX
```

### レガシー（Google、provider省略）
```
carauth://auth?url=https%3A%2F%2Fwww.google.com%2Fdevice&code=TEST-CODE
```

## 生成後のチェックリスト

- [ ] QRコードスキャンが動作するか
- [ ] Google用QRコードでGoogle認証ページが開くか
- [ ] Azure用QRコードでMicrosoft認証ページが開くか
- [ ] プロバイダーに応じたアイコン・色が表示されるか
- [ ] ユーザーコードがコピーできるか
- [ ] ダークモードで正しく表示されるか
- [ ] カメラ権限のハンドリングが適切か
