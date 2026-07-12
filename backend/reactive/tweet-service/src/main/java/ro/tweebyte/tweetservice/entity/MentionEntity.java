/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

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

@Table("mentions")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MentionEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column("user_id")
	private UUID userId;

	@Column("text")
	private String text;

	@Column("tweet_id")
	private UUID tweetId;

	@Transient
	private boolean isInsertable;

	@Override
	public boolean isNew() {
		return this.isInsertable || this.id == null;
	}

}
