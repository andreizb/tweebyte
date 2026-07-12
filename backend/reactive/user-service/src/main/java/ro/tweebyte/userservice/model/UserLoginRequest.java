/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class UserLoginRequest {

	/**
	 * Validate the login payload up-front so a malformed body returns a structured 400
	 * with field errors instead of leaking through the auth path as a generic 401.
	 */
	@Email(message = "Email must be a valid email address")
	@JsonProperty("email")
	private String email;

	@NotBlank(message = "Password is required")
	@JsonProperty("password")
	private String password;

}
