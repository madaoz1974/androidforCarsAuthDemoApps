# Claude Code プロンプト: スマートフォン コンパニオンアプリ

## 概要

このプロンプトを使用して、車載システムと連携するスマートフォン用コンパニオンアプリを生成してください。QRコードスキャン機能とGoogle認証のWebView/CustomTabs連携を実装します。

---

## プロンプト

```
Android スマートフォン向けの車載認証コンパニオンアプリを作成してください。

## 要件

### 機能要件
1. QRコードスキャン機能
   - CameraX を使用したリアルタイムカメラプレビュー
   - ML Kit または ZXing でのQRコード解析
   - カスタムURLスキーム（carauth://）の解析
   - スキャン成功時の振動フィードバック

2. Google認証連携
   - Chrome Custom Tabs での認証ページ表示
   - または WebView での認証フロー
   - ユーザーコードの自動入力オプション
   - 認証完了の検知と表示

3. UI/UX
   - Jetpack Compose による最新UI
   - カメラプレビュー画面
   - スキャンガイド（フレーム表示）
   - 認証成功/失敗の結果画面
   - ダークモード対応

4. 状態管理
   - スキャン状態の管理
   - 認証フロー状態の管理
   - 権限リクエストの処理

### 技術仕様

**プロジェクト構成:**
- Package名: com.example.carcompanion
- 最小SDK: 24
- ターゲットSDK: 34
- Kotlin 1.9.x
- Compose BOM 2024.x

**アーキテクチャ:**
- MVVM + Clean Architecture
- Hilt for DI
- Kotlin Coroutines + Flow

**依存関係:**
- androidx.camera:camera-camera2 (CameraX)
- androidx.camera:camera-lifecycle
- androidx.camera:camera-view
- com.google.mlkit:barcode-scanning (QRスキャン)
- androidx.browser:browser (Chrome Custom Tabs)
- com.google.dagger:hilt-android
- androidx.navigation:navigation-compose

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
│   │   │   │   │   ├── AuthWebScreen.kt
│   │   │   │   │   └── ResultScreen.kt
│   │   │   │   └── components/
│   │   │   │       ├── CameraPreview.kt
│   │   │   │       ├── ScanOverlay.kt
│   │   │   │       ├── ScanGuideFrame.kt
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
│   │   │   │   └── CustomTabsHelper.kt
│   │   │   ├── navigation/
│   │   │   │   ├── NavGraph.kt
│   │   │   │   └── Screen.kt
│   │   │   └── util/
│   │   │       ├── PermissionUtils.kt
│   │   │       └── VibrationUtils.kt
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── drawable/
│   │   │       └── scan_frame.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### 実装詳細

**1. ScanResult.kt:**
```kotlin
sealed class ScanResult {
    object Idle : ScanResult()
    object Scanning : ScanResult()
    data class Success(
        val verificationUrl: String,
        val userCode: String
    ) : ScanResult()
    data class Error(val message: String) : ScanResult()
    data class InvalidFormat(val rawValue: String) : ScanResult()
}
```

**2. AuthUrlParser.kt:**
```kotlin
object AuthUrlParser {
    private const val SCHEME = "carauth"
    private const val HOST = "auth"
    
    fun parse(qrContent: String): ParseResult {
        // carauth://auth?url=https://...&code=XXXX-XXXX
        val uri = Uri.parse(qrContent)
        if (uri.scheme != SCHEME || uri.host != HOST) {
            return ParseResult.InvalidScheme
        }
        
        val verificationUrl = uri.getQueryParameter("url")
        val userCode = uri.getQueryParameter("code")
        
        return if (verificationUrl != null && userCode != null) {
            ParseResult.Success(verificationUrl, userCode)
        } else {
            ParseResult.MissingParameters
        }
    }
    
    sealed class ParseResult {
        data class Success(val url: String, val code: String) : ParseResult()
        object InvalidScheme : ParseResult()
        object MissingParameters : ParseResult()
    }
}
```

**3. QRCodeAnalyzer.kt:**
```kotlin
class QRCodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { value ->
                    onBarcodeDetected(value)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
```

**4. CameraPreview.kt:**
```kotlin
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onQRCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    DisposableEffect(lifecycleOwner) {
        val cameraProvider = cameraProviderFuture.get()
        
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    QRCodeAnalyzer(onQRCodeScanned)
                )
            }
        
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
        
        onDispose {
            cameraProvider.unbindAll()
        }
    }
    
    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
```

**5. CustomTabsHelper.kt:**
```kotlin
class CustomTabsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openAuthUrl(verificationUrl: String, userCode: String) {
        // ユーザーコードをURLに追加（可能な場合）
        val fullUrl = buildAuthUrl(verificationUrl, userCode)
        
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .build()
        
        customTabsIntent.launchUrl(context, Uri.parse(fullUrl))
    }
    
    private fun buildAuthUrl(baseUrl: String, userCode: String): String {
        return Uri.parse(baseUrl)
            .buildUpon()
            .appendQueryParameter("user_code", userCode)
            .build()
            .toString()
    }
}
```

**6. Screen.kt (ナビゲーション):**
```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scan : Screen("scan")
    object AuthWeb : Screen("auth_web/{url}/{code}") {
        fun createRoute(url: String, code: String): String {
            return "auth_web/${Uri.encode(url)}/${Uri.encode(code)}"
        }
    }
    object Result : Screen("result/{success}") {
        fun createRoute(success: Boolean): String = "result/$success"
    }
}
```

**7. AndroidManifest.xml:**
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <uses-feature android:name="android.hardware.camera" android:required="true"/>
    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.VIBRATE"/>
    
    <application
        android:name=".CompanionApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.CarCompanion">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.CarCompanion">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
            
            <!-- カスタムURLスキームの処理（オプション） -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.BROWSABLE"/>
                <data android:scheme="carauth" android:host="auth"/>
            </intent-filter>
        </activity>
        
    </application>
</manifest>
```

### UIデザイン要件

1. **ホーム画面:**
   - アプリ説明テキスト
   - 「QRコードをスキャン」ボタン（大きく目立つ）
   - 使い方の簡単な説明

2. **スキャン画面:**
   - フルスクリーンカメラプレビュー
   - 中央にスキャンガイドフレーム（角が強調されたデザイン）
   - 画面上部に説明テキスト「車載画面のQRコードをスキャン」
   - 画面下部に閉じるボタン
   - スキャン成功時の振動フィードバック

3. **認証Web画面:**
   - Chrome Custom Tabsで認証ページを表示
   - または WebView + 戻るボタン
   - ユーザーコードをコピー可能な形で表示

4. **結果画面:**
   - 成功: チェックマークアイコン + 成功メッセージ
   - 失敗: エラーアイコン + エラーメッセージ + 再試行ボタン
   - ホームに戻るボタン

### 権限処理

カメラ権限のリクエストと処理:
```kotlin
@Composable
fun PermissionRequest(
    permission: String,
    onPermissionGranted: @Composable () -> Unit,
    onPermissionDenied: @Composable () -> Unit
) {
    val permissionState = rememberPermissionState(permission)
    
    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }
    
    when {
        permissionState.status.isGranted -> onPermissionGranted()
        else -> onPermissionDenied()
    }
}
```

### テスト

以下のテストを含めてください:
- AuthUrlParserのユニットテスト
- QRCodeAnalyzerのテスト
- ViewModelのテスト
- UIテスト（Compose Test）

### アニメーション

1. スキャンフレームのパルスアニメーション
2. スキャン成功時のフェードトランジション
3. 結果画面のスケールアニメーション

## 出力形式

完全に動作するAndroidプロジェクトを生成してください。すべてのファイルに適切なKotlinコードを含め、ビルドして実行できる状態にしてください。

## 注意事項

1. カメラ権限の適切な処理（rationale表示含む）
2. ML Kit または ZXing の正しい実装
3. Chrome Custom Tabsのフォールバック処理
4. バッテリー効率を考慮したカメラ処理
5. ダークモード対応
6. 複数の画面サイズ対応
```

---

## 使用方法

1. 上記のプロンプトをClaude Codeにコピー&ペースト
2. 生成されたコードを `companion-app/` ディレクトリに配置
3. Android Studioでプロジェクトを開き、ビルド
4. 実機またはエミュレータで実行
5. 車載システムのQRコードをスキャンしてテスト

## 生成後のチェックリスト

- [ ] すべてのファイルが正しい場所に配置されているか
- [ ] build.gradle.ktsの依存関係が正しいか
- [ ] カメラ権限が正しく宣言されているか
- [ ] CameraXが正しく実装されているか
- [ ] QRコードスキャンが動作するか
- [ ] カスタムURLスキーム（carauth://）が解析できるか
- [ ] Chrome Custom Tabsが起動するか
- [ ] ダークモードで正しく表示されるか

## テスト用QRコードデータ

開発・テスト用に以下のQRコードを使用できます:

```
carauth://auth?url=https://www.google.com/device&code=TEST-CODE
```

このURLをQRコードジェネレータでQRコード化し、スキャンテストに使用してください。
