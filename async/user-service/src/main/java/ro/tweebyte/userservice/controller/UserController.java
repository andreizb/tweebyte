package ro.tweebyte.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ro.tweebyte.userservice.model.CustomUserDetails;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.service.UserService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public CompletableFuture<UserDto> getUserProfile(@RequestHeader(value = "Authorization") String authorization,
                                                     @PathVariable(value = "userId") UUID userId) {
        return userService.getUserProfile(userId, authorization);
    }

    @GetMapping("/summary/{userId}")
    public CompletableFuture<UserDto> getUserSummary(@PathVariable(value = "userId") UUID userId) {
        return userService.getUserSummary(userId);
    }

    @GetMapping("/summary/name/{userName}")
    public CompletableFuture<UserDto> getUserSummaryByUserName(@PathVariable(value = "userName") String userName) {
        return userService.getUserSummaryByUserName(userName);
    }

    @GetMapping("/search/{searchTerm}")
    public CompletableFuture<List<UserDto>> searchUser(@PathVariable(value = "searchTerm") String searchTerm) {
        return userService.searchUser(searchTerm);
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> updateUser(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              @ModelAttribute UserUpdateRequest userUpdateRequest) {
        return userService.updateUser(userDetails.getUserId(), userUpdateRequest);
    }

}
