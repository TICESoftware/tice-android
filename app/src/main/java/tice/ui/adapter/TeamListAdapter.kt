package tice.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.TeamListItemLayoutBinding
import tice.ui.models.TeamData
import tice.ui.models.TeamLocationSharingState.*
import java.text.DateFormat
import java.util.*

class TeamListAdapter : RecyclerView.Adapter<TeamListAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: TeamListItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(teamItem: TeamData) {
            binding.teamName.text = teamItem.teamName.name
            binding.teamInfo.text =
                teamItem.memberNames
                    .sorted()
                    .toMutableList()
                    .plus(itemView.resources.getString(R.string.teams_memberNames_you))
                    .joinToString()
        }

        var meetUpIndicator = binding.meetupImage
        var chatIndicator = binding.chatImage
        var dateText = binding.date
    }

    private var teams = listOf<TeamData>()

    private var viewHolderOnClickListener: View.(teamItem: TeamData, position: Int) -> Unit = { _, _ -> }
    private var viewHolderOnLongClickListener: View.(teamItem: TeamData, position: Int, itemView: View) -> Unit = { _, _, _ -> }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(TeamListItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val teamItem = teams[position]
        holder.bind(teamItem)

        when (teamItem.locationSharingState) {
            TheyShareLocation -> {
                holder.meetUpIndicator.visibility = View.VISIBLE
                holder.meetUpIndicator.setImageResource(R.drawable.ic_invited)
            }
            WeShareLocation -> {
                holder.meetUpIndicator.visibility = View.VISIBLE
                holder.meetUpIndicator.setImageResource(R.drawable.ic_location)
            }
            None -> {
                holder.meetUpIndicator.visibility = View.GONE
            }
        }

        teamItem.unreadMessages?.let {
            if (it.unread) {
                holder.chatIndicator.visibility = View.VISIBLE
                holder.dateText.text = it.date
            } else {
                holder.chatIndicator.visibility = View.INVISIBLE
                holder.dateText.text = it.date
            }
        } ?: setDate(holder)

        holder.itemView.setOnClickListener { it.viewHolderOnClickListener(teamItem, position) }
        holder.itemView.setOnLongClickListener { it.viewHolderOnLongClickListener(teamItem, position, holder.chatIndicator); true }
    }

    private fun setDate(holder: ViewHolder) {
        holder.dateText.text = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date())
    }

    override fun getItemCount(): Int {
        return teams.size
    }

    fun setTeamData(newTeams: List<TeamData>) {
        teams = newTeams
        notifyDataSetChanged()
    }

    fun setViewHolderOnClickListener(block: View.(item: TeamData, position: Int) -> Unit) {
        viewHolderOnClickListener = block
    }

    fun setViewHolderOnLongClickListener(block: View.(item: TeamData, position: Int, itemView: View) -> Unit) {
        viewHolderOnLongClickListener = block
    }
}
