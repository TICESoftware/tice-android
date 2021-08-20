package tice.dagger.provides

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.ticeapp.TICE.BuildConfig
import com.ticeapp.TICE.R
import com.ticeapp.androiddoubleratchet.Base64Coder
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.tls.HandshakeCertificates
import tice.AppFlow
import tice.TICEApplication
import tice.dagger.scopes.AppScope
import tice.models.ConversationId
import tice.models.MembershipRenewalConfig
import tice.ui.delegates.AppStatusProvider
import tice.utility.getLogger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Named

@Module
class ConfigModule {
    companion object {
        const val VERSION = "VERSION"
        const val VERSION_CODE = "VERSION_CODE"
        const val PLATFORM = "PLATFORM"

        const val BASE_URL = "BASE_URL"
        const val WEB_SOCKET_URL = "WEB_SOCKET_URL"
        const val POST_OFFICE_TIMEOUT = "POST_OFFICE_TIMEOUT"
        const val REQUEST_DEVICE_ID_TIMEOUT = "REQUEST_DEVICE_ID_TIMEOUT"
        const val WEB_SOCKET_RETRY_DELAY = "WEB_SOCKET_RETRY_DELAY"
        const val USER_CACHE_TIMEOUT = "USER_CACHE_TIMEOUT"
        const val MAILBOX_DEFAULT_TIMEOUT = "MAILBOX_DEFAULT_TIMEOUT"
        const val SETTINGS_PREFS = "SETTINGS_PREFS"
        const val PUBLIC_SERVER_KEY = "PUBLIC_SERVER_KEY"
        const val CRYPTO_PARAMS = "CRYPTO_PARAMS"

        const val HCAPTCHA_SITE_KEY = "HCAPTCHA_SITE_KEY"

        const val MAPBOX_SECRET_TOKEN = "MAPBOX_SECRET_TOKEN"

        const val PLACES_SEARCH_REQUEST_CODE = "PLACES_SEARCH_REQUEST_CODE"

        const val BEEKEEPER_PRODUCT = "BEEKEEPER_PRODUCT"
        const val BEEKEEPER_BASE_URL = "BEEKEEPER_BASE_URL"
        const val BEEKEEPER_DISPATCH_INTERVAL = "BEEKEEPER_DISPATCH_INTERVAL"
        const val BEEKEEPER_MAX_BATCH_SIZE = "BEEKEEPER_MAX_BATCH_SIZE"
        const val BEEKEEPER_SECRET = "BEEKEEPER_SECRET"
        const val CERTIFICATE_VALIDITY_TIME_RENEWAL_THRESHOLD = "CERTIFICATE_VALIDITY_TIME_RENEWAL_THRESHOLD"

        const val LOCATION_SHARING_STATE_TIMER_INTERVAL = "LOCATION_SHARING_TIMER_INTERVAL"
        const val LOCATION_SHARING_STATE_MAX_AGE = "LOCATION_SHARING_STATE_MAX_AGE"

        const val DEVELOPMENT_VERIFICATION_CODE = "DEVELOPMENT_VERIFICATION_CODE"
    }

    data class CryptoParams(
        val maxSkip: Int,
        val maxCache: Int,
        val info: String,
        val oneTimePrekeyCount: Int,
        val signingAlgorithm: String,
        val certificateValidityPeriod: Int,
        val certificationValidationLeeway: Int
    )

    private val logger by getLogger()

    @Provides
    fun provideAppForegroundStatus(appFlow: AppFlow): AppStatusProvider {
        return appFlow
    }

    @Provides
    fun provideAppContext(application: TICEApplication): Context {
        return application.applicationContext
    }

    @Provides
    fun provideSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("tice", Context.MODE_PRIVATE)
    }

    @Provides
    @Named(VERSION)
    fun provideVersion(): String = BuildConfig.VERSION_NAME

    @Provides
    @Named(VERSION_CODE)
    fun provideVersionCode(): String = BuildConfig.VERSION_CODE.toString()

    @Provides
    @Named(PLATFORM)
    fun providePlatform(): String = "android"

    @Provides
    @Named(SETTINGS_PREFS)
    fun provideSettingsPrefs(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @AppScope
    @Provides
    fun provideOkHttpClient(context: Context): OkHttpClient {
        val logInterceptor = HttpLoggingInterceptor()
        logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logInterceptor)
            .pingInterval(15L, TimeUnit.SECONDS)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            logger.info("Add ISRG Root X1 Certificate because it's not bundled in this deprecated Android version.")

            val certInput = context.resources.openRawResource(R.raw.isrg_root_x1)
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(certInput) as X509Certificate

            val certificates = HandshakeCertificates.Builder()
                .addPlatformTrustedCertificates()
                .addTrustedCertificate(cert)
                .build()

            clientBuilder.sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
        }

        return clientBuilder.build()
    }

    @Provides
    @Named(LOCATION_SHARING_STATE_TIMER_INTERVAL)
    fun provideLocationSharingTimerInterval(): Long = 60_000

    @Provides
    @Named(LOCATION_SHARING_STATE_MAX_AGE)
    fun provideLocationSharingStateMaxAge(): Long = 60_000

    @Provides
    @Named(REQUEST_DEVICE_ID_TIMEOUT)
    fun provideDeviceIdTimeout(): Long = 25_000

    @Provides
    @Named(POST_OFFICE_TIMEOUT)
    fun provideTimeOutConst(): Long = 5000

    @Provides
    @Named(BASE_URL)
    fun provideBaseUrl(context: Context): String =
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData.getString("base_url")!!

    @Provides
    @Named(PUBLIC_SERVER_KEY)
    fun providePublicServerKey(context: Context): String = context.resources.getString(R.string.publicServerKey)

    @Provides
    fun provideCryptoParams(): CryptoParams =
        CryptoParams(
            2000,
            5010,
            "TICE",
            100,
            "SHA512withECDSA",
            60 * 60 * 24 * 30 * 6,
            3
        )

    @Provides
    @Named("DATABASE_KEY_LENGTH")
    fun provideDatabaseKeyLength(): Int = 48

    @Provides
    @Named(WEB_SOCKET_URL)
    fun provideWebSocketUrl(context: Context): String =
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData.getString("websocket_url")!!

    @Provides
    @Named(WEB_SOCKET_RETRY_DELAY)
    fun provideWebSocketRetryDelay(): Long = 60_000

    @Provides
    @Named(USER_CACHE_TIMEOUT)
    fun provideCacheTimeOut(): Long = 36_000

    @Provides
    @Named(MAILBOX_DEFAULT_TIMEOUT)
    fun provideMailboxTimeOut(): Long = 5_000

    @Provides
    @Named("COLLAPSING_CONVERSATION_ID")
    fun provideCollapsingConversationId(): ConversationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    @Provides
    @Named("NONCOLLAPSING_CONVERSATION_ID")
    fun provideNonCollapsingConversationId(): ConversationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")

    @Provides
    @AppScope
    fun provideNotificationManager(context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    @Named(BEEKEEPER_PRODUCT)
    fun provideBeekeeperProduct(context: Context): String =
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        ).metaData.getString("beekeeper_product")!!

    @Provides
    @Named(BEEKEEPER_BASE_URL)
    fun provideBeekeeperBaseURL(): String = "https://beekeeper.tice.app"

    @Provides
    @Named(BEEKEEPER_DISPATCH_INTERVAL)
    fun provideBeekeeperDispatchInterval(): Long = 30_000

    @Provides
    @Named(BEEKEEPER_MAX_BATCH_SIZE)
    fun provideBeekeeperMaxBatchSize(): Int = 100

    @Provides
    fun provideCertificateValidityTimeRenewalThreshold() = MembershipRenewalConfig(60 * 60 * 24 * 30 * 6)

    @Provides
    @Named(BEEKEEPER_SECRET)
    fun provideBeekeeperSecret(context: Context): String =
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        ).metaData.getString("beekeeper_secret")!!

    @Provides
    @Named(HCAPTCHA_SITE_KEY)
    fun provideHCaptchaSiteKey(context: Context): String =
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        ).metaData.getString("hcaptcha_site_key")!!

    @Provides
    @Named(MAPBOX_SECRET_TOKEN)
    fun provideMapboxSecretToken(context: Context): String =
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        ).metaData.getString("mapbox_secret_token")!!

    @Provides
    @Named(PLACES_SEARCH_REQUEST_CODE)
    fun providePlacesSearchRequestCode(): String = "1"

    @Provides
    @Named(DEVELOPMENT_VERIFICATION_CODE)
    fun provideDevelopmentVerificationCode(context: Context): String =
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        ).metaData.getString("development_verification_code")!!

    @Provides
    fun bindSodium(): LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid(), Base64Coder)
}
