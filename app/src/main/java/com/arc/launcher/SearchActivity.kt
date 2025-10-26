package com.arc.launcher

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView

class SearchActivity : Activity() {
    private lateinit var searchBar: EditText
    private lateinit var searchResults: ListView
    private lateinit var appAdapter: ArrayAdapter<String>
    private val allApps = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        searchBar = findViewById(R.id.search_bar)
        searchResults = findViewById(R.id.search_results)

        loadApps()

        appAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, allApps)
        searchResults.adapter = appAdapter

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchTerm = s.toString().lowercase()
                val filteredApps = allApps.filter { it.lowercase().contains(searchTerm) }
                appAdapter.clear()
                appAdapter.addAll(filteredApps)
                appAdapter.notifyDataSetChanged()

                // TODO: Search for contacts, app shortcuts, and settings
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadApps() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        for (app in apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                allApps.add(app.loadLabel(pm).toString())
            }
        }
        allApps.sort()
    }
}
