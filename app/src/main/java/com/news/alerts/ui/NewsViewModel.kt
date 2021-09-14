package com.news.alerts.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.*
import android.net.NetworkCapabilities.*
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.news.alerts.NewsApplication
import com.news.alerts.models.Article
import com.news.alerts.models.NewsResponse
import com.news.alerts.repository.NewsRepository
import com.news.alerts.util.Resource
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException

class NewsViewModel(
    app:Application,
    val newsRepository: NewsRepository
) : AndroidViewModel(app) {

    val breakingNews: MutableLiveData<Resource<NewsResponse>> = MutableLiveData()
    var breakingNewsPage = 1
    var breakingNewsResponse:NewsResponse? =null

    val searchNews: MutableLiveData<Resource<NewsResponse>> = MutableLiveData()
    var searchNewsPage = 1
    var searchNewsResponse:NewsResponse? =null

    init {
        getBreakingNews("us")
    }

    fun getBreakingNews(countryCode: String) = viewModelScope.launch {
        breakingNews.postValue(Resource.Loading())
        safeBreakingNewsCall(countryCode)

    }

    fun searchNews(searchQuery: String) = viewModelScope.launch {
        searchNews.postValue(Resource.Loading())
      safeSearchNewsCall(searchQuery)
    }

    private fun handleBreakingNewsResponse(response: Response<NewsResponse>) : Resource<NewsResponse> {
        if(response.isSuccessful) {
            response.body()?.let { resultResponse ->
                /*increase page number +1 everytime we get response*/
                breakingNewsPage++
                /*if it's the first page*/
                if(breakingNewsResponse == null){
                    breakingNewsResponse = resultResponse
                }
                else{
                    val oldArticles = breakingNewsResponse?.articles
                    val newArticles = resultResponse.articles
                    oldArticles?.addAll(newArticles)

                }

                return Resource.Success(breakingNewsResponse ?: resultResponse)
            }
        }
        return Resource.Error(response.message())
    }

    private fun handleSearchNewsResponse(response: Response<NewsResponse>) : Resource<NewsResponse> {
        if(response.isSuccessful) {
            response.body()?.let { resultResponse ->
                /*increase page number +1 everytime we get response*/
                searchNewsPage++
                /*if it's the first page*/
                if(searchNewsResponse == null){
                    searchNewsResponse = resultResponse
                }
                else{
                    val oldArticles = searchNewsResponse?.articles
                    val newArticles = resultResponse.articles
                    oldArticles?.addAll(newArticles)

                }

                return Resource.Success(searchNewsResponse ?: resultResponse)
            }
        }
        return Resource.Error(response.message())
    }

    fun saveArticle(article: Article) = viewModelScope.launch {
        newsRepository.upsert(article)
    }

    fun getSavedNews() = newsRepository.getSavedNews()

    fun deleteArticle(article: Article) = viewModelScope.launch {
        newsRepository.deleteArticle(article)
    }

    private suspend fun safeBreakingNewsCall(countryCode: String){
        breakingNews.postValue(Resource.Loading())
        try{
            if(hasInternetConnection()){
                val response = newsRepository.getBreakingNews(countryCode, breakingNewsPage)
                breakingNews.postValue(handleBreakingNewsResponse(response))
            }else{
                breakingNews.postValue(Resource.Error("No internet connection"))
            }
        }catch (t:Throwable){
            when(t){
                is IOException -> breakingNews.postValue(Resource.Error("Network Failure"))
                else -> breakingNews.postValue(Resource.Error("Conversion Error"))
            }
        }
    }

    private suspend fun safeSearchNewsCall(searchQuery: String){
        searchNews.postValue(Resource.Loading())
        try{
            if(hasInternetConnection()){
                val response = newsRepository.getBreakingNews(searchQuery, searchNewsPage)
                searchNews.postValue(handleSearchNewsResponse(response))
            }else{
                searchNews.postValue(Resource.Error("No internet connection"))
            }
        }catch (t:Throwable){
            when(t){
                is IOException -> searchNews.postValue(Resource.Error("Network Failure"))
                else -> searchNews.postValue(Resource.Error("Conversion Error"))
            }
        }
    }



    private fun hasInternetConnection(): Boolean{
        val connectivitymanager = getApplication<NewsApplication>().getSystemService(
            Context.CONNECTIVITY_SERVICE
        )as ConnectivityManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){  //isConnect was deprecated from api level 23
            val activeNetwork = connectivitymanager.activeNetwork ?: return false
            val capabilities = connectivitymanager.getNetworkCapabilities(activeNetwork) ?: return false
            return when {
                capabilities.hasTransport(TRANSPORT_WIFI) -> true
                capabilities.hasTransport(TRANSPORT_CELLULAR) -> true
                capabilities.hasTransport(TRANSPORT_ETHERNET) -> true

                else -> false

            }

        }
        else{
            connectivitymanager.activeNetworkInfo?.run {
                return when(type){
                    TYPE_WIFI->true
                    TYPE_MOBILE  -> true
                    TYPE_ETHERNET -> true
                    else ->false
                }

            }
        }
        return false
    }
}