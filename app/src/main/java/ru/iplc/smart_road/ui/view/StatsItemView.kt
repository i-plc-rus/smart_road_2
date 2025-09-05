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

    private val statValueView: TextView
    private val statLabelView: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.stats_item, this, true)

        statValueView = findViewById(R.id.stat_value)
        statLabelView = findViewById(R.id.stat_label)

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StatsItem)

        statValueView.text =
            typedArray.getInt(R.styleable.StatsItem_statValue, 0).toString()

        statLabelView.text =
            typedArray.getString(R.styleable.StatsItem_statLabel)

        typedArray.recycle()
    }

    fun setStatValue(value: Int) {
        statValueView.text = value.toString()
    }

    fun setStatValue(value: String) {
        statValueView.text = value
    }

    fun setStatLabel(label: String?) {
        statLabelView.text = label
    }
}
