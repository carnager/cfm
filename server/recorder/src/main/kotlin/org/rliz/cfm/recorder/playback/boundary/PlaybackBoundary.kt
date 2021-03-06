package org.rliz.cfm.recorder.playback.boundary

import com.fasterxml.jackson.databind.ObjectMapper
import org.rliz.cfm.recorder.common.data.contentMap
import org.rliz.cfm.recorder.common.exception.MbsLookupFailedException
import org.rliz.cfm.recorder.common.exception.NotFoundException
import org.rliz.cfm.recorder.common.log.logger
import org.rliz.cfm.recorder.mbs.service.MbsService
import org.rliz.cfm.recorder.playback.api.PlaybackRes
import org.rliz.cfm.recorder.playback.auth.demandOwnership
import org.rliz.cfm.recorder.playback.data.*
import org.rliz.cfm.recorder.user.boundary.UserBoundary
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.util.IdGenerator
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutionException

@Service
class PlaybackBoundary {

    companion object {
        val log = logger<PlaybackBoundary>()
    }

    @Autowired
    lateinit var userBoundary: UserBoundary

    @Autowired
    lateinit var rawPlaybackDataRepo: RawPlaybackDataRepo

    @Autowired
    lateinit var playbackRepo: PlaybackRepo

    @Autowired
    lateinit var idgen: IdGenerator

    @Autowired
    lateinit var mbsService: MbsService

    @Autowired
    lateinit var nowPlayingRepo: NowPlayingRepo

    @Autowired
    lateinit var objectMapper: ObjectMapper

    fun createPlayback(artists: List<String>, recordingTitle: String, releaseTitle: String, trackLength: Long? = null,
                       playTime: Long? = null, discNumber: Int? = null, trackNumber: Int? = null,
                       playbackTimestamp: Long? = null, source: String?): PlaybackDto {

        val rawPlaybackData = rawPlaybackDataRepo.save(
                RawPlaybackData(
                        artists = artists,
                        artistJson = objectMapper.writeValueAsString(artists),
                        recordingTitle = recordingTitle,
                        releaseTitle = releaseTitle,
                        length = trackLength,
                        discNumber = discNumber,
                        trackNumber = trackNumber
                )
        )

        val user = userBoundary.getCurrentUser()
        val timestamp = playbackTimestamp ?: Instant.now().epochSecond
        val time = playTime ?: trackLength

        val playback = Playback(idgen.generateId(), user, timestamp, time, rawPlaybackData, source)
        try {
            mbsService.identifyPlayback(recordingTitle, releaseTitle, artists)
                    .get()
                    .apply {
                        playback.recordingUuid = recordingId
                        playback.releaseGroupUuid = releaseGroupId
                    }
        } catch (e: ExecutionException) {
            log.info("Failed to lookup details via mbs service for new playback")
            log.debug("Causing exception for failed lookup during create playback", e)
        }

        return makePlaybackView(playbackRepo.save(playback))
    }

    fun getPlaybacksForUser(userId: UUID, broken: Boolean, pageable: Pageable): Page<PlaybackDto> =
            makePlaybackView(
                    if (broken)
                        playbackRepo.findBrokenPlaybacksForUser(userId, pageable)
                    else playbackRepo.findPlaybacksForUser(userId, pageable)
            )

    fun getAccumulatedBrokenPlaybacks(userId: UUID, pageable: Pageable): Page<AccumulatedPlaybacksDto> =
            playbackRepo.findAccumulatedBrokenPlaybacks(userId, pageable)
                    .map { acc -> acc.toDto(objectMapper.readValue(acc.artistsJson, List::class.java) as List<String>) }


    fun updatePlayback(playbackId: UUID,
                       skipMbs: Boolean,
                       artists: List<String>? = null,
                       recordingTitle: String? = null,
                       releaseTitle: String? = null,
                       trackLength: Long? = null,
                       playTime: Long? = null,
                       discNumber: Int? = null,
                       trackNumber: Int? = null,
                       playbackTimestamp: Long? = null): PlaybackDto {

        val playback = playbackRepo.findOneByUuid(playbackId) ?: throw NotFoundException(Playback::class)
        demandOwnership(playback)

        val originalData = playback.originalData!!
        if (artists != null) {
            originalData.artists = artists
            originalData.artistJson = objectMapper.writeValueAsString(artists)
        }
        if (recordingTitle != null) originalData.recordingTitle = recordingTitle
        if (releaseTitle != null) originalData.releaseTitle = releaseTitle
        if (trackLength != null) originalData.length = trackLength
        if (playTime != null) playback.playTime = playTime
        if (discNumber != null) originalData.discNumber = discNumber
        if (trackNumber != null) originalData.trackNumber = trackNumber
        if (playbackTimestamp != null) playback.timestamp = playbackTimestamp

        // Detect mbs details again, if not skipped
        if (!skipMbs) {
            try {
                mbsService.identifyPlayback(
                        originalData.recordingTitle!!,
                        originalData.releaseTitle!!,
                        originalData.artists!!
                )
                        .get()
                        .apply {
                            playback.recordingUuid = recordingId
                            playback.releaseGroupUuid = releaseGroupId
                        }
            } catch (e: ExecutionException) {
                log.info("Failed to lookup details via mbs service during PATCH; Fallback to broken playback")
                log.debug("Causing issue for failed lookup was", e)
                playback.recordingUuid = null
                playback.releaseGroupUuid = null
            }
        }
        playback.originalData = rawPlaybackDataRepo.save(playback.originalData)
        return makePlaybackView(playbackRepo.save(playback))
    }

    fun getPlayback(playbackId: UUID): PlaybackDto =
            findPlayback(playbackId) ?: throw NotFoundException(Playback::class)

    fun findPlayback(playbackId: UUID): PlaybackDto? = playbackRepo.findOneByUuid(playbackId)?.let {
        makePlaybackView(listOf(it)).first()
    }

    fun batchCreatePlaybacks(batch: List<PlaybackRes>): List<BatchResultItem> = batch.map { playbackRes ->

        /* We try to prevent here anything that could fail the entire transaction. The batch import is handled in one
        single transaction, which means one failed validation cancels the batch import. Taking out batch items that will
        should prevent this. */
        if (playbackRes.artists.isNotEmpty()
                && playbackRes.artists.map(String::isNotBlank).isNotEmpty()
                && playbackRes.recordingTitle != null
                && playbackRes.recordingTitle.isNotBlank()
                && playbackRes.releaseTitle != null
                && playbackRes.releaseTitle.isNotBlank()) {

            val identifiedPlaybackFuture =
                    mbsService.identifyPlayback(
                            playbackRes.recordingTitle,
                            playbackRes.releaseTitle,
                            playbackRes.artists
                    )
            return@map {
                val rawPlaybackData = rawPlaybackDataRepo.save(
                        RawPlaybackData(
                                artists = playbackRes.artists,
                                artistJson = objectMapper.writeValueAsString(playbackRes.artists),
                                recordingTitle = playbackRes.recordingTitle,
                                releaseTitle = playbackRes.releaseTitle,
                                length = playbackRes.trackLength,
                                discNumber = playbackRes.discNumber,
                                trackNumber = playbackRes.trackNumber
                        )
                )
                val user = userBoundary.getCurrentUser()
                val timestamp = playbackRes.timestamp ?: Instant.now().epochSecond
                val time = playbackRes.playTime ?: playbackRes.trackLength

                val playback = Playback(
                        playbackRes.id ?: idgen.generateId(),
                        user,
                        timestamp,
                        time,
                        rawPlaybackData,
                        playbackRes.source
                )
                try {
                    identifiedPlaybackFuture
                            .get()
                            .apply {
                                playback.recordingUuid = recordingId
                                playback.releaseGroupUuid = releaseGroupId
                            }
                } catch (e: ExecutionException) {
                    log.info(
                            "Failed to lookup details via mbs service for new playback ({},{},{})",
                            playbackRes.recordingTitle,
                            playbackRes.releaseTitle,
                            playbackRes.artists
                    )
                    log.debug("Causing exception for failed lookup during create playback", e)
                }
                playbackRepo.save(playback)
                BatchResultItem(true)
            }
        } else return@map { BatchResultItem(false) }
    }.map { it() }

    fun setNowPlaying(artists: List<String>, title: String, release: String, timestamp: Long?, trackLength: Long?) =
            userBoundary.getCurrentUser().let { user ->

                val nowPlaying = (nowPlayingRepo.findOneByUserUuid(user.uuid!!) ?: NowPlaying()).apply {
                    this.artists = artists
                    this.recordingTitle = title
                    this.releaseTitle = release
                    this.timestamp = (timestamp ?: Instant.now().epochSecond) + (trackLength ?: 600)
                    this.user = user
                    this.recordingUuid = null
                    this.releaseGroupUuid = null
                }

                try {
                    mbsService.identifyPlayback(title, release, artists)
                            .get().apply {
                        nowPlaying.recordingUuid = this.recordingId
                        nowPlaying.releaseGroupUuid = this.releaseGroupId
                    }
                } catch (e: ExecutionException) {
                    log.info(
                            "Failed to lookup details via mbs service for new playback ({},{},{})",
                            title,
                            release,
                            artists
                    )
                }
                makePlaybackView(nowPlayingRepo.save(nowPlaying))
            }

    fun getNowPlaying(): PlaybackDto = (nowPlayingRepo.findOneByUserUuid(userBoundary.getCurrentUser().uuid!!)?.apply {
        if (this.timestamp!! < Instant.now().epochSecond) throw NotFoundException(NowPlaying::class, "user")
    }?.let(this::makePlaybackView)) ?: throw NotFoundException(NowPlaying::class, "user")

    private fun makePlaybackView(nowPlaying: NowPlaying): PlaybackDto = nowPlaying.let { nowPlaying ->
        if (nowPlaying.recordingUuid != null && nowPlaying.releaseGroupUuid != null) {
            val recordingViewFuture = mbsService.getRecordingView(listOf(nowPlaying.recordingUuid!!))
            val releaseGroupViewFuture = mbsService.getReleaseGroupView(listOf(nowPlaying.releaseGroupUuid!!))

            try {
                val recordingView = recordingViewFuture.get().elements.first()
                val releaseGroupView = releaseGroupViewFuture.get().elements.first()

                return@let PlaybackDto(
                        artists = recordingView.artists,
                        recordingTitle = recordingView.name,
                        releaseTitle = releaseGroupView.name,
                        timestamp = nowPlaying.timestamp,
                        broken = false
                )
            } catch (e: ExecutionException) {
                // nothing
            }
        }
        PlaybackDto(
                artists = nowPlaying.artists!!,
                recordingTitle = nowPlaying.recordingTitle!!,
                releaseTitle = nowPlaying.releaseTitle!!,
                timestamp = nowPlaying.timestamp,
                broken = true
        )
    }

    private fun makePlaybackView(playback: Playback): PlaybackDto = playback.let { makePlaybackView(listOf(it)) }.first()

    private fun makePlaybackView(playbacks: Page<Playback>): Page<PlaybackDto> =
            playbacks.contentMap { it -> makePlaybackView(it) }

    private fun makePlaybackView(playbacks: List<Playback>): List<PlaybackDto> =
            if (playbacks.isEmpty()) emptyList()
            else playbacks.let {
                val releaseGroupsFuture = mbsService.getReleaseGroupView(it.mapNotNull { it.releaseGroupUuid })
                val recordingsFuture = mbsService.getRecordingView(it.mapNotNull { it.recordingUuid })

                val (recordings, releaseGroups) = try {
                    Pair(
                            recordingsFuture.get().elements.map { it.id to it }.toMap(),
                            releaseGroupsFuture.get().elements.map { it.id to it }.toMap()
                    )
                } catch (e: Exception) {
                    return@let it.map { it.toDto() }
                }

                it.map {
                    if (it.recordingUuid != null) {
                        val recordingView = recordings[it.recordingUuid!!] ?: throw MbsLookupFailedException()
                        val releaseGroupView = releaseGroups[it.releaseGroupUuid!!] ?: throw MbsLookupFailedException()
                        PlaybackDto(
                                artists = recordingView.artists,
                                recordingTitle = recordingView.name,
                                releaseTitle = releaseGroupView.name,
                                timestamp = it.timestamp,
                                playTime = it.playTime,
                                broken = false,
                                id = it.uuid
                        )
                    } else it.toDto()
                }
            }
}
