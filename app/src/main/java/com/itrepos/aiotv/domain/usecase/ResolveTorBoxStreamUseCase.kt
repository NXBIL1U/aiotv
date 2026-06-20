package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.TorBoxRepository
import com.itrepos.aiotv.domain.model.Stream
import javax.inject.Inject

class ResolveTorBoxStreamUseCase @Inject constructor(private val repo: TorBoxRepository) {

    suspend operator fun invoke(stream: Stream): String? {
        val infoHash = stream.infoHash ?: return stream.url
        val cached = repo.checkCached(listOf(infoHash))
        return if (cached[infoHash] == true) {
            null // caller will createTorrent and requestdl immediately
        } else {
            val torrentId = repo.createTorrent(buildMagnet(infoHash)) ?: return null
            val info = repo.pollUntilReady(torrentId) ?: return null
            val fileId = pickFile(info.files.map { it.id to (it.name ?: "") }, stream.fileIdx)
            repo.getDownloadUrl(torrentId, fileId)
        }
    }

    suspend fun resolveWithCache(stream: Stream): String? {
        val infoHash = stream.infoHash ?: return stream.url
        val torrentId = repo.createTorrent(buildMagnet(infoHash)) ?: return null
        val info = repo.pollUntilReady(torrentId) ?: return null
        val fileId = pickFile(info.files.map { it.id to (it.name ?: "") }, stream.fileIdx)
        return repo.getDownloadUrl(torrentId, fileId)
    }

    private fun buildMagnet(infoHash: String) = "magnet:?xt=urn:btih:$infoHash"

    private fun pickFile(files: List<Pair<Int, String>>, fileIdx: Int?): Int {
        if (fileIdx != null && fileIdx < files.size) return files[fileIdx].first
        val video = files.firstOrNull { (_, name) ->
            name.endsWith(".mkv") || name.endsWith(".mp4") || name.endsWith(".avi")
        }
        return video?.first ?: files.firstOrNull()?.first ?: 0
    }
}
