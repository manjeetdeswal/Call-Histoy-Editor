package com.thenotesgiver.callhistoryeditorandbackup

import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.CallLog
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.thenotesgiver.callhistoryeditorandbackup.databinding.ActivityCallHistoryBinding
import java.text.SimpleDateFormat
import java.util.Calendar

class CallHistory : AppCompatActivity() {

    private lateinit var binding: ActivityCallHistoryBinding
    private val CALLLOG_REQUEST_CODE = 201
    private lateinit var cursor: Cursor
    private lateinit var historyAdapter: HistoryAdapter

    private var mInterstitialAd: InterstitialAd? = null
     lateinit var myViewModel: HistoryViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]


        MobileAds.initialize(
            this
        ) { }
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        val adView = AdView(this)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = getString(R.string.bannerEmail)
        MobileAds.initialize(
            this
        ) { }


        val mAdView: AdView = findViewById(R.id.adView)
        mAdView.loadAd(adRequest)



        InterstitialAd.load(
            this,
            this.getString(R.string.intMain),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    // The mInterstitialAd reference will be null until
                    // an ad is loaded.
                    mInterstitialAd = interstitialAd
                    //   Log.i(TAG, "onAdLoaded");
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error
                    //    Log.i(TAG, loadAdError.getMessage());
                    mInterstitialAd = null
                }
            })

        mInterstitialAd?.fullScreenContentCallback  = object : FullScreenContentCallback(){
            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
               mInterstitialAd  = null
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time.
                mInterstitialAd = null
            }
        }


        binding.callLogRecycle.setHasFixedSize(true)
        binding.callLogRecycle.layoutManager = LinearLayoutManager(this@CallHistory,
            LinearLayoutManager.VERTICAL,false)



        historyAdapter = HistoryAdapter(this@CallHistory)
        binding.callLogRecycle.adapter = historyAdapter
        permissionInit()

        val itemsObserver = Observer<List<HistoryModel>> { items ->

            historyAdapter.submitList(items)
        }
        myViewModel.getList().observe(this, itemsObserver)


        myViewModel.filteredItemList.observe(this, itemsObserver)

        historyAdapter.setOnItemClickListener {

             val dialog = EditDialog(this,it,myViewModel)
             dialog.show()
            dialog.setOnDismissListener {

                finish();
                startActivity(intent);
                overridePendingTransition(0, 0);
            }


        }


    }


    override fun onBackPressed() {
        super.onBackPressed()
        if (mInterstitialAd != null) {
            mInterstitialAd!!.show(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        val menuItem = menu.findItem(R.id.app_bar_search)
        val searchView = menuItem.actionView as android.widget.SearchView

        searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {

                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {

                myViewModel.setQuery(newText)
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }



    private fun permissionInit() {
        if (ContextCompat.checkSelfPermission(this@CallHistory, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this@CallHistory, arrayOf(android.Manifest.permission.READ_CALL_LOG), CALLLOG_REQUEST_CODE
            )
        else {

            loadAllCallLogInfo()

        }
    }

    private fun loadAllCallLogInfo() {
        val contentResolver = contentResolver
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(CallLog.Calls.CACHED_NAME,CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.TYPE,CallLog.Calls._ID)
        val selection = null
        val args = null
        val sortedOrder = CallLog.Calls.DATE + " DESC"



        cursor = contentResolver.query(uri.buildUpon().appendQueryParameter("limit","1000").build(),projection,selection,args,sortedOrder)!!
        if (cursor.count>0 && cursor!= null) {

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

                val formattedDuration = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss aaa")
                val dateTime = simpleDateFormat.format(calendar.time)

                    val callLogModel = HistoryModel(name, number, dateTime, formattedDuration, type,id)
                    myViewModel.addItem(callLogModel)





            }


        } else {
            Toast.makeText(this@CallHistory,"No Call Log Found on this Device",Toast.LENGTH_SHORT).show()
        }



    }




    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CALLLOG_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadAllCallLogInfo()
                } else {
                    Toast.makeText(this@CallHistory,"No Permission Granted For Call Log", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


}