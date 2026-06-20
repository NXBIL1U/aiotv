package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.StremioRepository
import com.itrepos.aiotv.domain.model.MediaItem
import javax.inject.Inject

class GetCatalogUseCase @Inject constructor(private val repo: StremioRepository) {
    suspend operator fun invoke(type: String = "movie"): List<MediaItem> = repo.getCatalog(type)
}
