package com.asdk.tools.wpstatussaver.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.asdk.tools.wpstatussaver.MainActivity
import com.asdk.tools.wpstatussaver.MediaViewActivity
import com.asdk.tools.wpstatussaver.R
import com.asdk.tools.wpstatussaver.adapter.StatusAdapter
import com.asdk.tools.wpstatussaver.databinding.FragmentStatusListBinding
import com.asdk.tools.wpstatussaver.model.AppType
import com.asdk.tools.wpstatussaver.model.SortOrder
import com.asdk.tools.wpstatussaver.model.StatusFilter
import com.asdk.tools.wpstatussaver.model.StatusMedia
import com.asdk.tools.wpstatussaver.util.SettingsManager
import com.asdk.tools.wpstatussaver.util.StorageHelper
import com.asdk.tools.wpstatussaver.util.WhatsAppLauncher
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class StatusListFragment : Fragment() {

    private var _binding: FragmentStatusListBinding? = null
    private val binding get() = _binding!!

    private lateinit var statusAdapter: StatusAdapter
    private var isVideoOnly: Boolean = false
    private var currentStatuses: List<StatusMedia> = emptyList()
    private var isFirstLoad: Boolean = true

    private val currentAppType: AppType
        get() = (activity as? MainActivity)?.selectedAppType ?: AppType.WHATSAPP

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.all { it }
        if (isGranted) {
            loadStatuses()
        } else {
            showPermissionDeniedUI()
        }
    }

    private val safLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
                    StorageHelper.saveSafTreeUri(requireContext(), currentAppType, uri)
                    loadStatuses()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Permission grant failed", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            showPermissionDeniedUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isVideoOnly = arguments?.getBoolean(ARG_IS_VIDEO) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        loadStatuses()
    }

    override fun onResume() {
        super.onResume()
        val columns = SettingsManager.getGridColumns(requireContext())
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = columns
        if (StorageHelper.isPermissionGranted(requireContext(), currentAppType)) {
            loadStatuses(showLoading = false)
        }
    }

    private fun setupRecyclerView() {
        statusAdapter = StatusAdapter(
            isSavedTab = false,
            onItemClick = { status, sharedView ->
                val pos = currentStatuses.indexOf(status).coerceAtLeast(0)
                val intent = Intent(requireContext(), MediaViewActivity::class.java).apply {
                    putExtra(MediaViewActivity.EXTRA_STATUS_MEDIA, status)
                    putExtra(MediaViewActivity.EXTRA_MEDIA_LIST, ArrayList(currentStatuses))
                    putExtra(MediaViewActivity.EXTRA_INITIAL_POSITION, pos)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    sharedView,
                    "transition_media"
                )
                startActivity(intent, options.toBundle())
            },
            onSaveClick = { status, position ->
                saveStatusItem(status, position)
            },
            onShareClick = { status ->
                WhatsAppLauncher.shareStatus(requireContext(), lifecycleScope, status)
            },
            onSelectionChanged = { count ->
                (activity as? MainActivity)?.updateSelectionBar(count, isSavedTab = false)
            }
        )

        val dragSelectListener = com.asdk.tools.wpstatussaver.util.DragSelectTouchListener(
            binding.recyclerView,
            { statusAdapter },
            { count -> (activity as? MainActivity)?.updateSelectionBar(count, isSavedTab = false) }
        )
        binding.recyclerView.addOnItemTouchListener(dragSelectListener)

        val pinchZoomListener = com.asdk.tools.wpstatussaver.util.GridPinchZoomGestureListener(
            binding.recyclerView,
            binding.swipeRefreshLayout
        ) { _ -> }
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

    fun getRecyclerView(): androidx.recyclerview.widget.RecyclerView = binding.recyclerView

    fun getCurrentItems(): List<StatusMedia> = currentStatuses

    private fun setupListeners() {
        val typedValue = android.util.TypedValue()
        val primaryColor = if (requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)) {
            typedValue.data
        } else {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.whatsapp_primary)
        }
        binding.swipeRefreshLayout.setColorSchemeColors(primaryColor)
        binding.swipeRefreshLayout.setOnRefreshListener {
            com.asdk.tools.wpstatussaver.util.HapticHelper.selection(binding.swipeRefreshLayout)
            loadStatuses(showLoading = false)
        }

        binding.btnGrantPermission.setOnClickListener {
            requestStoragePermission()
        }

        binding.btnOpenApp.setOnClickListener {
            WhatsAppLauncher.openApp(requireContext(), currentAppType)
        }
    }

    fun requestStoragePermission() {
        if (StorageHelper.isSafRequired()) {
            val intent = StorageHelper.createSafIntent(currentAppType)
            safLauncher.launch(intent)
        } else {
            permissionLauncher.launch(StorageHelper.getLegacyPermissions())
        }
    }

    private fun showPermissionDeniedUI() {
        binding.recyclerView.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.cardPermission.visibility = View.VISIBLE

        if (StorageHelper.isSafRequired()) {
            binding.tvPermissionDescription.text = getString(R.string.permission_saf_description)
        } else {
            binding.tvPermissionDescription.text = getString(R.string.storage_permission_msg)
        }
    }

    fun loadStatuses(showLoading: Boolean = true) {
        val context = context ?: return

        if (!StorageHelper.isPermissionGranted(context, currentAppType)) {
            showPermissionDeniedUI()
            binding.swipeRefreshLayout.isRefreshing = false
            if (StorageHelper.isSafRequired()) {
                val intent = StorageHelper.createSafIntent(currentAppType)
                safLauncher.launch(intent)
            } else {
                permissionLauncher.launch(StorageHelper.getLegacyPermissions())
            }
            return
        }

        binding.cardPermission.visibility = View.GONE

        if (showLoading && currentStatuses.isEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val allStatuses = StorageHelper.loadStatuses(context, currentAppType)
            val filteredByMedia = allStatuses.filter { it.isVideo == isVideoOnly }

            val filter = SettingsManager.getStatusFilter(context)
            val filtered = when (filter) {
                StatusFilter.UNSAVED_ONLY -> filteredByMedia.filter { !it.isSaved }
                StatusFilter.SAVED_ONLY -> filteredByMedia.filter { it.isSaved }
                else -> filteredByMedia
            }

            val sorted = when (SettingsManager.getSortOrder(context)) {
                SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.dateModified }
                else -> filtered.sortedByDescending { it.dateModified }
            }

            currentStatuses = sorted

            binding.progressBar.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false

            if (sorted.isEmpty()) {
                binding.recyclerView.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE

                val isInstalled = WhatsAppLauncher.isAppInstalled(context, currentAppType.packageName)
                if (!isInstalled) {
                    binding.tvEmptyTitle.text = getString(R.string.app_not_installed)
                    binding.tvEmptyDesc.text = "Install ${currentAppType.title} to view and save status updates."
                    binding.btnOpenApp.text = "Install ${currentAppType.title}"
                    binding.btnOpenApp.setOnClickListener {
                        WhatsAppLauncher.openPlayStore(requireContext(), currentAppType.packageName)
                    }
                } else {
                    binding.tvEmptyTitle.text = getString(R.string.no_statuses_title)
                    binding.tvEmptyDesc.text = getString(R.string.no_statuses_desc)
                    binding.btnOpenApp.text = if (currentAppType == AppType.WHATSAPP) {
                        getString(R.string.btn_open_whatsapp)
                    } else {
                        getString(R.string.btn_open_wa_business)
                    }
                    binding.btnOpenApp.setOnClickListener {
                        WhatsAppLauncher.openApp(requireContext(), currentAppType)
                    }
                }
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

    private fun saveStatusItem(status: StatusMedia, position: Int) {
        val context = context ?: return
        if (status.isSaved) {
            Toast.makeText(context, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
            return
        }

        StorageHelper.requestSaveWithLocationChoice(context, status) { location ->
            viewLifecycleOwner.lifecycleScope.launch {
                val success = StorageHelper.saveMedia(context, status, location)
                if (success) {
                    status.isSaved = true
                    statusAdapter.notifyItemChanged(position)
                    Toast.makeText(context, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IS_VIDEO = "arg_is_video"

        fun newInstance(isVideoOnly: Boolean): StatusListFragment {
            return StatusListFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_VIDEO, isVideoOnly)
                }
            }
        }
    }
}
