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
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat as MaterialTimeFormat
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.databinding.FragmentCreatePollBinding
import com.hila.snapvote.ui.common.BaseFragment
import com.hila.snapvote.ui.common.pollArgs
import com.hila.snapvote.util.Deadlines
import com.hila.snapvote.util.TimeFormat
import java.io.File
import java.util.Calendar

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

        // Clear leftovers from a poll that was started and abandoned. Guarded by a flag
        // on the ViewModel, which survives rotation – doing it on every onViewCreated
        // would delete the copies of images the user has already picked.
        if (!viewModel.clearedStaleCopies) {
            viewModel.clearedStaleCopies = true
            viewModel.clearAbandonedCopies(requireContext().applicationContext)
        }

        binding.pickGalleryButton.setOnClickListener {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.takePhotoButton.setOnClickListener { ensureCameraPermission() }

        // Presets clear a hand-picked time; the custom chip is driven by its own click
        // listener, so that tapping it again lets you choose a different moment.
        binding.deadlineGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checked = checkedIds.firstOrNull()
            if (checked == R.id.deadlineCustom) return@setOnCheckedStateChangeListener
            viewModel.setCustomDeadline(null)
            viewModel.deadlineHours = when (checked) {
                R.id.deadline1h -> 1
                R.id.deadline6h -> 6
                R.id.deadline3d -> 72
                else -> 24
            }
        }
        binding.deadlineCustom.setOnClickListener { showDatePicker() }
        viewModel.customDeadline.observe(viewLifecycleOwner) { at ->
            binding.deadlineCustom.text =
                if (at == null) getString(R.string.deadline_custom) else TimeFormat.dateTime(at)
        }
        restoreOpenPickers()
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

    // ------------------------------------------------------ custom deadline

    /**
     * Day first, then clock. Both dialogs are DialogFragments that survive a rotation
     * on their own, but their listeners do not – see [restoreOpenPickers].
     */
    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.deadline_pick_date))
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build()
            )
            .build()
        attachDateListeners(picker)
        picker.show(childFragmentManager, TAG_DATE)
    }

    private fun attachDateListeners(picker: MaterialDatePicker<Long>) {
        picker.addOnPositiveButtonClickListener { utcMidnight ->
            viewModel.pendingDateUtc = utcMidnight
            showTimePicker()
        }
        picker.addOnNegativeButtonClickListener { revertToPresetIfUnset() }
        picker.addOnCancelListener { revertToPresetIfUnset() }
    }

    private fun showTimePicker() {
        val now = Calendar.getInstance()
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(MaterialTimeFormat.CLOCK_24H)
            .setHour(now.get(Calendar.HOUR_OF_DAY))
            .setMinute(now.get(Calendar.MINUTE))
            .setTitleText(getString(R.string.deadline_pick_time))
            .build()
        attachTimeListeners(picker)
        picker.show(childFragmentManager, TAG_TIME)
    }

    private fun attachTimeListeners(picker: MaterialTimePicker) {
        picker.addOnPositiveButtonClickListener {
            commitCustomDeadline(picker.hour, picker.minute)
        }
        picker.addOnNegativeButtonClickListener { revertToPresetIfUnset() }
        picker.addOnCancelListener { revertToPresetIfUnset() }
    }

    /** Re-attaches the listeners to dialogs that were already open before a rotation. */
    private fun restoreOpenPickers() {
        @Suppress("UNCHECKED_CAST")
        (childFragmentManager.findFragmentByTag(TAG_DATE) as? MaterialDatePicker<Long>)
            ?.let(::attachDateListeners)
        (childFragmentManager.findFragmentByTag(TAG_TIME) as? MaterialTimePicker)
            ?.let(::attachTimeListeners)
    }

    /**
     * Joins the chosen day and the chosen clock time. The date dialog reports UTC
     * midnight, so the day has to be read in UTC before being rebuilt in local time.
     */
    private fun commitCustomDeadline(hour: Int, minute: Int) {
        val dayUtc = viewModel.pendingDateUtc ?: return revertToPresetIfUnset()
        val chosen = Deadlines.combine(dayUtc, hour, minute)
        viewModel.pendingDateUtc = null

        // Today is a legal date and 09:00 is a legal time, but together they can be
        // in the past – that pair only shows up here.
        if (chosen.time <= System.currentTimeMillis()) {
            showMessage(R.string.deadline_past)
            revertToPresetIfUnset()
            return
        }
        viewModel.setCustomDeadline(chosen)
    }

    /** Backing out of the dialogs should not leave the custom chip selected but empty. */
    private fun revertToPresetIfUnset() {
        viewModel.pendingDateUtc = null
        if (viewModel.customDeadline.value == null) binding.deadline24h.isChecked = true
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
        val dir = File(requireContext().cacheDir, CreatePollViewModel.DIR_CAMERA)
            .apply { mkdirs() }
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

    private companion object {
        /** Tags let the open dialogs be found again after a rotation. */
        const val TAG_DATE = "deadline_date"
        const val TAG_TIME = "deadline_time"
    }
}
