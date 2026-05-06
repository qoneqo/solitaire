package com.qoneqo.solitaire.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qoneqo.solitaire.R

class TableColorAdapter(
    private val colors: List<String>,
    private val onColorSelected: (String) -> Unit
) : RecyclerView.Adapter<TableColorAdapter.ColorViewHolder>() {

    class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorView: View = view.findViewById(R.id.colorView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val colorStr = colors[position]
        val colorInt = android.graphics.Color.parseColor(colorStr)
        holder.colorView.background.setTint(colorInt)
        holder.itemView.setOnClickListener { onColorSelected(colorStr) }
    }

    override fun getItemCount(): Int = colors.size
}
