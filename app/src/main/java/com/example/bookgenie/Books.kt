package com.example.bookgenie

import java.io.Serializable

data class Books(
                 val authors: Int,
                 val author_name: String,
                 val average_rating: Double,
                 val book_id: Int,
                 val description: String,
                 val format: String,
                 val genres: List<String>,
                 val imageUrl: String,
                 val is_ebook:Boolean,
                 val isbn: String,
                 val isbn13:String,
                 val kindle_asin:String,
                 val language_code:String,
                 val num_pages:Int,
                 val publication_day:List<String>,
                 val publication_month:Int,
                 val publication_year:Int,
                 val publisher:String,
                 val title:String,
                 val rating_count:Int
) : Serializable{


}