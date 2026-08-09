package com.hila.snapvote.ui.common

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * A [ListAdapter] whose rows also depend on something other than the list itself –
 * the poll a row belongs to, which images finished loading, who already voted.
 *
 * Those extras arrive from LiveData, so they can land at any moment, including while
 * the list is measuring itself. Calling `notifyDataSetChanged()` right then throws
 * "Cannot call this method while RecyclerView is computing a layout or scrolling",
 * so [refreshRows] waits for the next frame when that is the case.
 */
abstract class RefreshableListAdapter<T : Any, VH : RecyclerView.ViewHolder>(
    diffCallback: DiffUtil.ItemCallback<T>,
) : ListAdapter<T, VH>(diffCallback) {

    private var attachedList: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedList = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedList = null
    }

    @SuppressLint("NotifyDataSetChanged")
    protected fun refreshRows() {
        val list = attachedList
        if (list != null && (list.isComputingLayout || list.scrollState != RecyclerView.SCROLL_STATE_IDLE)) {
            list.post { notifyDataSetChanged() }
        } else {
            notifyDataSetChanged()
        }
    }
}
