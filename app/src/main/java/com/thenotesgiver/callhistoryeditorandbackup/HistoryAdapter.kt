package com.thenotesgiver.callhistoryeditorandbackup

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thenotesgiver.callhistoryeditorandbackup.databinding.CallItemBinding

class HistoryAdapter(val context: Context
): ListAdapter<HistoryModel, HistoryAdapter.CallLogHolder>(ItemDiffCallback()) {

    private var onItemClickListener: ((HistoryModel) -> Unit)? = null
    class CallLogHolder(item: CallItemBinding): RecyclerView.ViewHolder(item.root) {
        val name: TextView = item.arrName
        val number: TextView = item.arrNumber
        val dateTime: TextView = item.arrTime
        val duration: TextView = item.arrDuration




    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogHolder {
        val root  = CallItemBinding.inflate(LayoutInflater.from(context),parent,false)
        return CallLogHolder(root)
    }



    override fun onBindViewHolder(holder: CallLogHolder, position: Int) {
        val log =getItem(position)
        if (log.name == "" || log.name == null){
            holder.name.text = "Unknown"
        }
        else{
            holder.name.text = log.name
        }

        holder.number.text = log.number
        holder.dateTime.text = log.dateTime
        holder.duration.text = log.duration



        val type = getItem(position).type
        when (type) {
            "1" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(context.resources.getDrawable(R.drawable.round_call_received_24),null,null,null)
            "2" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(context.resources.getDrawable(R.drawable.round_call_made_24),null,null,null)
            "3" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(context.resources.getDrawable(R.drawable.missed),null,null,null)
            "4" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(context.resources.getDrawable(R.drawable.baseline_record_voice_over_24),null,null,null)
            "5" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(context.resources.getDrawable(R.drawable.baseline_call_end_24),null,null,null)
            "6" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(context.resources.getDrawable(R.drawable.round_block_24),null,null,null)

            "7" -> "EXT"
            else -> "UNKNOWN"
        }
       holder.itemView.setOnClickListener {
           onItemClickListener?.invoke(log)
       }


    }
    fun setOnItemClickListener(listener: (HistoryModel) -> Unit) {
        onItemClickListener = listener
    }
    private class ItemDiffCallback : DiffUtil.ItemCallback<HistoryModel>() {
        override fun areItemsTheSame(oldItem: HistoryModel, newItem: HistoryModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HistoryModel, newItem: HistoryModel): Boolean {
            return oldItem == newItem
        }
    }
}