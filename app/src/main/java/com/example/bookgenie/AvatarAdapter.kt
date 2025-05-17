package com.example.bookgenie // Paket adınızı kendi projenize göre güncelleyin

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class AvatarAdapter(
    private val context: Context,
    private val avatarList: List<Int>, // Drawable resource ID'leri
    private val onAvatarSelected: (Int) -> Unit // Seçilen avatarın resource ID'sini döner
) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        // item_avatar.xml layout'unu inflate ediyoruz
        val view = LayoutInflater.from(context).inflate(R.layout.recycler_view_avatar_item, parent, false)
        return AvatarViewHolder(view)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val avatarResId = avatarList[position]
        // Glide kütüphanesi ile drawable'dan resmi yüklüyoruz
        Glide.with(context)
            .load(avatarResId)
            .into(holder.imageViewAvatar)

        // Avatar seçildiğinde tıklama olayını tetikliyoruz
        holder.itemView.setOnClickListener {
            onAvatarSelected(avatarResId)
        }
    }

    override fun getItemCount(): Int = avatarList.size

    class AvatarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // item_avatar.xml içindeki ImageView'a erişiyoruz
        val imageViewAvatar: ImageView = itemView.findViewById(R.id.imageViewAvatarItem)
    }
}
