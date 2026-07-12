/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.support;

import java.util.Locale;

/**
 * Which stack the current Maven profile selected.
 *
 * Read from the `fe.stack` system property set by the failsafe configuration in pom.xml.
 * Defaults to async if absent (mirrors the POM default).
 */
public enum StackProfile {

	ASYNC, REACTIVE;

	public static StackProfile current() {
		String s = System.getProperty("fe.stack", "async").trim().toLowerCase(Locale.ROOT);
		return switch (s) {
			case "reactive" -> REACTIVE;
			default -> ASYNC;
		};
	}

	public String composeName() {
		return name().toLowerCase(Locale.ROOT);
	}

}
