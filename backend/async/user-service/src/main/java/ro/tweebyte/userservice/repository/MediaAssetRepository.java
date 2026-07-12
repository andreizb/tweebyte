/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ro.tweebyte.userservice.entity.MediaAssetEntity;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {

	Optional<MediaAssetEntity> findByChecksum(long checksum);

	// Ids of media uploaded before the cutoff — the stale-GC's candidate set.
	// Projects only the id so the GC never pulls the BYTEA blob into memory.
	@Query(value = "SELECT id FROM media_assets WHERE created_at < :cutoff", nativeQuery = true)
	List<UUID> findStaleIds(@Param("cutoff") LocalDateTime cutoff);

	// Distinct originals still pointed at by some preview's source_media_id. The
	// stale-GC unions these into its reachable set so an original kept alive only
	// by a derived preview is never collected.
	@Query(value = "SELECT DISTINCT source_media_id FROM media_assets WHERE source_media_id IS NOT NULL",
			nativeQuery = true)
	List<UUID> findReferencedSourceMediaIds();

}
