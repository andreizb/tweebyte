/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table("media_assets")
@Builder
public class MediaAssetEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column("content_type")
	private String contentType;

	@Column("size_bytes")
	private Long sizeBytes;

	@Column("checksum")
	private Long checksum;

	@Column("created_at")
	private LocalDateTime createdAt;

	@Column("data")
	private byte[] data;

	// Set on a preview, pointing at the original it was derived from; null for
	// originals and pure uploads. Keeps the original reachable for the stale-GC.
	@Column("source_media_id")
	private UUID sourceMediaId;

	// bcrypt digest of the reveal password. Set on every preview (the /preview
	// endpoint requires a password); null for originals and pure uploads.
	@Column("access_hash")
	private String accessHash;

	@Transient
	private boolean isInsertable;

	@Override
	public boolean isNew() {
		return this.isInsertable || this.id == null;
	}

}
