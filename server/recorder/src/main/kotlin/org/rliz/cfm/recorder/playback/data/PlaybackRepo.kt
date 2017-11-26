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

    @EntityGraph(attributePaths = arrayOf("originalData", "user"))
    fun findOneByUuid(uuid: UUID): Playback?
}
