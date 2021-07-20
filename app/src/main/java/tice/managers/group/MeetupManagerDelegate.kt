package tice.managers.group

import tice.models.Team

interface MeetupManagerDelegate {
    suspend fun reload(team: Team): Team
}
