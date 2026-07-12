/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.util;

import java.util.UUID;

public final class MediaConstants {

	/**
	 * Default-avatar sentinel seeded in user_service_db (the all-zeros UUID). Fresh
	 * registrations default profile_picture_id to it and "remove my picture" swaps back
	 * to it, so the column is never user-facing NULL. image-upload mints ids via
	 * UUID.nameUUIDFromBytes, which can never produce all-zeros, so the sentinel can't
	 * collide with an uploaded asset. StaleMediaCleanupService hard-excludes it.
	 */
	public static final UUID DEFAULT_AVATAR_ID = new UUID(0L, 0L);

	private MediaConstants() {
	}

}
