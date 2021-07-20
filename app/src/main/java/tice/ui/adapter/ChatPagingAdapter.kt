package tice.ui.adapter

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.ticeapp.TICE.R
import tice.ui.models.ChatMessagesRepresentation
import java.text.DateFormat
import java.util.*

class ChatPagingAdapter : PagingDataAdapter<ChatMessagesRepresentation, ChatPagingAdapter.OrderHolder>(CustomDiffUtil) {

    class OrderHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView)

    private var viewHolderOnclickListener: View.(messagesRepresentation: ChatMessagesRepresentation, position: Int) -> Unit =
        { _, _ -> }

    init {
        hasStableIds()
    }

    companion object {
        object CustomDiffUtil : DiffUtil.ItemCallback<ChatMessagesRepresentation>() {
            override fun areItemsTheSame(
                oldItem: ChatMessagesRepresentation,
                newItem: ChatMessagesRepresentation
            ): Boolean {
                return if (oldItem is ChatMessagesRepresentation.MessageItem && newItem is ChatMessagesRepresentation.MessageItem) {
                    oldItem.messageId == newItem.messageId
                } else oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: ChatMessagesRepresentation,
                newItem: ChatMessagesRepresentation
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val message = getItem(position)) {
            is ChatMessagesRepresentation.MessageItem.TextMessage -> if (message.direction == ChatMessagesRepresentation.Direction.Incoming) 100 else 200
            is ChatMessagesRepresentation.MessageItem.ImageMessage -> if (message.direction == ChatMessagesRepresentation.Direction.Incoming) 101 else 201
            is ChatMessagesRepresentation.MetaInfo -> 102
            is ChatMessagesRepresentation.DateSeparator -> 103

            null -> -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderHolder {
        val view = when (viewType) {
            -1 ->
                LayoutInflater.from(parent.context).inflate(R.layout.chat_incoming_text_layout, parent, false)
            100 ->
                LayoutInflater.from(parent.context).inflate(R.layout.chat_incoming_text_layout, parent, false)
            200 ->
                LayoutInflater.from(parent.context).inflate(R.layout.chat_outgoing_text_layout, parent, false)
            101 ->
                TODO("implement incomming image message view")
            201 ->
                TODO("implement outgoing image message view")
            102 ->
                LayoutInflater.from(parent.context).inflate(R.layout.chat_event_layout, parent, false)
            103 ->
                LayoutInflater.from(parent.context).inflate(R.layout.chat_event_layout, parent, false)
            else ->
                throw Exception("Not supported viewtype")
        }
        return OrderHolder(view)
    }

    fun setViewHolderOnClickListener(block: View.(messagesRepresentation: ChatMessagesRepresentation, position: Int) -> Unit) {
        viewHolderOnclickListener = block
    }

    override fun onBindViewHolder(holder: OrderHolder, position: Int) {
        val item = getItem(position)

        when (item) {
            is ChatMessagesRepresentation.MessageItem.TextMessage -> bindMessageView(holder, item)
            is ChatMessagesRepresentation.MessageItem.ImageMessage -> throw Exception("Not implemented")
            is ChatMessagesRepresentation.MetaInfo -> bindMessageView(holder, item)
            is ChatMessagesRepresentation.DateSeparator -> bindMessageView(holder, item)

            null -> return
        }

        holder.itemView.setOnClickListener { it.viewHolderOnclickListener(item, position) }
    }

    private fun bindMessageView(
        holder: OrderHolder,
        item: ChatMessagesRepresentation.MessageItem.TextMessage
    ) {
        holder.itemView.findViewById<TextView>(R.id.chat_bubble_text).text = item.text

        if (item.isLastOfUser) {
            holder.itemView.findViewById<ConstraintLayout>(R.id.layout)?.let {
                it.visibility = View.VISIBLE
                ((it.background as LayerDrawable).getDrawable(0) as GradientDrawable).setColor(item.senderColor)
            }
        } else {
            holder.itemView.findViewById<ConstraintLayout>(R.id.layout).visibility =
                View.INVISIBLE
        }

        holder.itemView.findViewById<TextView>(R.id.name_short).text = item.senderName
    }

    private fun bindMessageView(holder: OrderHolder, item: ChatMessagesRepresentation.MetaInfo) {
        holder.itemView.findViewById<TextView>(R.id.chat_bubble_text).text = String.format(
            holder.itemView.context.getString(R.string.chat_metaInfo_other_meetup_info_text),
            item.text,
            formatTime(item.date)
        )
    }

    private fun bindMessageView(
        holder: OrderHolder,
        item: ChatMessagesRepresentation.DateSeparator
    ) {
        holder.itemView.findViewById<TextView>(R.id.chat_bubble_text).text = formatDate(item.date)
    }

    private fun formatDate(date: Date): String {
        return DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(date)
    }

    private fun formatTime(date: Date): String {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    }
}
