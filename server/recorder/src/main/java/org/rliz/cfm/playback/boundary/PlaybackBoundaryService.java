package org.rliz.cfm.playback.boundary;

import org.rliz.cfm.playback.api.dto.SavePlaybackDto;
import org.rliz.cfm.playback.model.Playback;
import org.rliz.cfm.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service for business functions related to {@link Playback}s.
 */
public interface PlaybackBoundaryService {

    /**
     * Creates a new playback event using the musicbrainz track ID and release group ID.
     *
     * @param dto       the create playback resource
     * @param threshold minimum score for mbs to consider the result a match
     * @return persisted playback event
     */
    Playback createPlayback(SavePlaybackDto dto, int threshold);

    /**
     * Gets a list of {@link Playback}s regardless of the user.
     *
     * @param pageable pageable from the request
     * @return page of playbacks
     */
    Page<Playback> findAll(Pageable pageable);

    /**
     * Gets a page of {@link Playback}s for the current user.
     *
     * @param onlyBroken only include broken playbacks
     * @param pageable   pageable from the request
     * @return page of playbacks
     */
    Page<Playback> findAllForCurrentUser(boolean onlyBroken, Pageable pageable);

    /**
     * Deletes the playback with given identifier if the user is permitted to do so.
     *
     * @param identifier        identifier of the {@link Playback}
     * @param authenticatedUser currently authenticated user
     */
    void deletePlayback(UUID identifier, User authenticatedUser);

    /**
     * Change details of a {@link Playback} accordingly. Null fields in the body will be ignored.
     *
     * @param identifier        identifier of the {@link Playback} to fix
     * @param body              body containing the details
     * @param authenticatedUser the authenticated user
     * @return the updated {@link Playback}
     */
    Playback updatePlayback(UUID identifier, SavePlaybackDto body, User authenticatedUser);

    /**
     * Loads the {@link Playback} with given UUID from the database and tries to detect the musicbrainz IDs
     * from the given original playback details. If they are detected, the IDs are updated accordingly. Otherwise the
     * {@link Playback} is returned unchanged.
     *
     * @param identifier        identifier of the {@link Playback}.
     * @param threshold         minimum score for mbs to consider a result a match
     * @param authenticatedUser currently authenticated user
     * @return the updated {@link Playback}
     */
    Playback detectAndUpdateMbDetails(UUID identifier, int threshold, User authenticatedUser);

}
