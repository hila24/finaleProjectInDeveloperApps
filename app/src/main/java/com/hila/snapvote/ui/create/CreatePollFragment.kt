package com.hila.snapvote.ui.create

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.databinding.FragmentCreatePollBinding
import com.hila.snapvote.ui.common.BaseFragment
import com.hila.snapvote.ui.common.pollArgs
import java.io.File

/** Upload images, pick a deadline, choose the voting style, publish. */
class CreatePollFragment :
    BaseFragment<FragmentCreatePollBinding>(FragmentCreatePollBinding::inflate) {

    private val viewModel: CreatePollViewModel by viewModels()
    private val adapter by lazy { SelectedImagesAdapter(onRemove = viewModel::removeImage) }

    /** Where the camera writes the photo it just took. */
    private var pendingCameraUri: Uri? = null

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CreatePollViewModel.MAX_IMAGES)
    ) { uris ->
        viewModel.addImages(requireContext(), uris)
    }

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) viewModel.addImages(requireContext(), listOf(uri))
        pendingCameraUri = null
    }

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else showMessage("אין הרשאה למצלמה")
    }

    /** Asked once, when the first poll is published – the reminders need it on Android 13+. */
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* reminders are a bonus – publish either way */ }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.imagesList.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        binding.pickGalleryButton.setOnClickListener {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.takePhotoButton.setOnClickListener { ensureCameraPermission() }

        binding.deadlineGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.deadlineHours = when (checkedIds.firstOrNull()) {
                R.id.deadline1h -> 1
                R.id.deadline6h -> 6
                R.id.deadline3d -> 72
                else -> 24
            }
        }
        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.mode =
                if (checkedId == R.id.modeRating) Poll.MODE_RATING else Poll.MODE_SINGLE
        }

        binding.publishButton.setOnClickListener {
            ensureNotificationPermission()
            publish()
        }

        viewModel.images.observe(viewLifecycleOwner) { adapter.submitList(it) }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progress.isVisible = loading
            binding.publishButton.isEnabled = !loading
        }
        viewModel.uploadStatus.observe(viewLifecycleOwner) { status ->
            binding.uploadStatus.isVisible = status != null
            binding.uploadStatus.text = status
        }
        observeMessage(viewModel.error, viewModel::errorShown)
        viewModel.createdPollId.observe(viewLifecycleOwner) { pollId ->
            if (pollId == null) return@observe
            navigateSafely(R.id.action_create_to_results, pollArgs(pollId))
        }
    }

    private fun publish() {
        val question = binding.questionInput.text?.toString().orEmpty().trim()
        if (question.isEmpty()) {
            showMessage(R.string.error_need_question)
            return
        }
        if (viewModel.images.value.orEmpty().size < 2) {
            showMessage(R.string.error_need_two_images)
            return
        }
        viewModel.publish(requireContext(), question)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val dir = File(requireContext().cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        pendingCameraUri = uri
        takePhoto.launch(uri)
    }

    override fun onDestroyView() {
        binding.imagesList.adapter = null
        super.onDestroyView()
    }
}
