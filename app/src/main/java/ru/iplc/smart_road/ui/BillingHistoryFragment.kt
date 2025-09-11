package ru.iplc.smart_road.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import ru.iplc.smart_road.R
import ru.iplc.smart_road.data.model.Car
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import ru.iplc.smart_road.data.model.Payment
import ru.iplc.smart_road.databinding.FragmentBillingHistoryBinding
import ru.iplc.smart_road.databinding.FragmentGarageBinding
import ru.iplc.smart_road.databinding.FragmentSettingDeviceBinding

class BillingHistoryFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private var _binding: FragmentBillingHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBillingHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvPayments)

        // Тестовые данные (замените на реальные из БД/API)
        val payments = listOf(
            Payment("15.03.2024", 299, "Google Pay", "Ежемесячная подписка"),
            Payment("15.03.2024", 999, "RuStore", "Годовая подписка"),
            Payment("20.03.2024", 299, "Банковская карта"),
            Payment("05.04.2024", 299, "СБП (Сбербанк)", "Автопродление")
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = PaymentsAdapter(payments)
        //setupToolbar()
    }
//    private fun setupToolbar() {
//        binding.toolbarBill.apply {
//            title = "Биллинг"
//            setNavigationOnClickListener {
//                findNavController().navigate(R.id.nav_home)
//            }
//        }
//    }
}