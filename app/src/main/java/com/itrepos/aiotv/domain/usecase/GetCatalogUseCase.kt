package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.StremioRepository
import com.itrepos.aiotv.domain.model.ContentSection
import com.itrepos.aiotv.domain.model.MediaItem
import javax.inject.Inject

class GetCatalogUseCase @Inject constructor(private val repo: StremioRepository) {
    suspend operator fun invoke(type: String = "movie"): List<MediaItem> = repo.getCatalog(type)
    suspend fun sections(type: String): List<ContentSection> = repo.getCatalogSections(type)
    suspend fun search(query: String): List<MediaItem> = repo.search(query)
}
