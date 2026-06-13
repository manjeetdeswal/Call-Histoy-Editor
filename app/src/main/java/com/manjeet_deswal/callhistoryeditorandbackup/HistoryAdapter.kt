package com.manjeet_deswal.callhistoryeditorandbackup

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.manjeet_deswal.callhistoryeditorandbackup.databinding.CallItemBinding

class HistoryAdapter(val context: Context) :
    ListAdapter<HistoryModel, HistoryAdapter.CallLogHolder>(ItemDiffCallback()) {

    private var onItemClickListener: ((HistoryModel) -> Unit)? = null

    // --- NEW: Multi-select tracking ---
    val selectedItemIds = mutableSetOf<String>()
    var onSelectionModeChange: ((isSelectionMode: Boolean, count: Int) -> Unit)? = null

    class CallLogHolder(item: CallItemBinding) : RecyclerView.ViewHolder(item.root) {
        val name: TextView = item.arrName
        val number: TextView = item.arrNumber
        val dateTime: TextView = item.arrTime
        val duration: TextView = item.arrDuration
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogHolder {
        val root = CallItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return CallLogHolder(root)
    }

    override fun onBindViewHolder(holder: CallLogHolder, position: Int) {
        val log = getItem(position)

        holder.name.text = if (log.name.isNullOrEmpty()) "Unknown" else log.name
        holder.number.text = log.number
        holder.dateTime.text = log.dateTime
        holder.duration.text = log.duration

        val type = getItem(position).type
        when (type) {
            "1" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(R.drawable.round_call_received_24, 0, 0, 0)
            "2" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(R.drawable.round_call_made_24, 0, 0, 0)
            "3" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(R.drawable.missed, 0, 0, 0)
            "4" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_record_voice_over_24, 0, 0, 0)
            "5" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_call_end_24, 0, 0, 0)
            "6" -> holder.number.setCompoundDrawablesWithIntrinsicBounds(R.drawable.round_block_24, 0, 0, 0)
        }


        val isSelected = selectedItemIds.contains(log.id)
        (holder.itemView as MaterialCardView).isChecked = isSelected


        holder.itemView.setOnClickListener {
            if (selectedItemIds.isNotEmpty()) {

                toggleSelection(log.id!!)
            } else {

                onItemClickListener?.invoke(log)
            }
        }


        holder.itemView.setOnLongClickListener {
            if (selectedItemIds.isEmpty()) {
                toggleSelection(log.id!!)
            }
            true
        }
    }

    fun setOnItemClickListener(listener: (HistoryModel) -> Unit) {
        onItemClickListener = listener
    }

    // --- NEW: Selection Helper Methods ---
    private fun toggleSelection(id: String) {
        if (selectedItemIds.contains(id)) {
            selectedItemIds.remove(id)
        } else {
            selectedItemIds.add(id)
        }
        notifyDataSetChanged()
        onSelectionModeChange?.invoke(selectedItemIds.isNotEmpty(), selectedItemIds.size)
    }

    fun clearSelection() {
        selectedItemIds.clear()
        notifyDataSetChanged()

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