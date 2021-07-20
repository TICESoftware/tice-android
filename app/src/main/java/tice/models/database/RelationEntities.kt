package tice.models.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Relation
import tice.models.Meetup
import tice.models.Team
import tice.models.User

@Entity
data class TeamAndMeetup(
    @Embedded val team: Team,
    @Relation(
        parentColumn = "groupId",
        entityColumn = "teamId"
    )
    val meetup: Meetup?
)

@Entity
data class UserAndMembership(
    @Embedded val user: User,
    @Relation(
        parentColumn = "userId",
        entityColumn = "userId"
    )
    val membership: MembershipEntity
)
