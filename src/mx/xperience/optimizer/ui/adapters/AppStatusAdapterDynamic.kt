// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 XPerience Project

package mx.xperience.optimizer.ui.adapters

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mx.xperience.optimizer.R

data class AppStatusDynamic(
    val name: String,
    val icon: Drawable,
    var status: Status,
    val packageName: String
)

enum class Status { PENDING, RUNNING, DONE }

class AppStatusAdapterDynamic(private val apps: List<AppStatusDynamic>) :
    RecyclerView.Adapter<AppStatusAdapterDynamic.AppViewHolder>() {

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_app_icon)
        val name: TextView = view.findViewById(R.id.tv_app_name)
        val statusIcon: ImageView = view.findViewById(R.id.iv_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_status, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.name
        when (app.status) {
            Status.PENDING -> holder.statusIcon.visibility = View.GONE
            Status.RUNNING -> {
                holder.statusIcon.visibility = View.VISIBLE
                holder.statusIcon.setImageResource(R.drawable.ic_sync)
            }
            Status.DONE -> {
                holder.statusIcon.visibility = View.VISIBLE
                holder.statusIcon.setImageResource(R.drawable.ic_check)
            }
        }
    }

    override fun getItemCount() = apps.size
}
