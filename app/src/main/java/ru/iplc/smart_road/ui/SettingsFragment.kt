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

    val aboutText = """
        <h2>О приложении «Умные дороги»</h2>
    
        <h3>Почему это важно?</h3>
        <p>
        Плохое качество дорог — это не только дискомфорт, но и реальная опасность для каждого водителя. 
        Ежегодно в России из-за неудовлетворительного состояния дорожной инфраструктуры происходит более 
        <b>35 тысяч ДТП</b>, в которых страдает свыше <b>46 тысяч человек</b> и погибает около <b>4 тысяч</b>. 
        Потери жителей от последствий плохих дорог превышают <b>1,3 трлн рублей</b> в год, а средние убытки на одного водителя — около 
        <b>30 тысяч рублей ежегодно</b>. Время создания и обработки заявки от жителей более 20 минут. 
        Вместе мы можем изменить ситуацию и сделать поездки безопаснее и экономичнее.
        </p>
        
        <p>
        Ваш опыт поможет обучить искусственный интеллект лучше определять проблемы дорог в автоматическом режиме. 
        Спасибо, что установили приложение «Умные дороги»!
        </p>
        
        <h3>Наши амбициозные цели</h3>
        <ul>
            <li> Сократить количество ДТП на 10% — около 3,5 тысячи случаев в год</li>
            <li> Уменьшить число пострадавших в ДТП на 10% — около 4,7 тысячи человек в год</li>
            <li> Снизить расходы на обслуживание автомобиля на 35% — около 30 тысяч рублей на водителя в год</li>
            <li> Ускорить обработку заявок жителей в 4 раза — время подачи заявки сокращается до 4 минут</li>
        </ul>
        
        <p>
        Мы уже разработали прототип, который собирает данные с акселерометра, гироскопа, магнитометра и GPS. 
        ИИ распознает 10+ паттернов повреждений и анализирует дорожное движение, что позволяет прогнозировать аварийные участки.
        </p>
        
        <h3>Для кого мы?</h3>
        <p><b>Частные водители (B2C):</b> легковые, грузовые автомобили, мотоциклы, курьеры, семьи с несколькими авто, владельцы автодомов и спецтехники, путешественники.</p>
        <p><b>Бизнес (B2B):</b> транспорт и логистика, каршеринг и такси, страховые и финансовые компании, IT и промышленность, автошколы, туризм, охрана и ритейл.</p>
        <p><b>Государственные структуры (B2G):</b> федеральные, региональные и муниципальные органы, правоохранительные и экстренные службы, транспортные предприятия, научные и контролирующие организации, военные.</p>
        
        <h3>Как работает приложение?</h3>
        <ol>
            <li> Установите бесплатное приложение «Умные дороги».</li>
            <li> Добавьте свой автомобиль в раздел «Гараж» и укажите место крепления смартфона для корректного сбора данных.</li>
            <li> Передвигайтесь как обычно — приложение автоматически анализирует вибрации и ускорения, фиксирует дорожные проблемы и предупреждает об опасностях.</li>
            <li> Ваша активность помогает создать точную интерактивную карту и сокращать количество аварий по всей стране.</li>
        </ol>
        
        <h3>Присоединяйтесь к изменениям!</h3>
        <p>Мы находимся на этапе активного роста и приглашаем к сотрудничеству:</p>
        <ul>
            <li> Скачайте приложение и установите его в машину — даже 10 минут в день помогут собрать данные</li>
            <li> Поделитесь впечатлениями в соцсетях — ваши отзывы помогают нам улучшать приложение</li>
            <li> Расскажите друзьям — чем больше пользователей, тем точнее карта проблемных участков</li>
            <li> Поддержите краудфандингом — ваши инвестиции ускорят запуск SOS-системы и других функций</li>
        </ul>
        
        <p><i>P.S. Спасибо всем, кто уже с нами — тестировщикам, разработчикам, инвесторам и просто неравнодушным водителям. 
        Вместе мы создаем не просто приложение — мы меняем страну к лучшему!</i></p>
        
        <p><b>Контакты:</b> <a href="https://t.me/smart_road">t.me/smart_road</a> | 
        <a href="mailto:info@smart-roads.ru">info@smart-roads.ru</a> | 
        <a href="https://smart-roads.ru">smart-roads.ru</a></p>
        
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

//        db = AppDatabase.getInstance(requireContext())
//        dao = db.potholeDao()
//
//        uiScope.launch {
//            while (isActive) {
//                val countInDb = withContext(Dispatchers.IO) { dao.getCount() }
//
//                // допустим, эти значения ты где-то сохраняешь
//                val sentCount = withContext(Dispatchers.IO) { dao.getSentCount() }
//                val newCount = withContext(Dispatchers.IO) { dao.getNewCount() }
//
//                val sendSpeed = sentCount - lastSent
//                val receiveSpeed = newCount - lastNew
//
//                lastSent = sentCount
//                lastNew = newCount
//
//                view.findViewById<TextView>(R.id.tvDbCount).text = "В базе: $countInDb"
//                view.findViewById<TextView>(R.id.tvSentCount).text = "Отправлено: $sentCount"
//                view.findViewById<TextView>(R.id.tvNewCount).text = "Буфер на отправку: $newCount"
//                view.findViewById<TextView>(R.id.tvSendSpeed).text = "Скорость отправки: $sendSpeed/с"
//                view.findViewById<TextView>(R.id.tvReceiveSpeed).text = "Скорость поступления: $receiveSpeed/с"
//
//                delay(1000) // обновление каждую секунду
//            }
//        }
        setupToolbar()
    }


    private fun setupToolbar() {
        binding.toolbarSettings.apply {
            title = "О приложении"
            setNavigationOnClickListener {
                findNavController().navigate(R.id.nav_home)
            }

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
