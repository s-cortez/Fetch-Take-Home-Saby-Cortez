package com.example.fetchtakehomesabycortez

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fetchtakehomesabycortez.ui.theme.FetchTakeHomeSabyCortezTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.min

// Data Class for Items. "name" string optional
data class Item(
    val name: String?,
    val listId: Int,
    val id: Int
)

// ItemAdapter for RecyclerView
class ItemAdapter(var items: List<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    init {
        Log.d("ItemAdapter", "Adapter initialized with ${items.size} items")
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    // ViewHolder for section headers
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sectionTitleTextView: TextView = itemView.findViewById(R.id.sectionTitleTextView)
    }

    // ViewHolder for items
    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val idTextView: TextView = itemView.findViewById(R.id.itemIdTextView)
        val listIdTextView: TextView = itemView.findViewById(R.id.listIdTextView)
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d("ItemAdapter", "Creating ViewHolder of type: $viewType")
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.section_header_layout, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_layout, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            TYPE_HEADER -> {
                val header = holder as HeaderViewHolder
                val sectionTitle = items[position] as String
                Log.d("ItemAdapter", "Binding header: $sectionTitle")
                header.sectionTitleTextView.text = sectionTitle
            }

            TYPE_ITEM -> {
                val item = holder as ItemViewHolder
                val currentItem = items[position] as Item
                Log.d(
                    "ItemAdapter",
                    "Binding item: ID: ${currentItem.id}, ListId: ${currentItem.listId}, Name: ${currentItem.name}"
                )
                item.idTextView.text =
                    holder.itemView.context.getString(R.string.item_id, currentItem.id)
                item.listIdTextView.text =
                    holder.itemView.context.getString(R.string.list_id, currentItem.listId)
                item.nameTextView.text = currentItem.name ?: "No Name"
            }
        }
    }

    override fun getItemCount(): Int {
        val count = items.size
        Log.d("ItemAdapter", "Item count: $count")
        return count
    }

    fun getItemAt(position: Int): Any = items[position]
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FetchTakeHomeSabyCortezTheme {
                MyApp()
            }
        }
    }

    @Composable
    fun MyApp() {
        val context = LocalContext.current
        val items = remember { mutableStateOf<List<Any>>(emptyList()) }
        val recyclerView = remember { mutableStateOf<RecyclerView?>(null) }

        LaunchedEffect(Unit) {
            val jsonString = fetchJsonFromUrl("https://fetch-hiring.s3.amazonaws.com/hiring.json")
            Log.d("MainActivity", "Fetched JSON: $jsonString")

            if (jsonString != null) {
                val gson = Gson()
                val itemType = object : TypeToken<List<Item>>() {}.type
                val itemList: List<Item>? = try {
                    gson.fromJson(jsonString, itemType)
                } catch (e: Exception) {
                    Log.e("JsonParsing", "Error parsing JSON", e)
                    null
                }

                if (itemList != null) {
                    val filteredItems = itemList
                        .filter { !it.name.isNullOrBlank() }
                        .sortedWith(compareBy<Item> { it.listId }.then(orderComparator()))

                    val groupedItems = filteredItems.groupBy { it.listId }
                    val itemsWithHeaders = mutableListOf<Any>()
                    groupedItems.entries.sortedBy { it.key }.forEach { (sectionId, items) ->
                        itemsWithHeaders.add("Section $sectionId")
                        itemsWithHeaders.addAll(items)
                    }

                    Log.d("MainActivity", "Filtered and grouped items: $itemsWithHeaders")
                    items.value = itemsWithHeaders
                } else {
                    Log.e("Data", "Failed to parse items")
                }
            } else {
                Log.e("Data", "Failed to fetch data")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    RecyclerView(context).apply {
                        layoutManager = LinearLayoutManager(context)
                        adapter = ItemAdapter(items.value)
                        recyclerView.value = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    val adapter = view.adapter as? ItemAdapter
                    if (adapter != null) {
                        Log.d("RecyclerView", "Updating adapter with ${items.value.size} items")
                        adapter.items = items.value
                        adapter.notifyDataSetChanged()
                    } else {
                        Log.e("RecyclerView", "Adapter is null")
                    }
                }
            )

            Button(
                onClick = {
                    jumpToNextGroup(recyclerView)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Text("Next Group")
            }
        }
    }

    private suspend fun fetchJsonFromUrl(url: String): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e("NetworkRequest", "Failed to fetch data: ${response.message}")
                    null
                }
            } catch (e: IOException) {
                Log.e("NetworkRequest", "Error fetching data", e)
                null
            }
        }
    }

    // Helper function to jump section
    private fun jumpToNextGroup(recyclerView: MutableState<RecyclerView?>) {
        val adapter = recyclerView.value?.adapter as? ItemAdapter ?: return

        val layoutManager = recyclerView.value!!.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()

        var currentSectionId = -1
        var headerPosition = -1
        for (position in 0 until adapter.itemCount) {
            val item = adapter.getItemAt(position)
            if (item is String && position <= firstVisiblePosition) {
                currentSectionId = getSectionId(item)
                headerPosition = position
            }
        }

        var nextSectionPosition = -1
        for (position in headerPosition + 1 until adapter.itemCount) {
            val item = adapter.getItemAt(position)
            if (item is String) {
                val sectionId = getSectionId(item)
                if (sectionId > currentSectionId) {
                    nextSectionPosition = position
                    break
                }
            }
        }

        if (nextSectionPosition != -1) {
            layoutManager.scrollToPositionWithOffset(nextSectionPosition, 0)
        }
    }

    // gets which groupId is currently on
    private fun getSectionId(header: String): Int {
        return header.replace("Section ", "").toIntOrNull() ?: -1
    }

    //order sorting
    private fun orderComparator(): Comparator<Item> {
        return Comparator { item1, item2 ->
            val name1 = item1.name ?: ""
            val name2 = item2.name ?: ""

            val p1 = name1.split(" ")
            val p2 = name2.split(" ")

            for (i in 0 until min(p1.size, p2.size)) {
                val cmp = when {
                    p1[i].isNumeric() && p2[i].isNumeric() -> p1[i].toInt().compareTo(p2[i].toInt())
                    else -> p1[i].compareTo(p2[i])
                }
                if (cmp != 0) return@Comparator cmp
            }
            p1.size.compareTo(p2.size)
        }
    }

    private fun String.isNumeric(): Boolean = this.matches(Regex("\\d+"))
}
