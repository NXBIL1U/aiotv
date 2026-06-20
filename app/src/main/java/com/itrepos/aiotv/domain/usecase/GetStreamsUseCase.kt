package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.StremioRepository
import com.itrepos.aiotv.domain.model.Stream
import javax.inject.Inject

class GetStreamsUseCase @Inject constructor(private val repo: StremioRepository) {
    suspend operator fun invoke(type: String, id: String): List<Stream> = repo.getStreams(type, id)
}
