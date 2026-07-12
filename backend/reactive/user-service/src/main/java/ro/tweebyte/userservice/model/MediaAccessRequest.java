/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Password body for the gated media endpoints. POST /media/{id}/preview requires it (it
 * becomes the preview's bcrypt access gate) and POST /media/{id}/reveal supplies it to
 * unlock the original. @NotBlank makes the gate mandatory: a missing or blank password is
 * rejected with a structured 400 before the service runs.
 *
 * @author Andrei Zbarcea
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class MediaAccessRequest {

	@NotBlank(message = "Password is required")
	@JsonProperty("password")
	private String password;

}
