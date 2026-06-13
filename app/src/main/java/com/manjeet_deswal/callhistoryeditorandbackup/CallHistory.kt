package com.manjeet_deswal.callhistoryeditorandbackup

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager

import com.manjeet_deswal.callhistoryeditorandbackup.databinding.ActivityCallHistoryBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CallHistory : AppCompatActivity() {

    private lateinit var binding: ActivityCallHistoryBinding
    private val callLogRequestCode = 201
    private lateinit var cursor: Cursor
    private lateinit var historyAdapter: HistoryAdapter
    lateinit var myViewModel: HistoryViewModel


    private var actionMode: ActionMode? = null

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_delete, menu)
            binding.addCallLogFab.hide()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    deleteSelectedItems()
                    mode.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            historyAdapter.clearSelection()


            binding.addCallLogFab.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        binding.callLogRecycle.setHasFixedSize(true)
        binding.callLogRecycle.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL, false
        )

        historyAdapter = HistoryAdapter(this)
        binding.callLogRecycle.adapter = historyAdapter
        permissionInit()

        val itemsObserver = Observer<List<HistoryModel>> { items ->
            historyAdapter.submitList(items)
        }

        myViewModel.getList().observe(this, itemsObserver)
        myViewModel.filteredItemList.observe(this, itemsObserver)

        historyAdapter.onSelectionModeChange = { isSelectionMode, count ->
            if (isSelectionMode) {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                }
                actionMode?.title = "$count Selected"
            } else {
                actionMode?.finish()
            }
        }

        historyAdapter.setOnItemClickListener {
            val dialog = EditDialog(this, it, myViewModel)
            dialog.show()
            dialog.setOnDismissListener {
                restartActivitySmoothly()
            }
        }

        binding.callLogRecycle.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            private val hideDateBubbleRunnable = Runnable {
                binding.fastScrollDateCard.visibility = android.view.View.GONE
            }

            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy != 0) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val position = layoutManager.findFirstVisibleItemPosition()

                    if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        val item = historyAdapter.currentList.getOrNull(position)
                        if (item != null && !item.dateTime.isNullOrEmpty()) {
                            val dateOnly = item.dateTime.substringBefore(" ")
                            binding.fastScrollDateText.text = dateOnly
                            binding.fastScrollDateCard.visibility = android.view.View.VISIBLE
                            binding.fastScrollDateCard.removeCallbacks(hideDateBubbleRunnable)
                            binding.fastScrollDateCard.postDelayed(hideDateBubbleRunnable, 1000)
                        }
                    }
                }
            }
        })

        binding.addCallLogFab.setOnClickListener {
            val newModel = HistoryModel(
                name = "", number = "", dateTime = "", duration = "", type = "1", id = ""
            )
            val dialog = EditDialog(this, newModel, myViewModel)
            dialog.show()
            dialog.setOnDismissListener {
                restartActivitySmoothly()
            }
        }
    }


    private fun deleteSelectedItems() {
        val idsToDelete = historyAdapter.selectedItemIds.toList()


        idsToDelete.forEach { id ->
            try {
                contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(id))
            } catch (e: SecurityException) {
                Log.e("CallHistory", "Permission denied deleting ID $id", e)
            }
        }

        Toast.makeText(this, "${idsToDelete.size} items deleted", Toast.LENGTH_SHORT).show()


        restartActivitySmoothly()
    }

    private fun restartActivitySmoothly() {
        finish()
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        val menuItem = menu.findItem(R.id.app_bar_search)
        val searchView = menuItem.actionView as android.widget.SearchView

        searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean = true
            override fun onQueryTextChange(newText: String): Boolean {
                myViewModel.setQuery(newText)
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    private fun permissionInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALL_LOG), callLogRequestCode)
        } else {
            loadAllCallLogInfo()
        }
    }

    private fun loadAllCallLogInfo() {
        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE,
            CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls._ID
        )
        val sortedOrder = CallLog.Calls.DATE + " DESC"

        cursor = contentResolver.query(
            CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter("limit", "1000").build(),
            projection, null, null, sortedOrder
        )!!

        if (cursor.count > 0) {
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val type = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val id = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))

                val calendar = Calendar.getInstance()
                calendar.timeInMillis = date

                val hours = (duration / 3600)
                val minutes = (duration % 3600) / 60
                val seconds = (duration % 60)

                val formattedDuration = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss aaa", Locale.getDefault())
                val dateTime = simpleDateFormat.format(calendar.time)

                val callLogModel = HistoryModel(name, number, dateTime, formattedDuration, type, id)
                myViewModel.addItem(callLogModel)
            }
        } else {
            Toast.makeText(this, "No Call Log Found on this Device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == callLogRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAllCallLogInfo()
            } else {
                Toast.makeText(this, "No Permission Granted For Call Log", Toast.LENGTH_SHORT).show()
            }
        }
    }
}