package com.edwardstock.leveldb.example

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.edwardstock.leveldb.example.databinding.DbItemBinding


class RowsAdapter(
    private val onDelete: (TextItem) -> Unit
) : RecyclerView.Adapter<RowsAdapter.ViewHolder>() {

    private var inflater: LayoutInflater? = null
    private var items: MutableList<TextItem> = ArrayList()

    class ViewHolder(val binding: DbItemBinding) : RecyclerView.ViewHolder(binding.root)

    fun setData(data: List<TextItem>) {
        if (items.isNotEmpty()) {
            notifyItemRangeRemoved(0, items.size)
        }
        items = data.toMutableList()
        notifyItemRangeInserted(0, items.size)
    }

    fun addItem(item: TextItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun removeItem(item: TextItem) {
        var idx: Int = -1
        items.forEachIndexed { index, textItem ->
            if (textItem == item) {
                idx = index
            }
        }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (inflater == null) {
            inflater = LayoutInflater.from(parent.context)
        }

        return ViewHolder(
            DbItemBinding.inflate(inflater!!, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = items[position]
        holder.binding.text.text = data.text
        holder.binding.actionDelete.setOnClickListener {
            onDelete(items[holder.bindingAdapterPosition])
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
