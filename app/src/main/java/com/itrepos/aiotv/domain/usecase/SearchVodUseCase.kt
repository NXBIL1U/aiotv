package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.SearchRepository
import com.itrepos.aiotv.domain.model.MediaItem
import javax.inject.Inject

class SearchVodUseCase @Inject constructor(private val repo: SearchRepository) {
    suspend operator fun invoke(query: String): List<MediaItem> = repo.search(query)
}
