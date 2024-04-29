package com.example.c001apk.ui.feed.reply.attopic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.constant.Constants
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.RecentAtUser
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.logic.repository.RecentAtUserRepo
import com.example.c001apk.ui.base.BaseViewModel
import com.example.c001apk.util.Event
import com.example.c001apk.util.PrefManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AtTopicViewModel @Inject constructor(
    private val recentAtUserRepo: RecentAtUserRepo,
    private val networkRepo: NetworkRepo
) : BaseViewModel() {

    val recentAtUsersData: LiveData<List<RecentAtUser>> = recentAtUserRepo.loadAllListLive()

    private val uid by lazy { PrefManager.uid }

    val followListData = MutableLiveData<List<RecentAtUser>>()
    val dataList = MutableLiveData<List<HomeFeedResponse.Data>>()
    val footerState = MutableLiveData<FooterState>()
    val toastText = MutableLiveData<Event<String?>>()

    fun getFollowList() {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getFollowList("/v6/user/followList", uid, page, lastItem)
                .onStart {
                    if (isLoadMore)
                        footerState.postValue(FooterState.Loading)
                }
                .collect { result ->
                    val data = result.getOrNull()
                    if (data != null) {
                        if (data.message != null) {
                            toastText.postValue(Event(data.message))
                            footerState.postValue(FooterState.LoadingError(data.message))
                        } else if (!data.data.isNullOrEmpty()) {
                            page++
                            lastItem = data.data.first().id
                            data.data.map { user ->
                                RecentAtUser(
                                    group = "follow",
                                    avatar = user.fUserInfo?.userAvatar ?: "",
                                    username = user.fUserInfo?.username ?: ""
                                )
                            }.let {
                                followListData.postValue((followListData.value ?: emptyList()) + it)
                            }
                            footerState.postValue(FooterState.LoadingDone)
                        } else if (data.data?.isEmpty() == true) {
                            isEnd = true
                            footerState.postValue(FooterState.LoadingEnd(Constants.LOADING_END))
                        }
                    } else {
                        toastText.postValue(Event("response is null"))
                        footerState.postValue(FooterState.LoadingError(Constants.LOADING_FAILED))
                    }
                    isRefreshing = false
                    isLoadMore = false
                }
        }
    }

    fun updateList(userList: List<RecentAtUser>) {
        viewModelScope.launch(Dispatchers.IO) {
            userList.forEach {
                if (recentAtUserRepo.checkUser(it.username)) {
                    recentAtUserRepo.updateUser(it.username, System.currentTimeMillis())
                } else {
                    recentAtUserRepo.insertUser(
                        RecentAtUser(
                            avatar = it.avatar,
                            username = it.username
                        )
                    )
                }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            recentAtUserRepo.deleteAll()
        }
    }

    override fun fetchData() {}
}