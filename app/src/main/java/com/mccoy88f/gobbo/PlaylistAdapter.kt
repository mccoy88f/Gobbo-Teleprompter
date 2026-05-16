package com.mccoy88f.gobbo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mccoy88f.gobbo.databinding.ItemPlaylistRowBinding
import kotlin.math.absoluteValue

class PlaylistAdapter(
    private val onOpen: (Int) -> Unit,
    private val onEdit: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

    private val items = mutableListOf<Pair<PlaylistItem, Boolean>>()

    fun submit(list: List<PlaylistItem>, currentIndex: Int) {
        items.clear()
        list.forEachIndexed { i, it -> items += it to (i == currentIndex) }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPlaylistRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemPlaylistRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(pair: Pair<PlaylistItem, Boolean>) {
            val (item, selected) = pair
            binding.playlistItemName.text = item.displayName

            val ctx = binding.root.context
            val presetSec = item.durationSeconds ?: item.lastParsedTagSeconds
            val presetStr =
                if (presetSec != null && presetSec > 0) formatDuration(ctx, presetSec)
                else ctx.getString(R.string.playlist_duration_auto)
            val measuredStr = item.recordedElapsedSeconds?.let { formatDuration(ctx, it) } ?: "—"

            binding.playlistItemMeta.text =
                ctx.getString(R.string.playlist_item_meta, presetStr, measuredStr) +
                    if (selected) " ✓" else ""

            binding.root.setOnClickListener { onOpen(adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener) }
            binding.btnPlaylistEdit.setOnClickListener { onEdit(adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener) }
            binding.btnPlaylistRemove.setOnClickListener { onRemove(adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener) }
        }
    }

    companion object {
        fun formatDuration(context: android.content.Context, totalSeconds: Int): String {
            val neg = totalSeconds < 0
            val abs = totalSeconds.absoluteValue
            val m = abs / 60
            val s = abs % 60
            val base = "%d:%02d".format(m, s)
            return if (neg) "-$base" else base
        }
    }
}
