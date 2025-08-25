package ru.iplc.smart_road.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ru.iplc.smart_road.R
import ru.iplc.smart_road.data.local.PreferenceStorage
import ru.iplc.smart_road.data.model.Car
import ru.iplc.smart_road.databinding.FragmentGarageBinding
import java.util.Calendar

class GarageFragment : Fragment() {
    private var _binding: FragmentGarageBinding? = null
    private val binding get() = _binding!!
    private lateinit var carAdapter: CarAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGarageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadCars()
    }

    private fun setupToolbar() {
        binding.toolbarGarage.apply {
            title = "Гараж"
            setNavigationOnClickListener {
                findNavController().navigate(R.id.nav_home)
            }
        }
    }

    private fun setupRecyclerView() {
        carAdapter = CarAdapter(
            cars = emptyList(),
            onItemClick = { car -> showCarOptionsDialog(car) },
            onDefaultSelected = { car -> setDefaultCar(car) }
        )

        binding.recyclerViewCars.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = carAdapter
        }
    }

    private fun setupFab() {
        binding.buttonAddCar.setOnClickListener {
            showAddCarDialog()
        }
    }

    private fun loadCars() {
        val cars = PreferenceStorage.getCars()
        carAdapter.updateCars(cars)

        if (cars.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
        }
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.recyclerViewCars.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyState.visibility = View.GONE
        binding.recyclerViewCars.visibility = View.VISIBLE
    }

    private fun showAddCarDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_car, null)

        // Находим TextInputLayout по id
        val vinLayout = dialogView.findViewById<TextInputLayout>(R.id.vinLayout)
        val brandLayout = dialogView.findViewById<TextInputLayout>(R.id.brandLayout)
        val modelLayout = dialogView.findViewById<TextInputLayout>(R.id.modelLayout)
        val yearLayout = dialogView.findViewById<TextInputLayout>(R.id.yearLayout)

        // Находим EditText и чекбокс
        val vinInput = dialogView.findViewById<TextInputEditText>(R.id.vinInput)
        val brandInput = dialogView.findViewById<TextInputEditText>(R.id.brandInput)
        val modelInput = dialogView.findViewById<TextInputEditText>(R.id.modelInput)
        val yearInput = dialogView.findViewById<TextInputEditText>(R.id.yearInput)
        val defaultCheckbox = dialogView.findViewById<CheckBox>(R.id.defaultCheckbox)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить автомобиль")
            .setView(dialogView)
            .setPositiveButton("Добавить", null) // обработчик позже
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val vin = vinInput.text.toString().trim()
                val brand = brandInput.text.toString().trim()
                val model = modelInput.text.toString().trim()
                val yearStr = yearInput.text.toString().trim()

                // Валидация VIN
                if (vin.isEmpty()) {
                    vinLayout.error = "Введите VIN номер"
                    vinInput.requestFocus()
                    return@setOnClickListener
                } else {
                    vinLayout.error = null
                }

                // Валидация марки
                if (brand.isEmpty()) {
                    brandLayout.error = "Введите марку"
                    brandInput.requestFocus()
                    return@setOnClickListener
                } else {
                    brandLayout.error = null
                }

                // Валидация модели
                if (model.isEmpty()) {
                    modelLayout.error = "Введите модель"
                    modelInput.requestFocus()
                    return@setOnClickListener
                } else {
                    modelLayout.error = null
                }

                // Валидация года
                val year = try {
                    yearStr.toInt()
                } catch (e: NumberFormatException) {
                    yearLayout.error = "Некорректный год"
                    yearInput.requestFocus()
                    return@setOnClickListener
                }

                if (year < 1900 || year > Calendar.getInstance().get(Calendar.YEAR) + 1) {
                    yearLayout.error = "Недопустимый год"
                    yearInput.requestFocus()
                    return@setOnClickListener
                } else {
                    yearLayout.error = null
                }

                // Создаем новый автомобиль
                val newCar = Car(vin, brand, model, year)

                val currentCars = PreferenceStorage.getCars().toMutableList()

                // Проверка дубликата VIN
                if (currentCars.any { it.vin.equals(vin, ignoreCase = true) }) {
                    vinLayout.error = "Авто с таким VIN уже есть"
                    vinInput.requestFocus()
                    return@setOnClickListener
                }

                currentCars.add(newCar)
                PreferenceStorage.saveCars(currentCars)

                // Если основной автомобиль
                if (defaultCheckbox.isChecked) {
                    PreferenceStorage.saveDefaultCar(newCar)
                }

                carAdapter.updateCars(currentCars)
                hideEmptyState()
                showSnackbar("Автомобиль добавлен")
                dialog.dismiss()
            }
        }

        dialog.show()
    }


    private fun addNewCar(car: Car) {
        val currentCars = PreferenceStorage.getCars().toMutableList()
        currentCars.add(car)
        PreferenceStorage.saveCars(currentCars)
        carAdapter.updateCars(currentCars)
        hideEmptyState()
        showSnackbar("Автомобиль добавлен")
    }

    private fun showCarOptionsDialog(car: Car) {
        val options = arrayOf(
            "Удалить",
            if (PreferenceStorage.getDefaultCar()?.vin == car.vin) "По умолчанию ✓" else "Сделать по умолчанию"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${car.brand} ${car.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> deleteCar(car)
                    1 -> setDefaultCar(car)
                }
            }
            .show()
    }

    private fun deleteCar(car: Car) {
        val currentCars = PreferenceStorage.getCars().toMutableList()
        currentCars.removeAll { it.vin == car.vin }
        PreferenceStorage.saveCars(currentCars)

        // Если удаляем машину по умолчанию - сбрасываем выбор
        if (PreferenceStorage.getDefaultCar()?.vin == car.vin) {
            PreferenceStorage.saveDefaultCar(null)
        }

        carAdapter.updateCars(currentCars)
        showSnackbar("Автомобиль удален")

        if (currentCars.isEmpty()) {
            showEmptyState()
        }
    }

    private fun setDefaultCar(car: Car) {
        PreferenceStorage.saveDefaultCar(car)
        carAdapter.updateCars(PreferenceStorage.getCars())
        showSnackbar("${car.brand} ${car.name} - теперь по умолчанию")
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}