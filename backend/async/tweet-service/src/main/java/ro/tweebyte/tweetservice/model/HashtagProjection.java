/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

public interface HashtagProjection {

	UUID getId();

	String getText();

	Long getCount();

}
