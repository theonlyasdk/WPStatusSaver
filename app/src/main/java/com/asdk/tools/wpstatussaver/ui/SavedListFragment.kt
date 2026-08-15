package com.asdk.tools.wpstatussaver.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.asdk.tools.wpstatussaver.MainActivity
import com.asdk.tools.wpstatussaver.MediaViewActivity
import com.asdk.tools.wpstatussaver.R
import com.asdk.tools.wpstatussaver.adapter.StatusAdapter
import com.asdk.tools.wpstatussaver.databinding.FragmentSavedListBinding
import com.asdk.tools.wpstatussaver.model.SortOrder
import com.asdk.tools.wpstatussaver.model.StatusMedia
import com.asdk.tools.wpstatussaver.util.SettingsManager
import com.asdk.tools.wpstatussaver.util.StorageHelper
import com.asdk.tools.wpstatussaver.util.WhatsAppLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SavedListFragment : Fragment() {

    private var _binding: FragmentSavedListBinding? = null
    private val binding get() = _binding!!

    private lateinit var statusAdapter: StatusAdapter
    private var savedStatuses: List<StatusMedia> = emptyList()
    private var isFirstLoad: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        loadSavedList()
    }

    override fun onResume() {
        super.onResume()
        val columns = SettingsManager.getGridColumns(requireContext())
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = columns
        loadSavedList(showLoading = false)
    }

    private fun setupRecyclerView() {
        statusAdapter = StatusAdapter(
            isSavedTab = true,
            onItemClick = { status, sharedView ->
                val pos = savedStatuses.indexOf(status).coerceAtLeast(0)
                val intent = Intent(requireContext(), MediaViewActivity::class.java).apply {
                    putExtra(MediaViewActivity.EXTRA_STATUS_MEDIA, status)
                    putExtra(MediaViewActivity.EXTRA_MEDIA_LIST, ArrayList(savedStatuses))
                    putExtra(MediaViewActivity.EXTRA_INITIAL_POSITION, pos)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    sharedView,
                    "transition_media"
                )
                startActivity(intent, options.toBundle())
            },
            onSaveClick = { _, _ -> },
            onShareClick = { status ->
                WhatsAppLauncher.shareStatus(requireContext(), lifecycleScope, status)
            },
            onDeleteClick = { status, _ ->
                showDeleteConfirmationDialog(status)
            },
            onSelectionChanged = { count ->
                (activity as? MainActivity)?.updateSelectionBar(count, isSavedTab = true)
            }
        )

        val dragSelectListener = com.asdk.tools.wpstatussaver.util.DragSelectTouchListener(
            binding.recyclerView,
            { statusAdapter },
            { count -> (activity as? MainActivity)?.updateSelectionBar(count, isSavedTab = true) }
        )
        binding.recyclerView.addOnItemTouchListener(dragSelectListener)

        val pinchZoomListener = com.asdk.tools.wpstatussaver.util.GridPinchZoomGestureListener(
            binding.recyclerView
        ) { _ ->
            statusAdapter.notifyItemRangeChanged(0, statusAdapter.itemCount)
        }
        binding.recyclerView.addOnItemTouchListener(pinchZoomListener)

        statusAdapter.onItemLongClick = { _, position ->
            dragSelectListener.startDragSelection(position)
        }

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), SettingsManager.getGridColumns(requireContext()))
            adapter = statusAdapter
            setHasFixedSize(true)
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        }
    }

    fun getAdapter(): StatusAdapter = statusAdapter

    fun getCurrentItems(): List<StatusMedia> = savedStatuses

    private fun setupListeners() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.whatsapp_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadSavedList(showLoading = false)
        }
    }

    fun loadSavedList(showLoading: Boolean = true) {
        val context = context ?: return

        if (showLoading && savedStatuses.isEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val list = StorageHelper.loadSavedMedia(context)

            val sorted = when (SettingsManager.getSortOrder(context)) {
                SortOrder.OLDEST_FIRST -> list.sortedBy { it.dateModified }
                else -> list.sortedByDescending { it.dateModified }
            }

            savedStatuses = sorted

            binding.progressBar.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false

            if (sorted.isEmpty()) {
                binding.recyclerView.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                val shouldAnimate = isFirstLoad
                isFirstLoad = false
                statusAdapter.submitList(sorted) {
                    if (shouldAnimate) {
                        binding.recyclerView.scheduleLayoutAnimation()
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(status: StatusMedia) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteStatusItem(status)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteStatusItem(status: StatusMedia) {
        val context = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val success = StorageHelper.deleteSavedMedia(context, status)
            if (success) {
                Toast.makeText(context, getString(R.string.deleted_success), Toast.LENGTH_SHORT).show()
                loadSavedList(showLoading = false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SavedListFragment {
            return SavedListFragment()
        }
    }
}
