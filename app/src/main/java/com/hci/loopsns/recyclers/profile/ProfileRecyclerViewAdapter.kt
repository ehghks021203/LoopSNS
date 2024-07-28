package com.hci.loopsns.recyclers.profile

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.hci.loopsns.R
import com.hci.loopsns.network.ArticleDetail
import com.hci.loopsns.recyclers.detail.ArticleRecyclerViewAdapter.ArticleHolder
import com.hci.loopsns.utils.formatTo
import com.hci.loopsns.utils.toDate
import com.hci.loopsns.view.fragment.profile.BaseProfileFragment

class ProfileRecyclerViewAdapter(private val activity: BaseProfileFragment, private var items: ArrayList<ArticleDetail>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var loadMoreButton: ImageView? = null

    class ArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contents: TextView = itemView.findViewById(R.id.contents)
        val time: TextView = itemView.findViewById(R.id.time)
        val picture: ImageView = itemView.findViewById(R.id.picture)

        val category1: TextView = itemView.findViewById(R.id.tag_1_article)
        val category2: TextView = itemView.findViewById(R.id.tag_2_article)
        val keywords1: TextView = itemView.findViewById(R.id.keyword_1_article)
        val keywords2: TextView = itemView.findViewById(R.id.keyword_2_article)
        val keywords3: TextView = itemView.findViewById(R.id.keyword_3_article)
        val keywords4: TextView = itemView.findViewById(R.id.keyword_4_article)
    }

    class MoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val loadMoreButton: ImageButton = itemView.findViewById(R.id.load_more_Button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view: View
        val context = parent.context
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        return when (viewType) {
            ViewType.ARTICLE -> {
                view = inflater.inflate(R.layout.child_fragment_profile_article_item, parent, false)
                ArticleViewHolder(view)
            }
            ViewType.ADD_MORE_BUTTON -> {
                view = inflater.inflate(R.layout.child_fragment_profile_article_more_load, parent, false)
                MoreViewHolder(view)
            }
            else -> { //Never Happend
                view = inflater.inflate(R.layout.child_fragment_profile_article_more_load, parent, false)
                MoreViewHolder(view)
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        if(position < items.size) {
            return ViewType.ARTICLE
        }
        return ViewType.ADD_MORE_BUTTON
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder) {
            is ArticleViewHolder -> {
                val item = items[position]

                holder.contents.text = item.contents
                holder.time.text =  item.time.toDate().formatTo("yyyy-MM-dd HH:mm")

                holder.category1.text = item.cat1
                holder.category2.text= item.cat2

                val keywords = listOf(
                    holder.keywords1,
                    holder.keywords2,
                    holder.keywords3,
                    holder.keywords4
                )

                for (i in 0 until item.keywords.size) {
                    if (i < item.keywords.size) {
                        if(item.keywords[i].isNotBlank()) {
                            keywords[i].visibility = View.VISIBLE
                            keywords[i].text = item.keywords[i]
                        }
                    }
                }

                if (item.images.isNotEmpty()) {
                    holder.picture.visibility = View.VISIBLE
                    Glide.with(activity)
                        .load(item.images[0])
                        .thumbnail(Glide.with(activity).load(R.drawable.picture_placeholder))
                        .transform(CenterCrop(), RoundedCorners(30))
                        //.apply(RequestOptions.bitmapTransform(RoundedCorners(150)))
                        .into(holder.picture)

                } else {
                    holder.picture.visibility = View.GONE
                }
            }
            is MoreViewHolder -> {
                loadMoreButton = holder.loadMoreButton
                loadMoreButton?.setOnClickListener {
                    loadMoreButton?.setImageDrawable(null)
                    loadMoreButton?.isClickable = false
                    activity.requestMoreArticle()
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        if(holder !is MoreViewHolder) {
            return
        }

        loadMoreButton = null
    }

    fun getLastArticle(): String {
        if(items.size == 0) {
            return ""
        }
        return items[items.size - 1].uid
    }

    fun deleteArticle(uid: String) {
        for (i in 0..<items.size) {
            val item = items[i]

            if(item.uid != uid) {
                continue
            }

            items.removeAt(i)
            this.notifyItemRemoved(i)
        }
    }

    fun createArticle(article: ArticleDetail) {
        items.add(0, article)
        this.notifyItemInserted(0)
    }

    fun insertArticle(articles: List<ArticleDetail>) {
        articles.forEach {
            Log.e("article", it.contents)
        }
        if(articles.isEmpty()) return

        items.addAll(articles)
        this.notifyItemRangeInserted(items.size - articles.size, articles.size)

        if(articles.size < 3) {
            loadMoreButton?.setImageDrawable(null)
            loadMoreButton?.isClickable = false
            return
        }
        loadMoreButton?.setImageResource(R.drawable.add_circle_48px)
        loadMoreButton?.isClickable = true
    }
}