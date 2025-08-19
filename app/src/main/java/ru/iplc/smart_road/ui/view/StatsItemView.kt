package ru.iplc.smart_road.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import ru.iplc.smart_road.R

class StatsItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.stats_item, this, true)

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StatsItem)

        findViewById<TextView>(R.id.stat_value).text =
            typedArray.getInt(R.styleable.StatsItem_statValue, 0).toString()

        findViewById<TextView>(R.id.stat_label).text =
            typedArray.getString(R.styleable.StatsItem_statLabel)

        typedArray.recycle()
    }
}