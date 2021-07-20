package tice.managers.storageManagers

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class DeviceIdStorageManagerTest {

    private lateinit var deviceIdStorageManager: DeviceIdStorageManager

    private val mockStorageLocker: StorageLocker = mockk(relaxUnitFun = true)

    val TEST_DEVICE_ID = "deviceid"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        deviceIdStorageManager = DeviceIdStorageManager(mockStorageLocker)
    }

    @Test
    fun storeDeviceId() {

        deviceIdStorageManager.storeDeviceId(TEST_DEVICE_ID)

        verify(exactly = 1) { mockStorageLocker.store(StorageLockerType.StorageKey.DEVICE_ID, TEST_DEVICE_ID) }
    }

    @Test
    fun loadDeviceId() {
        every { mockStorageLocker.load(StorageLockerType.StorageKey.DEVICE_ID) } returns TEST_DEVICE_ID

        val result = deviceIdStorageManager.loadDeviceId()

        assertEquals(TEST_DEVICE_ID, result)
    }

    @Test
    fun deleteDeviceId() {
        deviceIdStorageManager.deleteDeviceId()

        verify(exactly = 1) { mockStorageLocker.remove(StorageLockerType.StorageKey.DEVICE_ID) }
    }
}