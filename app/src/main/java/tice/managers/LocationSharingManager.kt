package tice.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tice.dagger.scopes.AppScope
import tice.exceptions.LocationManagerException
import tice.exceptions.UnexpectedPayloadTypeException
import tice.managers.messaging.PostOfficeType
import tice.managers.messaging.notificationHandler.PayloadReceiver
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.models.*
import tice.models.messaging.LocationUpdateV2
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainerBundle
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import kotlin.concurrent.fixedRateTimer

@AppScope
class LocationSharingManager @Inject constructor(
    private val locationSharingStorageManager: LocationSharingStorageManagerType,
    private val locationManager: LocationManagerType,
    private val postOffice: PostOfficeType,
    private val groupStorageManager: GroupStorageManagerType,
    private val userManager: UserManagerType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val signedInUserManager: SignedInUserManagerType,
    @Named("LOCATION_SHARING_TIMER_INTERVAL") private val checkTime: Long,
    @Named("LOCATION_SHARING_STATE_MAX_AGE") private val locationMaxAge: Long
) : LocationSharingManagerType, PayloadReceiver {
    private val logger by getLogger()

    private var lastLocations: MutableMap<UserGroupIds, Location> = mutableMapOf()

    private val _memberLocationFlow = MutableSharedFlow<UserLocation>()
    override val memberLocationFlow: SharedFlow<UserLocation>
        get() = _memberLocationFlow

    private var outdatedLocationSharingTimer: Timer? = null

    override fun startOutdatedLocationSharingStateCheck() {
        if (outdatedLocationSharingTimer != null) return

        outdatedLocationSharingTimer = fixedRateTimer(
            "LocationSharingTimer",
            false,
            checkTime,
            checkTime
        ) {
            CoroutineScope(coroutineContextProvider.IO).launch {
                checkOutdatedLocationSharingStates()
            }
        }
    }

    override suspend fun checkOutdatedLocationSharingStates() {
        val outdatedStates = locationSharingStorageManager.getAllStatesEnabled(true).filter { state ->
            Date().time - state.lastUpdate.time > locationMaxAge && state.userId != signedInUserManager.signedInUser.userId
        }

        outdatedStates.forEach { state ->
            lastLocation(UserGroupIds(state.userId, state.groupId))?.let { location ->
                if (Date().time - location.timestamp.time > locationMaxAge) {
                    logger.debug("Did not receive a location update from user ${state.userId} since a while while their location state is enabled. Disabling it now.")
                    locationSharingStorageManager.storeLocationSharingState(
                        LocationSharingState(state.userId, state.groupId, false, max(location.timestamp, state.lastUpdate))
                    )
                }
            } ?: run {
                logger.debug("Did not receive a location update from user ${state.userId} at all while their location state is enabled. Disabling it now.")
                locationSharingStorageManager.storeLocationSharingState(
                    LocationSharingState(state.userId, state.groupId, false, state.lastUpdate)
                )
            }
        }
    }

    private fun max(date1: Date, date2: Date): Date {
        return if (date1.after(date2)) date1
        else date2
    }

    override suspend fun getAllLocationSharingStatesOfGroup(groupId: GroupId): List<LocationSharingState> {
        return locationSharingStorageManager.getAllUserStatesOfGroup(groupId).let { list ->
            logger.debug("Fill list of LocationSharingStates with missing user states")

            fillLocationStates(list, groupId)
        }
    }

    override fun getFlowOfAllLocationSharingStatesOfGroup(groupId: GroupId): Flow<List<LocationSharingState>> {
        return locationSharingStorageManager.getStatesFlowOfAllUserInGroup(groupId).map { list ->
            fillLocationStates(list, groupId)
        }
    }

    private suspend fun fillLocationStates(list: List<LocationSharingState>, groupId: GroupId): List<LocationSharingState> {
        val newList = mutableListOf<LocationSharingState>()
        newList.addAll(list)

        val memberships = groupStorageManager.loadMembershipsOfGroup(groupId)
        memberships.forEach { membership ->
            list.find { it.userId == membership.userId } ?: run {
                newList.add(LocationSharingState(membership.userId, membership.groupId, false, Date()))
            }
        }

        return newList
    }

    override fun registerEnvelopeReceiver() {
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.LocationUpdateV2, this)
    }

    override fun lastLocation(userGroupIds: UserGroupIds): Location? = lastLocations[userGroupIds]

    override suspend fun handlePayloadContainerBundle(bundle: PayloadContainerBundle) {
        val payload = (bundle.payload as? LocationUpdateV2) ?: throw UnexpectedPayloadTypeException

        val user = userManager.getUser(bundle.metaInfo.senderId) ?: throw LocationManagerException.UnknownUser

        logger.debug("Received location update from user ${user.publicName ?: user.userId}")

        var location = payload.location
        if (location.timestamp.after(Date())) {
            logger.warn("Location update from ${user.userId} is in the future: ${location.timestamp}. Overwriting date with now.")
            location = location.copy(timestamp = Date())
        }

        val key = UserGroupIds(user.userId, payload.groupId)
        val shouldProcessLocationUpdate = lastLocations[key]?.let { it.timestamp < location.timestamp } ?: true

        if (!shouldProcessLocationUpdate) {
            logger.debug("This location update is older than last location update. Skipping it.")
            return
        }

        lastLocations[key] = location

        val locationSharingState = locationSharingStorageManager.getStateOfUserInGroup(user.userId, payload.groupId) ?: LocationSharingState(user.userId, payload.groupId, false, Date(0))
        if (!locationSharingState.sharingEnabled) {
            logger.debug("Received location update from user ${user.userId} but their location sharing state was disabled at ${locationSharingState.lastUpdate}.")

            val locationUpdateRecentEnough = Date().time - location.timestamp.time <= locationMaxAge

            if (locationSharingState.lastUpdate < location.timestamp && locationUpdateRecentEnough) {
                logger.warn("Enabling location sharing for user ${user.userId} now.")
                val newLocationSharingState = LocationSharingState(user.userId, payload.groupId, true, location.timestamp)
                locationSharingStorageManager.storeLocationSharingState(newLocationSharingState)
            }
        }

        _memberLocationFlow.emit(UserLocation(user.userId, location))
    }
}
