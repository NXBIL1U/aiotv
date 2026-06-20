package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.IptvRepository
import com.itrepos.aiotv.domain.model.Channel
import javax.inject.Inject

class GetChannelsUseCase @Inject constructor(private val repo: IptvRepository) {
    suspend operator fun invoke(): List<Channel> = repo.getChannels()
}
