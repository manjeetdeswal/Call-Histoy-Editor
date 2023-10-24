package com.thenotesgiver.callhistoryeditorandbackup

import androidx.core.text.isDigitsOnly
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.map

class HistoryViewModel : ViewModel() {


    private val list = MutableLiveData<List<HistoryModel>>()
    private val query = MutableLiveData<String>()

    // Use the switchMap extension function on LiveData
    val filteredItemList: LiveData<List<HistoryModel>> = liveData {
        emitSource(query.map { searchQuery ->
            val currentList = list.value ?: emptyList()
            if (searchQuery.isNullOrBlank()) {
                currentList
            }
            if (searchQuery.toString().isDigitsOnly()) {

                currentList.filter { it.number?.contains(searchQuery, true) ?:true }

            } else {

                currentList.filter { it.name?.contains(searchQuery, true) ?: true }

            }


        })
    }


    fun getList(): LiveData<List<HistoryModel>> {
        return list
    }


    fun addItem(item: HistoryModel) {
        // Get the current list, or create a new one if it's null
        val currentList = list.value ?: emptyList()

        // Create a new list with the added item
        val newList = currentList + item

        // Update the MutableLiveData with the new list
        list.value = newList
    }

    fun removeItem(item: HistoryModel) {
        // Get the current list, or return if it's null
        val currentList = list.value ?: return

        // Create a new list without the specified item
        val newList = currentList.filter { it != item }

        // Update the MutableLiveData with the new list
        list.value = newList
    }


    fun setQuery(searchQuery: String) {
        query.value = searchQuery
    }


}