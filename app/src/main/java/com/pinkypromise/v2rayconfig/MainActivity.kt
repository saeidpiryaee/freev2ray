package com.pinkypromise.v2rayconfig

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.MobileAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader
import com.yandex.mobile.ads.appopenad.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay


data class ShowMessageResponse(val show: Boolean, val message: String)
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}

    }



}

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var sharedPreferences: SharedPreferences
    private var userScore = 0
    private var serverUrl: String = ""



    private val rewardedAdUnitId = "R-M-16460619-4"
   // private val interstitialAdUnitId = "R-M-16460619-2"
    private val appOpenAdUnitId = "R-M-16460619-3"

    private var rewardedAd: RewardedAd? = null
    private var rewardedAdLoader: RewardedAdLoader? = null

   // private var interstitialAd: InterstitialAd? = null
   // private var interstitialAdLoader: InterstitialAdLoader? = null

    private var appOpenAd: AppOpenAd? = null
    private var appOpenAdLoader: AppOpenAdLoader? = null
    private var currentScoreState: MutableState<Int>? = null
    private var hiddenConfig: String? = null





    private suspend fun fetchRemoteServerUrl(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://raw.githubusercontent.com/kiowgarden/config-data/main/url.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val encoded = connection.inputStream.bufferedReader().readText().trim()
                    val decodedBytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                    String(decodedBytes)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to fetch or decode server URL", e)
                null
            }
        }
    }


    private fun launchReviewFlow() {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    Log.d(TAG, "In-app review flow completed")
                }
            } else {
                Log.e(TAG, "Review flow failed: ${task.exception?.message}")
                // fallback: open Play Store page
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val launchCount = sharedPreferences.getInt("launchCount", 0) + 1
        sharedPreferences.edit().putInt("launchCount", launchCount).apply()


        hiddenConfig = sharedPreferences.getString("hiddenConfig", null)


        userScore = sharedPreferences.getInt("userScore", 0)
        var serverUrlReady = mutableStateOf(false)
        var showMessageFromServer: ShowMessageResponse? = null

        // Load Ads1
        initYandexAds()
        lifecycleScope.launch {
            val remoteUrl = fetchRemoteServerUrl()
            if (remoteUrl != null) {
                serverUrl = remoteUrl
                showMessageFromServer = fetchShowMessage()
                serverUrlReady.value = true
            } else {
                Toast.makeText(this@MainActivity, "Failed to load server URL", Toast.LENGTH_LONG).show()
            }
        }





        setContent {
            // Show loading UI until serverUrl and message are ready
            val serverInitialized = remember { mutableStateOf(false) }
            val messageResponse = remember { mutableStateOf<ShowMessageResponse?>(null) }
            val showRatingDialog = remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val hasRated = sharedPreferences.getBoolean("hasRated", false)
                if (!hasRated && launchCount >= 3) {
                    showRatingDialog.value = true
                }

                val remoteUrl = fetchRemoteServerUrl()
                if (remoteUrl != null) {
                    serverUrl = remoteUrl
                    val result = fetchShowMessage()
                    messageResponse.value = result
                } else {
                    Toast.makeText(this@MainActivity, "Failed to load config server", Toast.LENGTH_LONG).show()
                }
                serverInitialized.value = true
            }


            // Load server URL and message onceee
            LaunchedEffect(Unit) {
                val remoteUrl = fetchRemoteServerUrl()
                if (remoteUrl != null) {
                    serverUrl = remoteUrl
                    val result = fetchShowMessage()
                    messageResponse.value = result
                } else {
                    Toast.makeText(this@MainActivity, "Failed to load config server", Toast.LENGTH_LONG).show()
                }
                serverInitialized.value = true
            }

            // Show loading spinner until ready
            if (!serverInitialized.value) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@setContent
            }

            // After initialized
            val scoreState = remember { mutableStateOf(userScore) }
            currentScoreState = scoreState
            var currentScore by scoreState

            var isAdAvailable by remember { mutableStateOf(rewardedAd != null) }
            var serverThreshold by remember { mutableStateOf(0) }
            var config by remember { mutableStateOf<String?>(null) }

            var showMessage by remember { mutableStateOf(false) }
            var messageText by remember { mutableStateOf("") }
            var bellVisible by remember { mutableStateOf(false) }

            // Set bell/message state once
            LaunchedEffect(messageResponse.value) {
                messageResponse.value?.let { msg ->
                    if (msg.show && msg.message.isNotEmpty()) {
                        bellVisible = true
                        messageText = msg.message
                    }
                }
            }

            var language by remember { mutableStateOf(sharedPreferences.getString("language", "en") ?: "en") }
            var showLanguageDialog by remember { mutableStateOf(!sharedPreferences.getBoolean("languageSelected", false)) }
            var showNotificationPromptDialog by remember { mutableStateOf(false) }

            val coroutineScope = rememberCoroutineScope()

            if (showLanguageDialog) {
                LanguageSelectionDialog {
                    language = it
                    saveLanguagePreference(it)
                    sharedPreferences.edit().putBoolean("languageSelected", true).apply()
                    showLanguageDialog = false
                    showNotificationPromptDialog = true
                }
            }

            if (showNotificationPromptDialog) {
                NotificationPermissionPrompt(
                    language = language,
                    onAllow = {
                        requestNotificationPermissionIfNeeded()
                        sharedPreferences.edit().putBoolean("shouldAskNotification", false).apply()
                        showNotificationPromptDialog = false
                    },
                    onNeverAsk = {
                        sharedPreferences.edit().putBoolean("shouldAskNotification", false).apply()
                        showNotificationPromptDialog = false
                    },
                    onDismiss = {
                        showNotificationPromptDialog = false
                    }
                )
            }

            if (showRatingDialog.value) {
                RatingPromptDialog(
                    onRateNow = {
                        launchReviewFlow()
                        sharedPreferences.edit().putBoolean("hasRated", true).apply()
                        showRatingDialog.value = false
                    },
                    onLater = {
                        showRatingDialog.value = false
                    }
                )
            }




            AppContent(
                currentScore = currentScore,
                config = config,
                onReceiveConfig = {
                    coroutineScope.launch {
                        serverThreshold = fetchServerThreshold() ?: 0
                        if (currentScore >= serverThreshold) {
                            val fetched = fetchConfig()
                            if (fetched != null) {
                                config = fetched
                                currentScore -= serverThreshold
                                userScore = currentScore // <--- ADD THIS LINE
                                saveUserScore(currentScore)
                                currentScoreState?.value = currentScore

                            }
                        } else {
                            val adsLeft = (serverThreshold - currentScore).coerceAtLeast(1)
                            val msg = if (language == "fa") {
                                "شما باید $adsLeft تبلیغ دیگر ببینید"
                            } else {
                                "You need to watch $adsLeft more ads"
                            }
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onShowAd = {
                    if (rewardedAd != null) showRewardedAd()
                    else {
                        val msg = if (language == "fa")
                            "تبلیغ آماده نیست. لطفاً بعداً دوباره امتحان کنید."
                        else
                            "Ad not ready. Please try again later."

                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }

                }
                ,
                onRetryAdCache = {
                    initYandexAds()
                },
                isAdAvailable = rewardedAd != null,
                serverThreshold = serverThreshold,
                showMessage = showMessage,
                messageText = messageText,
                bellVisible = bellVisible,
                onDismissMessage = {
                    showMessage = false
                    bellVisible = false
                },
                onShowMessageChange = {
                    showMessage = true
                },
                language = language,
                onLanguageChange = {
                    language = it
                    saveLanguagePreference(it)
                },
                onCopyConfig = {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Config", it))
                    Toast.makeText(this@MainActivity, if (language == "fa") "کپی شد!" else "Copied!", Toast.LENGTH_SHORT).show()
                },
                bannerHeight = null
            )
        }



        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                showAppOpenAd()
            }
        })



        // App content rendering here... (AppContent composable logic remains similar)
    }
    private fun saveHiddenConfig(config: String) {
        hiddenConfig = config
        sharedPreferences.edit().putString("hiddenConfig", config).apply()
    }

    private fun initYandexAds() {
        rewardedAdLoader = RewardedAdLoader(this).apply {
            setAdLoadListener(object : RewardedAdLoadListener {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    Log.e(TAG, "RewardedAd failed: ${error.description}")
                }
            })
        }

        appOpenAdLoader = AppOpenAdLoader(application).apply {
            setAdLoadListener(object : AppOpenAdLoadListener {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    Log.e(TAG, "AppOpenAd failed: ${error.description}")
                }
            })
        }

        val rewardedRequest = com.yandex.mobile.ads.common.AdRequestConfiguration.Builder(rewardedAdUnitId).build()
        val appOpenRequest = com.yandex.mobile.ads.common.AdRequestConfiguration.Builder(appOpenAdUnitId).build()

        rewardedAdLoader?.loadAd(rewardedRequest)
        appOpenAdLoader?.loadAd(appOpenRequest)
    }

    private fun showRewardedAd() {
        rewardedAd?.apply {
            setAdEventListener(object : RewardedAdEventListener {
                override fun onAdShown() {}
                override fun onAdFailedToShow(adError: AdError) {}
                override fun onAdDismissed() {
                    rewardedAd?.setAdEventListener(null)
                    rewardedAd = null
                    rewardedAdLoader?.loadAd(
                        com.yandex.mobile.ads.common.AdRequestConfiguration.Builder(rewardedAdUnitId).build()
                    )
                }
                override fun onAdClicked() {}
                override fun onAdImpression(data: ImpressionData?) {}
                override fun onRewarded(reward: Reward) {
                    userScore++
                    currentScoreState?.value = userScore
                    saveUserScore(userScore)
                    val language = sharedPreferences.getString("language", "en") ?: "en"
                    val earnedText = if (language == "fa") "شما یک امتیاز گرفتید!" else "You earned 1 point!"
                    Toast.makeText(this@MainActivity, earnedText, Toast.LENGTH_SHORT).show()


                }
            })
            show(this@MainActivity)
        }
    }

//    private fun showInterstitialAd() {
//        interstitialAd?.apply {
//            setAdEventListener(object : InterstitialAdEventListener {
//                override fun onAdShown() {}
//                override fun onAdFailedToShow(adError: AdError) {}
//                override fun onAdDismissed() {
//                    interstitialAd?.setAdEventListener(null)
//                    interstitialAd = null
//                    interstitialAdLoader?.loadAd(
//                        com.yandex.mobile.ads.common.AdRequestConfiguration.Builder(interstitialAdUnitId).build()
//                    )
//                }
//                override fun onAdClicked() {}
//                override fun onAdImpression(data: ImpressionData?) {}
//            })
//            show(this@MainActivity)
//        }
//    }

    private fun showAppOpenAd() {
        appOpenAd?.apply {
            setAdEventListener(object : AppOpenAdEventListener {
                override fun onAdShown() {}
                override fun onAdFailedToShow(adError: AdError) {}
                override fun onAdDismissed() {
                    appOpenAd?.setAdEventListener(null)
                    appOpenAd = null
                    appOpenAdLoader?.loadAd(
                        com.yandex.mobile.ads.common.AdRequestConfiguration.Builder(appOpenAdUnitId).build()
                    )
                }
                override fun onAdClicked() {}
                override fun onAdImpression(data: ImpressionData?) {}
            })
            show(this@MainActivity)
        }
    }

    private fun saveUserScore(score: Int) {
        sharedPreferences.edit().putInt("userScore", score).apply()
    }

    private fun saveLanguagePreference(language: String) {
        sharedPreferences.edit().putString("language", language).apply()
    }

    private suspend fun fetchServerThreshold(): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$serverUrl/check_score")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText().trim()
                    Log.d(ContentValues.TAG, "Server threshold response: $response")
                    response.toIntOrNull()
                } else {
                    Log.d(ContentValues.TAG, "Error fetching server threshold: ${connection.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e(ContentValues.TAG, "Exception fetching server threshold", e)
                null
            }
        }
    }

    private suspend fun fetchConfig(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$serverUrl/get_config")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText().trim()
                    Log.d(ContentValues.TAG, "Config fetched: $response")
                    response
                } else {
                    Log.d(ContentValues.TAG, "Error fetching config: ${connection.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e(ContentValues.TAG, "Exception fetching config", e)
                null
            }
        }
    }

    private suspend fun fetchShowMessage(): ShowMessageResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$serverUrl/show_message")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText().trim()
                    Log.d(ContentValues.TAG, "Show message response: $response")
                    val jsonObject = JSONObject(response)
                    val show = jsonObject.optBoolean("show", false)
                    val message = jsonObject.optString("message", "")

                    ShowMessageResponse(show, message)
                } else {
                    Log.d(ContentValues.TAG, "Error fetching show message: ${connection.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e(ContentValues.TAG, "Exception fetching show message", e)
                null
            }
        }
    }



    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }


}





@Composable
fun LanguageSelectionDialog(onSelect: (String) -> Unit) {
    Dialog(onDismissRequest = { }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Choose your language / زبان خود را انتخاب کنید", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onSelect("fa") }, modifier = Modifier.fillMaxWidth()) {
                    Text("🇮🇷 فارسی")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onSelect("en") }, modifier = Modifier.fillMaxWidth()) {
                    Text("🇺🇸 English")
                }
            }
        }
    }
}



@Composable
fun NotificationPermissionPrompt(
    language: String,
    onAllow: () -> Unit,
    onNeverAsk: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (language == "fa")
                        "اجازه دهید زمانی که کانفیگ جدیدی منتشر شد به شما اطلاع دهیم. در غیر این صورت ممکن است از سرورهای جدید بی‌خبر بمانید!"
                    else
                        "Let us notify you when new configs are released. Otherwise, you might miss fresh servers!",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = onAllow, modifier = Modifier.weight(1f)) {
                        Text(if (language == "fa") "اجازه بده" else "Allow")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(if (language == "fa") "الان نه" else "Not Now")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onNeverAsk, modifier = Modifier.align(Alignment.End)) {
                    Text(
                        text = if (language == "fa") "دیگه نپرس" else "Never ask again",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}








@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    currentScore: Int,
    config: String?,
    onReceiveConfig: suspend () -> Unit,
    onShowAd: () -> Unit,
    onRetryAdCache: () -> Unit,
    isAdAvailable: Boolean,
    serverThreshold: Int,
    showMessage: Boolean,
    messageText: String,
    bellVisible: Boolean,
    onDismissMessage: () -> Unit,
    onShowMessageChange: () -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    onCopyConfig: (String) -> Unit,
    bannerHeight: Int?
) {
    val context = LocalContext.current

    val configLoading = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()


    val coroutineScope = rememberCoroutineScope()
    val alpha: Float by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000)
    )
    val rotation by animateFloatAsState(
        targetValue = if (showMessage) 10f else 0f,
        animationSpec = tween(durationMillis = 100)
    )
    val appName = if (language == "fa") "کانفیگ رایگان وی تو ری" else "V2RAY CONFIG"
    val receiveConfigText = if (language == "fa") "دریافت کانفیگ 📥" else "Receive Config 📥"
    val watchAdText = if (language == "fa") "تماشای تبلیغ 🎥" else "Watch Ad 🎥"
    val retryText = if (language == "fa") "تلاش دوباره برای بارگذاری تبلیغ" else "Retry Ad Load"
    val currentScoreText = if (language == "fa") "امتیاز فعلی: $currentScore" else "Current Score: $currentScore"
    val serverThresholdText = if (language == "fa") "حداقل امتیاز لازم: $serverThreshold" else "Minimum Required Score: $serverThreshold"
   // val bannerHeightDp = remember(bannerHeight) { (bannerHeight / context.resources.displayMetrics.density).toInt() }

    LaunchedEffect(isAdAvailable) {
        Log.d("MainActivity", "isAdAvailable changed to: $isAdAvailable")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appName, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E2761)),
                actions = {
                    Row {
                        Text(
                            text = "🇮🇷",
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clickable { onLanguageChange("fa") }
                                .padding(8.dp)
                        )
                        Text(
                            text = "🇺🇸",
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clickable { onLanguageChange("en") }
                                .padding(8.dp)
                        )
                    }
                    if (bellVisible) {
                        IconButton(
                            onClick = onShowMessageChange,
                            modifier = Modifier.graphicsLayer(rotationZ = rotation)
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notification Bell",
                                    tint = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color.Red, shape = CircleShape)
                                        .align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = "1",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E2761))
                    .padding(paddingValues)
                    .verticalScroll(scrollState),


                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(8.dp)
                        .graphicsLayer(alpha = alpha)
                )
                if (showMessage) {
                    MessageDialog(messageText, onDismissMessage)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        configLoading.value = true  // ✅ Trigger spinner before coroutine
                        coroutineScope.launch {
                            onReceiveConfig()
                            delay(300)
                            configLoading.value = false
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                    ,
                    enabled = !configLoading.value,

                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF408EC6),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    if (configLoading.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(receiveConfigText)
                    }

                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onShowAd() },
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAdAvailable) Color(0xFF7A2048) else Color(0xFF757575),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = watchAdText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        if (!isAdAvailable) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onRetryAdCache() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(retryText)
                    }
                }
                if (!isAdAvailable) {
                    Text(
                        text = if (language == "fa")
                            "تبلیغ در حال بارگذاری است... اگر زمان زیادی طول کشید، لطفاً از وی پی ان استفاده کنید یا دکمه تلاش دوباره را بزنید."
                        else
                            "Ad is loading... If it takes too long, please use a VPN or press Retry Ad Load.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                config?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val qrCodeBitmap = generateQRCode(it)
                    qrCodeBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(150.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onCopyConfig(it) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8E9AAF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .width(150.dp)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (language == "fa") "کپی کانفیگ" else "Copy Config")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = currentScoreText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    modifier = Modifier.padding(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = serverThresholdText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    )
}

@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit) {





    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                IconButton(onClick = onDismiss) {
                    Text("✖", color = Color.Red, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Black, fontSize = 14.sp)
                )
            }
        }
    }
}


@Composable
fun RatingPromptDialog(
    onRateNow: () -> Unit,
    onLater: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val language = sharedPreferences.getString("language", "en") ?: "en"

    val promptText = if (language == "fa")
        "⭐ اگر از برنامه راضی بودید، لطفاً به ما امتیاز بدهید!"
    else
        "⭐ If you’ve liked the app so far, would you mind rating it?"

    val rateNowText = if (language == "fa") "حتماً!" else "Sure!"
    val laterText = if (language == "fa") "بعداً" else "Maybe later"

    Dialog(onDismissRequest = onLater) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = promptText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = onRateNow) {
                        Text(rateNowText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onLater) {
                        Text(laterText)
                    }
                }
            }
        }
    }
}


fun generateQRCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        Log.e("QRCode", "Error generating QR code", e)
        null
    }
}
