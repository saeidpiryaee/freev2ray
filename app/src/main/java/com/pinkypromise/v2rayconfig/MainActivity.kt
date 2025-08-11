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

import androidx.annotation.RequiresApi
import kotlinx.coroutines.isActive


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
    private var rewardPollJob: kotlinx.coroutines.Job? = null

    private var inviteRewardCountState: MutableState<Int>? = null
    private var showInviteRewardDialogState: MutableState<Boolean>? = null








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
                    val raw = String(decodedBytes).trim()
                    // IMPORTANT: return the value (you weren’t returning anything before)
                    raw.removeSuffix("/")
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to fetch or decode server URL", e)
                null
            }
        }
    }


    private suspend fun registerInviterOwner() {
        withContext(Dispatchers.IO) {
            try {
                val payload = """{"code":"${inviterCode()}","device":"${deviceId()}"}"""
                val url = URL("$serverUrl/register_inviter")
                val c = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                Log.d("InviteRegister", "owner bind: HTTP ${c.responseCode} body: $body")
            } catch (e: Exception) {
                Log.e("InviteRegister", "owner bind failed", e)
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        // Read language early for multilingual toasts
        val bootLanguage = sharedPreferences.getString("language", "en") ?: "en"

// Handle deep links (vless:// or ss://)
        intent?.data?.let { uri ->
            val link = uri.toString()
            if (link.startsWith("vless://") || link.startsWith("ss://")) {
                sharedPreferences.edit().putString("hiddenConfig", link).apply()
                val msg = if (bootLanguage == "fa") "کانفیگ از لینک وارد شد" else "Config imported from link"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        val launchCount = sharedPreferences.getInt("launchCount", 0) + 1
        sharedPreferences.edit().putInt("launchCount", launchCount).apply()

        val langFirst = sharedPreferences.getString("language", "en") ?: "en"
        val firstRun = sharedPreferences.getBoolean("firstRun", true)
        if (firstRun) {
            userScore += 1
            saveUserScore(userScore)
            sharedPreferences.edit().putBoolean("firstRun", false).apply()
            val msg = if (langFirst == "fa") "هدیه خوش‌آمدگویی: +۱ امتیاز" else "Welcome bonus: +1 point"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }



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
                // 1) bind inviter ownership immediately
                registerInviterOwner()
                // 2) preload message
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


            val showInviteDialog = remember { mutableStateOf(false) }


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
            val inviteRewardCount = remember {
                mutableStateOf(sharedPreferences.getInt("pendingInviteRewardCount", 0))
            }
            val showInviteRewardDialog = remember { mutableStateOf(inviteRewardCount.value > 0) }

// Bridge these to MainActivity so non-Compose code can trigger UI
            this@MainActivity.inviteRewardCountState = inviteRewardCount
            this@MainActivity.showInviteRewardDialogState = showInviteRewardDialog


            LaunchedEffect(Unit) {
                val pts = pollInviterRewards() ?: 0
                if (pts > 0) applyPointsDelta(pts)
            }

            var currentScore by scoreState

            // If any points arrived before UI was ready, deliver them now and clear the buffer
            LaunchedEffect(Unit) {
                val buffered = sharedPreferences.getInt("pendingInvitePts", 0)
                if (buffered > 0) {
                    sharedPreferences.edit().putInt("pendingInvitePts", 0).apply()
                    applyPointsDelta(buffered)
                }
            }


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

            if (showInviteRewardDialog.value && inviteRewardCount.value > 0) {
                InviteRewardDialog(
                    language = language,
                    points = inviteRewardCount.value,
                    onDismiss = {
                        // Show only once: clear persisted counter and close dialog
                        sharedPreferences.edit().putInt("pendingInviteRewardCount", 0).apply()
                        inviteRewardCount.value = 0
                        showInviteRewardDialog.value = false
                    }
                )
            }





            AppContent(
                currentScore = currentScore,
                config = config,
                onReceiveConfigAndTellMeIfGotIt = {
                    var success = false
                    try {
                        serverThreshold = fetchServerThreshold() ?: 0
                        if (currentScore >= serverThreshold) {
                            val fetched = fetchConfig()
                            if (fetched != null) {
                                config = fetched
                                currentScore -= serverThreshold
                                userScore = currentScore
                                saveUserScore(currentScore)
                                currentScoreState?.value = currentScore
                                success = true
                            } else {
                                val m = if (language == "fa") "خطا در دریافت کانفیگ" else "Failed to fetch config"
                                Toast.makeText(this@MainActivity, m, Toast.LENGTH_SHORT).show()
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
                    } catch (e: Exception) {
                        val m = if (language == "fa") "مشکل ارتباط با سرور" else "Server connection problem"
                        Toast.makeText(this@MainActivity, m, Toast.LENGTH_SHORT).show()
                    }
                    success
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
                bannerHeight = null,



                onShareApp = {
                    val shareText = if (language == "fa")
                        "با این اپ سریع کانفیگ بگیر و وارد کن (QR و لینک). دانلود از گوگل‌پلی:\nhttps://play.google.com/store/apps/details?id=$packageName"
                    else
                        "Get and import configs fast (QR & links) with this app. Play Store:\nhttps://play.google.com/store/apps/details?id=$packageName"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    startActivity(Intent.createChooser(intent, if (language == "fa") "اشتراک‌گذاری" else "Share"))
                },
                onOpenInviteDialog = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val payload = """{"code":"${inviterCode()}","device":"${deviceId()}"}"""
                            val url = URL("$serverUrl/register_inviter")
                            val c = (url.openConnection() as HttpURLConnection).apply {
                                connectTimeout = 8000
                                readTimeout = 8000
                                requestMethod = "POST"
                                doOutput = true
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            }
                            c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                            val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
                                ?.bufferedReader()?.readText() ?: ""
                            Log.d("InviteRegister", "HTTP ${c.responseCode} body: $body")

                            // ignore body, open dialog anyway
                        } catch (_: Exception) { /* ignore */ }
                        withContext(Dispatchers.Main) { showInviteDialog.value = true }

                        // After opening the dialog, try to pull any pending inviter rewards too
                        lifecycleScope.launch {
                            val pts = pollInviterRewards() ?: 0
                            if (pts > 0) applyPointsDelta(pts)
                        }


                    }
                },



                dailyAvailable = isDailyBonusAvailable(),
                onClaimDaily = {
                    if (isDailyBonusAvailable()) {
                        userScore += 1
                        saveUserScore(userScore)
                        currentScoreState?.value = userScore
                        markDailyBonusClaimed()
                        val msg = if (language == "fa") "جایزه روزانه +۱ اضافه شد" else "Daily bonus +1 added"
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = if (language == "fa") "امروز قبلاً دریافت شده" else "Already claimed today"
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }





            )


            // Invite dialog
            if (showInviteDialog.value) {
                InviteDialog(
                    language = language,
                    yourCode = inviterCode(),
                    onDismiss = { showInviteDialog.value = false },
                    onRedeem = { enteredCode ->
                        val code = enteredCode.trim().uppercase()
                        if (code.length != 8 || !code.all { it in "0123456789ABCDEF" }) {
                            val m = if (language == "fa")
                                "کد باید ۸ کاراکتر هگزادسیمال باشد (0-9, A-F)"
                            else
                                "Code must be 8 hex characters (0-9, A-F)"
                            Toast.makeText(this@MainActivity, m, Toast.LENGTH_LONG).show()
                            return@InviteDialog   // ⬅️ stay inside the dialog’s onRedeem lambda
                        }
                        if (code.isEmpty()) {
                            val msg = if (language == "fa") "کد دعوت را وارد کنید" else "Enter an invite code"
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                            return@InviteDialog
                        }
                        // Network call
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val url = URL("$serverUrl/redeem_invite?inviter=${java.net.URLEncoder.encode(code, "UTF-8")}&device=${java.net.URLEncoder.encode(deviceId(), "UTF-8")}")
                                Log.d("InviteRedeem", "GET $url")   // ✅ see the final URL in Logcat

                                val c = (url.openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 8000
                                    readTimeout = 8000
                                    requestMethod = "GET"
                                }
                                c.connect()
                                val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                                val body = stream?.bufferedReader()?.readText() ?: ""


                                // 🔍 Debug log — see exactly what the server sent back
                                Log.d("InviteRedeem", "HTTP ${c.responseCode} body: $body")

// 🛠 Parse JSON safely (you already imported org.json.JSONObject at top)
                                val json = try { JSONObject(body) } catch (_: Exception) { null }
                                val ok = json?.optBoolean("ok") ?: false
                                val inviteePoints = json?.optInt("invitee_points", 0) ?: 0
                                val reason = json?.optString("reason", "") ?: ""

// Switch to main thread for UI updates
                                withContext(Dispatchers.Main) {
                                    if (ok && inviteePoints > 0) {
                                        userScore += inviteePoints
                                        saveUserScore(userScore)
                                        currentScoreState?.value = userScore
                                        val msg = if (language == "fa")
                                            "کد دعوت پذیرفته شد: +$inviteePoints"
                                        else
                                            "Invite code accepted: +$inviteePoints"
                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                        showInviteDialog.value = false
                                    } else {
                                        val friendly = when (reason) {
                                            "self_redeem_forbidden" ->
                                                if (language == "fa") "نمی‌توانید کد خودتان را وارد کنید" else "You can’t redeem your own code"
                                            "already_redeemed" ->
                                                if (language == "fa") "این دستگاه قبلاً کدی را وارد کرده است" else "This device already redeemed a code"
                                            "invalid_code_format" ->
                                                if (language == "fa") "فرمت کد نامعتبر است (۸ کاراکتر هگز)" else "Invalid code format (8 hex)"
                                            "inviter_daily_limit" ->
                                                if (language == "fa") "امروز ظرفیت دعوت‌کننده پر شده است" else "Inviter’s daily limit reached"
                                            "ip_daily_limit" ->
                                                if (language == "fa") "محدودیت روزانه IP پر شده است" else "IP daily limit reached"
                                            "missing_params", "invalid_params" ->
                                                if (language == "fa") "پارامترهای نامعتبر" else "Invalid parameters"
                                            else ->
                                                if (language == "fa") "کد نامعتبر یا خطای سرور" else "Invalid code or server error"
                                        }
                                        Toast.makeText(this@MainActivity, friendly, Toast.LENGTH_LONG).show()
                                    }
                                }

                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    val msg = if (language == "fa") "اتصال برقرار نشد. بعداً دوباره تلاش کنید." else "Couldn’t connect. Try again later."
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )
            }




        }



        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                showAppOpenAd()

                // one immediate poll
                lifecycleScope.launch {
                    val pts = pollInviterRewards() ?: 0
                    if (pts > 0) {
                        val lang = sharedPreferences.getString("language", "en") ?: "en"
                        applyPointsDelta(pts, lang)
                    }
                }

                // start periodic polling while in foreground
                rewardPollJob?.cancel()
                rewardPollJob = lifecycleScope.launch {
                    while (isActive) {
                        try {
                            val pts = pollInviterRewards() ?: 0
                            if (pts > 0) {
                                val lang = sharedPreferences.getString("language", "en") ?: "en"
                                applyPointsDelta(pts, lang)
                            }
                        } catch (_: Exception) { }
                        delay(30_000) // every 30s
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                rewardPollJob?.cancel()
                rewardPollJob = null
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

    // Apply points to the score and UI if ready; otherwise buffer in SharedPreferences.
    private fun applyPointsDelta(pts: Int, langOverride: String? = null) {
        if (pts <= 0) return
        userScore += pts
        saveUserScore(userScore)

        // 1) Always accumulate a persistent, one-time counter
        val pending = sharedPreferences.getInt("pendingInviteRewardCount", 0) + pts
        sharedPreferences.edit().putInt("pendingInviteRewardCount", pending).apply()

        // 2) Update UI score if screen mounted
        if (currentScoreState != null) {
            currentScoreState?.value = userScore
            val l = langOverride ?: (sharedPreferences.getString("language", "en") ?: "en")
            val msg = if (l == "fa") "امتیاز دعوت‌ها: +$pts" else "Invite rewards: +$pts"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } else {
            // If UI wasn’t ready, we no longer need the old pendingInvitePts buffer
            // (you can keep it if you want, but the new dialog uses pendingInviteRewardCount)
        }

        // 3) If Compose is active, tell it to show the dialog now
        inviteRewardCountState?.let { it.value = pending }
        showInviteRewardDialogState?.let { it.value = true }
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


    private suspend fun pollInviterRewards(): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$serverUrl/poll_rewards?device=${java.net.URLEncoder.encode(deviceId(), "UTF-8")}")
                val c = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                c.connect()
                val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                val body = stream?.bufferedReader()?.readText() ?: ""
                Log.d("InvitePoll", "HTTP ${c.responseCode} body: $body")
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                if (json?.optBoolean("ok") == true) {
                    json.optInt("points", 0)
                } else null
            } catch (e: Exception) {
                Log.e("InvitePoll", "poll failed", e)
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


    // -------- Daily Bonus Helpers --------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun todayStr(): String = try {
        java.time.LocalDate.now().toString()
    } catch (e: Throwable) {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun isDailyBonusAvailable(): Boolean {
        val last = sharedPreferences.getString("lastClaimDay", null)
        return last != todayStr()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun markDailyBonusClaimed() {
        sharedPreferences.edit().putString("lastClaimDay", todayStr()).apply()
    }

    // -------- Invite Helpers --------
    private fun sha256Hex(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    // Stable, URL-safe, reinstall-resistant for same user + signing key
    private fun deviceId(): String {
        val raw = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
        // Salt with packageName so codes can't collide across different apps
        val base = "$raw:${packageName}"
        // 32 hex chars (128-bit) is plenty and URL-safe
        return sha256Hex(base).take(32)
    }

    // Short, human-typeable invite code (8 hex, uppercase)
    private fun inviterCode(): String {
        return deviceId().take(8).uppercase()
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
    onReceiveConfigAndTellMeIfGotIt: suspend () -> Boolean,
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
    bannerHeight: Int?,
    // NEW:

    onShareApp: () -> Unit,
    onOpenInviteDialog: () -> Unit,
    dailyAvailable: Boolean,
    onClaimDaily: () -> Unit
)




{
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


                // --- Daily Bonus Card ---
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = if (language == "fa") "جایزه روزانه 🎁" else "Daily Bonus 🎁",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (language == "fa")
                                "هر روز می‌توانید +۱ امتیاز بگیرید و سریع‌تر کانفیگ دریافت کنید."
                            else
                                "Claim +1 point every day to reach config faster.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onClaimDaily,
                            enabled = dailyAvailable,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dailyAvailable) Color(0xFF6A1B9A) else Color(0xFF757575),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (dailyAvailable)
                                    (if (language == "fa") "گرفتن +۱ امروز" else "Claim today’s +1")
                                else
                                    (if (language == "fa") "امروز گرفته شد" else "Already claimed today")
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))



                Button(
                    onClick = {
                        configLoading.value = true  // start spinner
                        coroutineScope.launch {
                            val gotConfig = onReceiveConfigAndTellMeIfGotIt()
                            configLoading.value = false  // stop spinner when work is done
                            if (gotConfig) {
                                // let UI render the new config, then scroll down to it
                                delay(150)
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    },
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
                        Text(if (language == "fa") "دریافت کانفیگ 📥" else "Receive Config 📥")
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

                // --- Share App Button ---
                Button(
                    onClick = onShareApp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E88E5),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(if (language == "fa") "اشتراک‌گذاری برنامه 🔗" else "Share the App 🔗")
                }
                Text(
                    text = if (language == "fa")
                        "دوستان‌تان را دعوت کنید؛ هرچه کاربران بیشتر، انگیزه ما برای انتشار سریع‌تر کانفیگ‌ها بیشتر!"
                    else
                        "Invite friends—more users means quicker, more frequent config releases!",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                    modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // --- Invite & Earn Button ---
                Button(
                    onClick = onOpenInviteDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF8F00),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(if (language == "fa") "دعوت دوستان و امتیاز 🎁" else "Invite & Earn 🎁")
                }
                Text(
                    text = if (language == "fa")
                        "کد دوست‌تان را وارد کنید تا +۱ امتیاز بگیرید. کد خودتان را هم برای دوستان ارسال کنید!"
                    else
                        "Enter a friend’s code to get +1 point. Share your code so they can earn too!",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                    modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))




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


@Composable
fun InviteRewardDialog(
    language: String,
    points: Int,
    onDismiss: () -> Unit
) {
    val title = if (language == "fa") "تبریک! 🎉" else "Congrats! 🎉"
    val msg = if (language == "fa")
        "به خاطر دعوت دوستان، +$points امتیاز گرفتید."
    else
        "You received +$points points from your invitations."

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(msg, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (language == "fa") "باشه" else "OK")
                }
            }
        }
    }
}



@Composable
fun InviteDialog(
    language: String,
    yourCode: String,
    onDismiss: () -> Unit,
    onRedeem: (String) -> Unit
) {
    var entered by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    fun t(en: String, fa: String) = if (language == "fa") fa else en

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {

                // --- BIG PRIMARY on top: Redeem ---
                OutlinedTextField(
                    value = entered,
                    onValueChange = { entered = it },
                    label = { Text(t("Enter friend’s code", "کد دوست‌تان را وارد کنید")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onRedeem(entered) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(t("Redeem", "دریافت امتیاز"))
                }

                Spacer(Modifier.height(18.dp))

                // --- Your code row with small Copy button ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t("Your code:", "کد شما:"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = yourCode,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("InviteCode", yourCode))
                            Toast.makeText(ctx, t("Copied", "کپی شد"), Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(t("Copy", "کپی"))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t(
                        "Share this code with friends. They enter it to get points.",
                        "این کد را برای دوستان ارسال کنید. آنها با وارد کردن کد امتیاز می‌گیرند."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(12.dp))

                // --- Secondary: Share my code ---
                OutlinedButton(
                    onClick = {
                        val shareText = if (language == "fa")
                            "کد من برای دریافت امتیاز: $yourCode\nدانلود اپ:\nhttps://play.google.com/store/apps/details?id=${ctx.packageName}"
                        else
                            "My invite code: $yourCode\nGet the app:\nhttps://play.google.com/store/apps/details?id=${ctx.packageName}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        ctx.startActivity(Intent.createChooser(intent, t("Share", "اشتراک‌گذاری")))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(t("Share My Code", "اشتراک‌گذاری کد من"))
                }

                Spacer(Modifier.height(12.dp))

                // --- Tertiary: Close ---
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t("Close", "بستن"))
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
