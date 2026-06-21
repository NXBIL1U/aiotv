package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.LiveTvRepository
import com.itrepos.aiotv.domain.model.Channel
import javax.inject.Inject

/**
 * Cache-first channel list for Home/Search: reads from the Room-backed [LiveTvRepository]
 * (which refreshes from the network only when the cache is stale), so these screens no longer
 * re-download the full channel list on every cold start.
 */
class GetChannelsUseCase @Inject constructor(private val repo: LiveTvRepository) {
    suspend operator fun invoke(): List<Channel> = repo.getChannelsOnce()
}
