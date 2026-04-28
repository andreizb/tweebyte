package ro.tweebyte.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public Mono<UserDto> getUserProfile(@RequestHeader(value = "Authorization") String authorization,
            @PathVariable(value = "userId") UUID userId) {
        return userService.getUserProfile(userId, authorization);
    }

    @GetMapping("/summary/{userId}")
    public Mono<UserDto> getUserSummary(@PathVariable(value = "userId") UUID userId) {
        return userService.getUserSummary(userId);
    }

    @GetMapping("/summary/name/{userName}")
    public Mono<UserDto> getUserSummaryByUserName(@PathVariable(value = "userName") String userName) {
        return userService.getUserSummaryByUserName(userName);
    }

    @GetMapping("/search/{searchTerm}")
    public Flux<UserDto> searchUser(@PathVariable(value = "searchTerm") String searchTerm) {
        return userService.searchUser(searchTerm);
    }

    @PutMapping(path = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> updateUser(@PathVariable(value = "userId") UUID userId,
            @ModelAttribute UserUpdateRequest userUpdateRequest) {
        return userService.updateUser(userId, userUpdateRequest);
    }

}