package tice.managers

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.maps.model.LatLng
import com.ticeapp.TICE.R.string
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.models.CameraSettings

internal class SettingsManagerTest {

    private lateinit var settingsManager: SettingsManager

    private val mockSharedPreferences: SharedPreferences = mockk(relaxUnitFun = true)
    private val mockContext: Context = mockk(relaxUnitFun = true)

    private val TEST_LAT = 1f
    private val TEST_LNG = 2f
    private val TEST_ZOOM = 3f
    private val TEST_TILT = 4f
    private val TEST_BEARING = 5f

    private val LAST_CAMERA_POSITION_KEY_LAT = "LAST_CAMERA_POSITION_KEY_LAT"
    private val LAST_CAMERA_POSITION_KEY_LNG = "LAST_CAMERA_POSITION_KEY_LNG"
    private val LAST_CAMERA_POSITION_KEY_ZOOM = "LAST_CAMERA_POSITION_KEY_ZOOM"
    private val LAST_CAMERA_POSITION_KEY_TILT = "LAST_CAMERA_POSITION_KEY_TILT"
    private val LAST_CAMERA_POSITION_KEY_BEARING = "LAST_CAMERA_POSITION_KEY_BEARING"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockContext.getString(string.lastCameraPositionKeyLat) } returns LAST_CAMERA_POSITION_KEY_LAT
        every { mockContext.getString(string.lastCameraPositionKeyLng) } returns LAST_CAMERA_POSITION_KEY_LNG
        every { mockContext.getString(string.lastCameraPositionKeyZoom) } returns LAST_CAMERA_POSITION_KEY_ZOOM
        every { mockContext.getString(string.lastCameraPositionKeyTilt) } returns LAST_CAMERA_POSITION_KEY_TILT
        every { mockContext.getString(string.lastCameraPositionKeyBearing) } returns LAST_CAMERA_POSITION_KEY_BEARING

        settingsManager = SettingsManager(mockSharedPreferences, mockContext)
    }

    @Test
    fun `Get cameraLocation value`() = runBlockingTest {
        every { mockSharedPreferences.getFloat(LAST_CAMERA_POSITION_KEY_LAT, 0f) } returns TEST_LAT
        every { mockSharedPreferences.getFloat(LAST_CAMERA_POSITION_KEY_LNG, 0f) } returns TEST_LNG
        every { mockSharedPreferences.getFloat(LAST_CAMERA_POSITION_KEY_ZOOM, 0f) } returns TEST_ZOOM
        every { mockSharedPreferences.getFloat(LAST_CAMERA_POSITION_KEY_TILT, 0f) } returns TEST_TILT
        every { mockSharedPreferences.getFloat(LAST_CAMERA_POSITION_KEY_BEARING, 0f) } returns TEST_BEARING

        val result = settingsManager.cameraSettings

        assertEquals(TEST_LAT, result.latLng.latitude.toFloat())
        assertEquals(TEST_LNG, result.latLng.longitude.toFloat())
        assertEquals(TEST_ZOOM, result.zoom)
        assertEquals(TEST_TILT, result.tilt)
        assertEquals(TEST_BEARING, result.bearing)
    }


    @Test
    fun `Set locationSharingEnabled with true and false value`() = runBlockingTest {
        val TEST_SETTINGS = CameraSettings(LatLng(TEST_LAT.toDouble(), TEST_LNG.toDouble()), TEST_ZOOM, TEST_TILT, TEST_BEARING)

        val mockSharedPreferencesEditor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)

        every { mockSharedPreferences.edit() } returns mockSharedPreferencesEditor
        every { mockSharedPreferencesEditor.putFloat(LAST_CAMERA_POSITION_KEY_LAT, TEST_LAT) } returns mockSharedPreferencesEditor
        every { mockSharedPreferencesEditor.putFloat(LAST_CAMERA_POSITION_KEY_LNG, TEST_LNG) } returns mockSharedPreferencesEditor
        every { mockSharedPreferencesEditor.putFloat(LAST_CAMERA_POSITION_KEY_ZOOM, TEST_ZOOM) } returns mockSharedPreferencesEditor
        every { mockSharedPreferencesEditor.putFloat(LAST_CAMERA_POSITION_KEY_TILT, TEST_TILT) } returns mockSharedPreferencesEditor
        every { mockSharedPreferencesEditor.putFloat(LAST_CAMERA_POSITION_KEY_BEARING, TEST_BEARING) } returns mockSharedPreferencesEditor

        settingsManager.cameraSettings = TEST_SETTINGS

        verify(exactly = 1) { mockSharedPreferencesEditor.apply() }
    }
}
