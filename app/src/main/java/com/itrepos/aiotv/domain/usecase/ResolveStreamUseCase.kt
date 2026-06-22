package com.itrepos.aiotv.domain.usecase

import com.itrepos.aiotv.data.repository.TorBoxRepository
import com.itrepos.aiotv.domain.model.Stream
import javax.inject.Inject

/**
 * Resolves a [Stream] to a directly-playable URL.
 *
 * If the stream already carries a `url` (HTTP addon / debrid direct link) it is returned as-is.
 * Otherwise we treat it as a torrent: create it on TorBox, poll until ready, and hand back the
 * download URL of the first playable file. Failures surface as a [Result.failure] so callers can
 * auto-advance to the next ranked candidate.
 */
class ResolveStreamUseCase @Inject constructor(
    private val torBox: TorBoxRepository,
) {
    suspend operator fun invoke(stream: Stream): Result<String> = try {
        Result.success(
            stream.url ?: run {
                val hash = stream.infoHash ?: error("Stream has no URL or info hash")
                val torrentId = torBox.createTorrent("magnet:?xt=urn:btih:$hash")
                    ?: error("Failed to create torrent")
                val info = torBox.pollUntilReady(torrentId)
                    ?: error("Torrent did not become ready")
                val fileId = info.files.firstOrNull()?.id
                    ?: error("Torrent has no playable files")
                torBox.getDownloadUrl(torrentId, fileId)
            }
        )
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (e: Exception) {
        Result.failure(e)
    }
}
