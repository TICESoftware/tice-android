package tice.managers.services
//
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import androidx.test.core.app.ApplicationProvider
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import androidx.test.rule.ServiceTestRule
//import com.google.android.gms.common.api.GoogleApiClient
//import com.google.android.gms.location.FusedLocationProviderClient
//import com.google.android.gms.location.LocationServices
//import io.mockk.*
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.runBlocking
//import org.junit.Rule
//import org.junit.Test
//import org.junit.jupiter.api.Assertions
//import org.junit.runner.RunWith
//import tice.managers.LocationManagerType
//import tice.utility.contextProvider.CoroutineContextProviderType
//
//@RunWith(AndroidJUnit4::class)
//class LocationServiceTest {
//
//    @get:Rule
//    val serviceRule = ServiceTestRule()
//
//    @Test
//    fun onStartCommand() = runBlocking {
//
//        val mockLocationManager = mockk<LocationManagerType>(relaxUnitFun = true)
//        val mockCoroutineContentProvider = mockk<CoroutineContextProviderType>(relaxUnitFun = true)
//
//        val mockFusedLocationProviderClient = mockk<FusedLocationProviderClient>(relaxUnitFun = true)
//        val mockGoogleApiClientBuilder = mockk<GoogleApiClient.Builder>(relaxUnitFun = true)
//        val mockGoogleApiClient = mockk<GoogleApiClient>(relaxUnitFun = true)
//
//        mockkStatic(LocationServices::class)
//        every { LocationServices.getFusedLocationProviderClient(any()) } returns mockFusedLocationProviderClient
//        every {
//            mockFusedLocationProviderClient.requestLocationUpdates(
//                any(),
//                any(),
//                any()
//            )
//        } returns mockk()
//        every { mockCoroutineContentProvider.IO } returns Dispatchers.IO
//
//        val serviceIntent = Intent(
//            ApplicationProvider.getApplicationContext<Context>(),
//            LocationService::class.java
//        )
//
//        val binder = serviceRule.bindService(serviceIntent)
//        val localBinder = binder as LocationService.LocalBinder
//
//        val locationService = localBinder.locationService
//
//        locationService.locationManager = mockLocationManager
//        locationService.coroutineContextProvider = mockCoroutineContentProvider
//
//        mockkStatic(GoogleApiClient::class)
//        mockkStatic(GoogleApiClient.Builder::class)
//
//        every { mockGoogleApiClient.connect() } returns Unit
//        every { GoogleApiClient.Builder(any()) } returns mockGoogleApiClientBuilder
//        every { mockGoogleApiClientBuilder.addApi(LocationServices.API) } returns mockGoogleApiClientBuilder
//        every { mockGoogleApiClientBuilder.addConnectionCallbacks(locationService) } returns mockGoogleApiClientBuilder
//        every { mockGoogleApiClientBuilder.addOnConnectionFailedListener(locationService) } returns mockGoogleApiClientBuilder
//        every { mockGoogleApiClientBuilder.build() } returns mockGoogleApiClient
//
//        val result = locationService.onStartCommand(null, 0, 0)
//
//        Assertions.assertEquals(Service.START_STICKY, result)
//
//        verify(exactly = 1) { LocationServices.getFusedLocationProviderClient(locationService) }
//        verify(exactly = 1) { mockGoogleApiClientBuilder.build() }
//    }
//
//    @Test
//    fun onBind() {
//
//    }
//
//    @Test
//    fun onConnected() {
//
//    }
//
//    @Test
//    fun onCreate() {
//    }
//
//    @Test
//    fun onConnectionSuspended() {
//    }
//
//    @Test
//    fun onConnectionFailed() {
//    }
//
//    @Test
//    fun stopService() {
//    }
//
//    @Test
//    fun onDestroy() {
//    }
//}