package ru.iplc.smart_road.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ru.iplc.smart_road.R
import ru.iplc.smart_road.data.local.PreferenceStorage
import ru.iplc.smart_road.data.model.Car
import ru.iplc.smart_road.databinding.ItemCarBinding
import android.util.TypedValue

class CarAdapter(
    private var cars: List<Car>,
    private val onItemClick: (Car) -> Unit,
    private val onDefaultSelected: (Car) -> Unit
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {

    inner class CarViewHolder(private val binding: ItemCarBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(car: Car) {
            binding.apply {
                vinText.text = "VIN: ${car.vin}"
                brandText.text = car.brand
                modelText.text = car.name
                yearText.text = car.year.toString()

                val isDefault = PreferenceStorage.getDefaultCar()?.vin == car.vin

                // Получаем цвет через атрибут темы
                /*val typedValue = TypedValue()
                val attr = if (isDefault) R.attr.colorCardSelected else R.attr.cardBackgroundColor
                itemView.context.theme.resolveAttribute(attr, typedValue, true)
                val backgroundColor = typedValue.data
                cardView.setCardBackgroundColor(backgroundColor)*/

                // Управление видимостью иконки
                defaultIcon.visibility = if (isDefault) View.VISIBLE else View.GONE

                // Текст кнопки
                defaultButton.text = if (isDefault) {
                    itemView.context.getString(R.string.default_selected)
                } else {
                    itemView.context.getString(R.string.set_default)
                }

                // Обработчики кликов
                root.setOnClickListener { onItemClick(car) }
                defaultButton.setOnClickListener { onDefaultSelected(car) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
        val binding = ItemCarBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CarViewHolder, position: Int) {
        holder.bind(cars[position])
    }

    override fun getItemCount(): Int = cars.size

    fun updateCars(newCars: List<Car>) {
        cars = newCars
        notifyDataSetChanged()
    }
}