/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

	private String userName;

	private String email;

	private String biography;

	private Boolean isPrivate;

	private String password;

	private LocalDate birthDate;

	// Optional avatar swap → media_assets.id. Absent leaves the current picture
	// unchanged (swap-only); supplying the default-avatar sentinel is how the client
	// "removes" a picture. When present it must reference an existing asset (validated
	// in UserService.updateUser).
	private UUID profilePictureId;

}
