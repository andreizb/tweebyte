/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.userservice.entity.MediaAssetEntity;

@Repository
public interface MediaAssetRepository extends ReactiveCrudRepository<MediaAssetEntity, UUID> {

	Mono<MediaAssetEntity> findByChecksum(long checksum);

	// Ids of media uploaded before the cutoff — the stale-GC's candidate set.
	// Projects only the id so the GC never pulls the BYTEA blob into memory.
	@Query("SELECT id FROM media_assets WHERE created_at < :cutoff")
	Flux<UUID> findStaleIds(LocalDateTime cutoff);

	// Distinct originals still pointed at by some preview's source_media_id. The
	// stale-GC unions these into its reachable set so an original kept alive only
	// by a derived preview is never collected.
	@Query("SELECT DISTINCT source_media_id FROM media_assets WHERE source_media_id IS NOT NULL")
	Flux<UUID> findReferencedSourceMediaIds();

}
