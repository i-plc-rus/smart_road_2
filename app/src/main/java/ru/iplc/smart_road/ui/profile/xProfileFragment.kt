package ru.iplc.smart_road.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.iplc.smart_road.data.local.PreferenceStorage
import ru.iplc.smart_road.databinding.FragmentProfileBinding
import com.bumptech.glide.Glide
import ru.iplc.smart_road.ui.auth.AuthActivity

class xProfileFragment : Fragment() {
    private lateinit var binding: FragmentProfileBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val profile = PreferenceStorage.getProfile()
        if (profile != null) {
            //binding.usernameText.text = profile.username
            //binding.phoneText.text = profile.phone
            //Glide.with(this).load(profile.avatarUrl).into(binding.avatarImage)
        }

        /*binding.logoutButton.setOnClickListener {
            PreferenceStorage.clear()
            requireActivity().startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }*/
    }
}
