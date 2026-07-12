/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "media_assets")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MediaAssetEntity implements Persistable<UUID> {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private Long sizeBytes;

	@Column(name = "checksum", nullable = false, unique = true)
	private Long checksum;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "data", nullable = false)
	private byte[] data;

	// Set on a preview, pointing at the original it was derived from; null for
	// originals and pure uploads. Keeps the original reachable for the stale-GC.
	@Column(name = "source_media_id")
	private UUID sourceMediaId;

	// bcrypt digest of the reveal password. Set on every preview (the /preview
	// endpoint requires a password); null for originals and pure uploads.
	@Column(name = "access_hash")
	private String accessHash;

	// The id is assigned client-side (content hash), so JpaRepository.save() would otherwise
	// route through merge() and issue a SELECT-before-INSERT probe. Persistable.isNew() returns
	// true when this flag is set (the store factories set it), steering save() straight to
	// persist() — a direct INSERT, no probe — matching the reactive MediaAssetEntity and the
	// async UserEntity pattern. Transient: not a column.
	@Transient
	private boolean isInsertable;

	@Override
	public boolean isNew() {
		return this.isInsertable || this.id == null;
	}

}
