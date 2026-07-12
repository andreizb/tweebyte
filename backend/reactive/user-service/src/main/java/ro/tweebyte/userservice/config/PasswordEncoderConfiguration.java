/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class PasswordEncoderConfiguration {

	// bcrypt runs off the benchmark path only: actuator basic-auth (W4) and the media
	// preview/reveal access-hash check. Kept ungated so the bean survives the benchmark
	// overlay (which excludes Spring Security autoconfiguration) for the always-present
	// UserService/MediaService consumers.
	@Bean
	public BCryptPasswordEncoder bCryptPasswordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
