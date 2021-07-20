package tice.managers.storageManagers

import tice.dagger.scopes.AppScope
import tice.models.DeviceId
import javax.inject.Inject

@AppScope
class DeviceIdStorageManager @Inject constructor(
    private val storageLocker: StorageLockerType
) : DeviceIdStorageManagerType {

    override fun storeDeviceId(deviceId: DeviceId) {
        storageLocker.store(StorageLockerType.StorageKey.DEVICE_ID, deviceId)
    }

    override fun loadDeviceId(): DeviceId? = storageLocker.load(StorageLockerType.StorageKey.DEVICE_ID)

    override fun deleteDeviceId() = storageLocker.remove(StorageLockerType.StorageKey.DEVICE_ID)
}
