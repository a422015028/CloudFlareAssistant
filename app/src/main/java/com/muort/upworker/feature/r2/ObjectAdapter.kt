package com.muort.upworker.feature.r2

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.R2Object
import com.muort.upworker.databinding.ItemR2ObjectBinding

class ObjectAdapter : RecyclerView.Adapter<ObjectAdapter.ViewHolder>() {
    private var objects = listOf<R2Object>()
    private var onObjectClick: ((R2Object) -> Unit)? = null

    fun submitList(newList: List<R2Object>) {
        objects = newList
        notifyDataSetChanged()
    }

    fun setOnObjectClickListener(listener: (R2Object) -> Unit) {
        onObjectClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemR2ObjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onObjectClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(objects[position])
    }

    override fun getItemCount() = objects.size

    class ViewHolder(
        private val binding: ItemR2ObjectBinding,
        private val onObjectClick: ((R2Object) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(obj: R2Object) {
            binding.objectKeyText.text = obj.key
            binding.objectSizeText.text = obj.size?.let { "${it} B" } ?: "-"
            binding.root.setOnClickListener {
                onObjectClick?.invoke(obj)
            }
        }
    }
}