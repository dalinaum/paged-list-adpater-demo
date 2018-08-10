package io.github.dalinaum.paging

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PageKeyedDataSource
import android.arch.paging.PagedList
import android.arch.paging.PagedListAdapter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_recyclerview.view.*
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


class MainActivity : AppCompatActivity() {
    private val pokeAPI: PokeAPI by lazy {
        val retrofit = Retrofit.Builder()
                .baseUrl("https://pokeapi.co/api/v2/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        retrofit.create<PokeAPI>(PokeAPI::class.java)
    }

    private val adapter by lazy(::Adapter)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recyclerView.apply {
            adapter = this@MainActivity.adapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        createLiveData().observe(this, Observer(adapter::submitList))
    }

    private fun createLiveData(): LiveData<PagedList<Result>> {
        val config = PagedList.Config.Builder()
                .setInitialLoadSizeHint(20)
                .setPageSize(20)
                .setPrefetchDistance(10)
                .build()

        return LivePagedListBuilder(object : android.arch.paging.DataSource.Factory<String, Result>() {
            override fun create(): android.arch.paging.DataSource<String, Result> {
                return MainActivity.DataSource(pokeAPI)
            }
        }, config).build()
    }

    private class DataSource(private val pokeAPI: PokeAPI) : PageKeyedDataSource<String, Result>() {

        override fun loadInitial(params: LoadInitialParams<String>, callback: LoadInitialCallback<String, Result>) {
            val body = pokeAPI.listPokemons().execute().body()
            callback.onResult(body!!.results, body.previous, body.next)
        }

        override fun loadBefore(params: LoadParams<String>, callback: LoadCallback<String, Result>) {
            val map = handleKey(params.key)
            val body = pokeAPI.listPokemons(map["offset"]!!, map["limit"]!!).execute().body()
            callback.onResult(body!!.results, body.previous)
        }

        override fun loadAfter(params: LoadParams<String>, callback: LoadCallback<String, Result>) {
            val map = handleKey(params.key)
            val body = pokeAPI.listPokemons(map["offset"]!!, map["limit"]!!).execute().body()
            callback.onResult(body!!.results, body.next)
        }

        private fun handleKey(key: String): MutableMap<String, String> {
            val (_, queryPart) = key.split("?")
            val queries = queryPart.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val map = mutableMapOf<String, String>()
            for (query in queries) {
                val (k, v) = query.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                map[k] = v
            }
            return map
        }
    }

    private class DiffItemCallback : DiffUtil.ItemCallback<Result>() {
        override fun areItemsTheSame(oldItem: Result, newItem: Result): Boolean =
                oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: Result, newItem: Result): Boolean =
                oldItem.name == newItem.name && oldItem.url == newItem.url
    }

    private class Adapter : PagedListAdapter<Result, VieHolder>(DiffItemCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VieHolder =
                VieHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_recyclerview, parent, false))

        override fun onBindViewHolder(holder: VieHolder, position: Int) {
            getItem(position)?.let { (_, name) ->
                holder.title = name
            }
        }
    }

    private class VieHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var title: String
            get() =
                itemView.title.text.toString()
            set(title) {
                itemView.title.text = title
            }
    }
}

interface PokeAPI {
    @GET("pokemon/")
    fun listPokemons(): Call<Response>

    @GET("pokemon/")
    fun listPokemons(
            @Query("offset") offset: String,
            @Query("limit") limit: String
    ): Call<Response>
}

data class Response(
        val count: Int,
        val previous: String,
        val next: String,
        val results: List<Result>
)

data class Result(
        val url: String,
        val name: String
)
