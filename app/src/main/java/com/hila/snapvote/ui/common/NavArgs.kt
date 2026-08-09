package com.hila.snapvote.ui.common

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment

/** Every screen that shows a poll is addressed by the same argument. */
const val ARG_POLL_ID = "pollId"

fun pollArgs(pollId: String): Bundle = bundleOf(ARG_POLL_ID to pollId)

fun Fragment.pollIdArgument(): String = requireArguments().getString(ARG_POLL_ID).orEmpty()
