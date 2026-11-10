package com.jarvis.ai

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

class MessageAdapter(
    private val items: List<Message>,
    private val prefs: Prefs
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(val tv: TextView, root: FrameLayout) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false) as FrameLayout
        return VH(v.findViewById(R.id.tv), v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        val tv = h.tv
        val dm = tv.resources.displayMetrics
        val onVariant = MaterialColors.getColor(tv, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val lp = tv.layoutParams as FrameLayout.LayoutParams
        val bg = GradientDrawable()
        bg.cornerRadius = 18 * dm.density

        tv.text = m.text
        tv.textSize = prefs.fontSize.toFloat()
        tv.maxWidth = (dm.widthPixels * 0.82).toInt()
        tv.setTypeface(null, Typeface.NORMAL)

        when (m.role) {
            "user" -> {
                lp.gravity = Gravity.END
                bg.setColor(prefs.accent)
                tv.setTextColor(Color.WHITE)
            }
            "ai" -> {
                lp.gravity = Gravity.START
                bg.setColor(MaterialColors.getColor(tv, com.google.android.material.R.attr.colorSurfaceVariant))
                tv.setTextColor(onVariant)
            }
            else -> { // sys
                lp.gravity = Gravity.CENTER_HORIZONTAL
                bg.setColor(Color.TRANSPARENT)
                tv.setTextColor(onVariant)
                tv.setTypeface(null, Typeface.ITALIC)
                tv.textSize = (prefs.fontSize - 3).coerceAtLeast(10).toFloat()
            }
        }
        tv.layoutParams = lp
        tv.background = bg
    }
}
