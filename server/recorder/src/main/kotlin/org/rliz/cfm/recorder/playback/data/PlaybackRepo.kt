package org.rliz.cfm.recorder.playback.data

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface PlaybackRepo : JpaRepository<Playback, Long> {

    @Query(value = """
        select distinct p
        from Playback p
        join fetch p.originalData o
        join fetch p.user u
        where u.uuid = :userId
    """, countQuery = """
        select distinct count(p)
        from Playback p
        join p.user u
        where u.uuid = :userId
                """)
    fun findPlaybacksForUser(@Param("userId") userId: UUID, pageable: Pageable): Page<Playback>

    @Query(value = """
        select distinct p
        from Playback p
        join fetch p.originalData o
        join fetch p.user u
        where u.uuid = :userId
        and p.recordingUuid is null
    """, countQuery = """
        select distinct count(p)
        from Playback p
        join p.user u
        where u.uuid = :userId
        and p.recordingUuid is null
                """)
    fun findBrokenPlaybacksForUser(@Param("userId") userId: UUID, pageable: Pageable): Page<Playback>

    @Query(value = """
        select
        new org.rliz.cfm.recorder.playback.data.AccumulatedPlaybacks(count(p), o.artistJson, o.recordingTitle, o.releaseTitle)
        from Playback p
        join p.originalData o
        join p.user u
        group by o.artistJson, o.recordingTitle, o.releaseTitle, u.uuid, p.recordingUuid, p.releaseGroupUuid
        having u.uuid = :userId and p.recordingUuid is null and p.releaseGroupUuid is null
        order by count(p) desc, o.artistJson asc
        """,
            countQuery = """
        select count(p)
        from Playback p
        join p.originalData o
        join p.user u
        group by o.artistJson, o.recordingTitle, o.releaseTitle, u.uuid, p.recordingUuid, p.releaseGroupUuid
        having u.uuid = :userId and p.recordingUuid is null and p.releaseGroupUuid is null
                """)
    fun findAccumulatedBrokenPlaybacks(@Param("userId") userId: UUID, pageable: Pageable): Page<AccumulatedPlaybacks>

    @EntityGraph(attributePaths = arrayOf("originalData", "user"))
    fun findOneByUuid(uuid: UUID): Playback?
}
