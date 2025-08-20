package ru.iplc.smart_road.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.iplc.smart_road.db.AppDatabase
import ru.iplc.smart_road.db.PotholeDao
import ru.iplc.smart_road.R
import ru.iplc.smart_road.databinding.FragmentGarageBinding
import ru.iplc.smart_road.databinding.FragmentSettingsBinding
import ru.iplc.smart_road.worker.PotholeUploadWorker
import java.util.concurrent.TimeUnit

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SettingsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var dao: PotholeDao

    private var lastSent = 0
    private var lastNew = 0

    private val updateJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + updateJob)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = AppDatabase.getInstance(requireContext())
        dao = db.potholeDao()

        uiScope.launch {
            while (isActive) {
                val countInDb = withContext(Dispatchers.IO) { dao.getCount() }

                // допустим, эти значения ты где-то сохраняешь
                val sentCount = withContext(Dispatchers.IO) { dao.getSentCount() }
                val newCount = withContext(Dispatchers.IO) { dao.getNewCount() }

                val sendSpeed = sentCount - lastSent
                val receiveSpeed = newCount - lastNew

                lastSent = sentCount
                lastNew = newCount

                view.findViewById<TextView>(R.id.tvDbCount).text = "В базе: $countInDb"
                view.findViewById<TextView>(R.id.tvSentCount).text = "Отправлено: $sentCount"
                view.findViewById<TextView>(R.id.tvNewCount).text = "Буфер на отправку: $newCount"
                view.findViewById<TextView>(R.id.tvSendSpeed).text = "Скорость отправки: $sendSpeed/с"
                view.findViewById<TextView>(R.id.tvReceiveSpeed).text = "Скорость поступления: $receiveSpeed/с"

                delay(1000) // обновление каждую секунду
            }
        }
        setupToolbar()
    }


    private fun setupToolbar() {
        binding.toolbarSettings.apply {
            title = "О приложении"
            setNavigationOnClickListener { findNavController().navigateUp() }

            // Подключаем меню
            inflateMenu(R.menu.menu_settings)


            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_send -> {
                        val workManager = WorkManager.getInstance(requireContext())

                        val uploadRequest = OneTimeWorkRequestBuilder<PotholeUploadWorker>()
                            .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                10, TimeUnit.SECONDS
                            )
                            .build()

                        // Используем уникальную работу, чтобы не создавать несколько параллельных воркеров
                        workManager.enqueueUniqueWork(
                            "pothole_upload_work",
                            androidx.work.ExistingWorkPolicy.KEEP,
                            uploadRequest
                        )

                        Toast.makeText(requireContext(), "Начата отправка данных...", Toast.LENGTH_SHORT).show()

                        workManager.getWorkInfoByIdLiveData(uploadRequest.id)
                            .observe(viewLifecycleOwner) { workInfo ->
                                when (workInfo?.state) {
                                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                                        Toast.makeText(requireContext(), "Данные успешно отправлены", Toast.LENGTH_SHORT).show()
                                    }
                                    androidx.work.WorkInfo.State.FAILED -> {
                                        val error = workInfo.outputData.getString("error_message")
                                        Toast.makeText(requireContext(), "Ошибка отправки: $error", Toast.LENGTH_LONG).show()
                                    }
                                    androidx.work.WorkInfo.State.ENQUEUED,
                                    androidx.work.WorkInfo.State.RUNNING,
                                    androidx.work.WorkInfo.State.BLOCKED -> {
                                        // Можно показать индикатор прогресса
                                    }
                                    androidx.work.WorkInfo.State.CANCELLED, null -> {
                                        // Можно игнорировать или показать уведомление
                                        Toast.makeText(requireContext(), "Отправка отменена", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }


                        true
                    }
                    else -> false
                }
            }

        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        updateJob.cancel()
    }
}
