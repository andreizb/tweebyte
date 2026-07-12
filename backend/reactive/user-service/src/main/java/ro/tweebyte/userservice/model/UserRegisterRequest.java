/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisterRequest {

	@NotBlank(message = "Full name is required")
	private String userName;

	@NotBlank(message = "Email is required")
	@Email(message = "Email must be a valid email address")
	private String email;

	@Size(max = 500, message = "Biography must not exceed 500 characters")
	private String biography;

	@NotBlank(message = "Password is required")
	@Size(min = 8, message = "Password must be at least 8 characters long")
	private String password;

	@NotNull(message = "Birth date is required")
	private LocalDate birthDate;

	private Boolean isPrivate;

	// Optional avatar → media_assets.id. Absent on a fresh registration means the
	// user gets the default-avatar sentinel; when present it must reference an
	// existing asset (validated in AuthenticationService.register).
	private UUID profilePictureId;

}
