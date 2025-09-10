package ru.iplc.smart_road.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
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

class ContactsFragment  : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val updateJob = Job()


    val aboutText = """
    <h2>Контакты</h2>
    <p>Мы создаём инновационную систему для общей безопасности, экономии и комфорта на дорогах России. 
    Готовы ответить на вопросы и надеемся на вашу поддержку!</p>

    <p><b>Юрий Струк:</b> +7 (909) 668-69-96, <a href="https://t.me/omhotey">t.me/omhotey</a></p>

    <p><b>Группа в Telegram:</b> <a href="https://t.me/smart_road">t.me/smart_road</a></p>

    <p><b>Email:</b> <a href="mailto:info@smart-roads.ru">info@smart-roads.ru</a></p>

    <p><b>Сайт:</b> <a href="https://smart-roads.ru">smart-roads.ru</a></p>
""".trimIndent()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvAboutApp.text = HtmlCompat.fromHtml(aboutText, HtmlCompat.FROM_HTML_MODE_LEGACY)


        setupToolbar()
    }


    private fun setupToolbar() {
        binding.toolbarSettings.apply {
            title = "Контакты"
            setNavigationOnClickListener {
                findNavController().navigate(R.id.nav_home)
            }

        }
    }


    override fun onDestroyView() {
        super.onDestroyView()

    }
}