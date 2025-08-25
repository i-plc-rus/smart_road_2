package ru.iplc.smart_road.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ru.iplc.smart_road.R
import ru.iplc.smart_road.data.local.PreferenceStorage
import ru.iplc.smart_road.data.model.InstallationMethod
import ru.iplc.smart_road.databinding.FragmentSettingDeviceBinding

class SettingDeviceFragment : Fragment() {

    private var _binding: FragmentSettingDeviceBinding? = null
    private val binding get() = _binding!!

    private var selectedMethod: InstallationMethod = InstallationMethod.CRADLE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Загружаем сохранённое значение или выбираем CRADLE по умолчанию
        selectedMethod = PreferenceStorage.getInstallationMethod() ?: InstallationMethod.CRADLE
        updateSelectionUI()

        binding.optionCradle.setOnClickListener {
            selectedMethod = InstallationMethod.CRADLE
            updateSelectionUI()
        }

        binding.optionDashboard.setOnClickListener {
            selectedMethod = InstallationMethod.DASHBOARD
            updateSelectionUI()
        }

        binding.optionTunnel.setOnClickListener {
            selectedMethod = InstallationMethod.TUNNEL
            updateSelectionUI()
        }

        binding.saveButton.setOnClickListener {
            PreferenceStorage.saveInstallationMethod(selectedMethod)
            parentFragmentManager.popBackStack() // закрываем фрагмент
        }

        setupToolbar()
    }

    private fun setupToolbar() {
        binding.toolbarSettingDevice.apply {
            title = "Способ установки"
            setNavigationOnClickListener {
                findNavController().navigate(R.id.nav_home)
            }
        }
    }

    private fun updateSelectionUI() {
        // Сбрасываем все
        binding.radioCradle.isChecked = false
        binding.radioDashboard.isChecked = false
        binding.radioTunnel.isChecked = false

        // Выбираем текущий
        when (selectedMethod) {
            InstallationMethod.CRADLE -> binding.radioCradle.isChecked = true
            InstallationMethod.DASHBOARD -> binding.radioDashboard.isChecked = true
            InstallationMethod.TUNNEL -> binding.radioTunnel.isChecked = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
