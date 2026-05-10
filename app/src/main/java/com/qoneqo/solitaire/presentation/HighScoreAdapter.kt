package com.qoneqo.solitaire.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.qoneqo.solitaire.R
import com.qoneqo.solitaire.data.StatEntity
import com.qoneqo.solitaire.utils.TimeUtils

class HighScoreAdapter(private val scores: List<StatEntity>) :
    RecyclerView.Adapter<HighScoreAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rankText: TextView = view.findViewById(R.id.rankText)
        val scoreText: TextView = view.findViewById(R.id.scoreText)
        val detailsText: TextView = view.findViewById(R.id.detailsText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_high_score, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = scores[position]
        holder.rankText.text = "#${position + 1}"
        holder.scoreText.text = "Score: ${stat.score}"
        holder.detailsText.text = "Moves: ${stat.moves} | Time: ${TimeUtils.formatTime(stat.timeElapsedSeconds)}"
    }

    override fun getItemCount() = scores.size
}
