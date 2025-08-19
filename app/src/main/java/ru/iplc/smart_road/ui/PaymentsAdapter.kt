package ru.iplc.smart_road.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import android.view.View
import ru.iplc.smart_road.R
import ru.iplc.smart_road.data.model.Payment

class PaymentsAdapter(private val payments: List<Payment>) :
    RecyclerView.Adapter<PaymentsAdapter.PaymentViewHolder>() {

    class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvPaymentMethod: TextView = itemView.findViewById(R.id.tvPaymentMethod)
        val tvNotes: TextView = itemView.findViewById(R.id.tvNotes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = payments[position]
        holder.tvDate.text = payment.date
        holder.tvAmount.text = "${payment.amount} ₽"
        holder.tvPaymentMethod.text = payment.paymentMethod

        // Показываем примечание, если оно есть
        payment.notes?.let {
            holder.tvNotes.text = it
            holder.tvNotes.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = payments.size
}