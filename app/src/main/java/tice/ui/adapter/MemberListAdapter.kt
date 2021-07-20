package tice.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ticeapp.TICE.R
import tice.ui.models.MemberData

class MemberListAdapter constructor(private var member: List<MemberData>) : RecyclerView.Adapter<MemberListAdapter.ViewHolder>() {

    inner class ViewHolder(val containerView: View) : RecyclerView.ViewHolder(containerView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.group_member_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = member[position]

        val memberStatus = if (item.isAdmin) {
            holder.itemView.context.getString(R.string.groupInfo_admin)
        } else {
            holder.itemView.context.getString(R.string.groupInfo_member)
        }

        holder.containerView.findViewById<TextView>(R.id.groupMemberListItem_Title_TextView).text = item.userName
        holder.containerView.findViewById<TextView>(R.id.groupMemberListItem_Info_TextView).text = memberStatus
    }

    override fun getItemCount(): Int {
        return member.size
    }

    fun addNewItems(newMembers: List<MemberData>) {
        member = newMembers
        notifyDataSetChanged()
    }
}
