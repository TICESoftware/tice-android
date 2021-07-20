package tice.managers.storageManagers

import tice.models.DeviceId

interface DeviceIdStorageManagerType {
    fun storeDeviceId(deviceId: DeviceId)
    fun loadDeviceId(): DeviceId?
    fun deleteDeviceId()
}
