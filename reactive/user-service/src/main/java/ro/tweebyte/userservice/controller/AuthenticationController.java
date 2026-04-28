package ro.tweebyte.userservice.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.service.AuthenticationService;

@RestController
@RequestMapping(path = "/auth")
@AllArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping(path = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<AuthenticationResponse> userRegister(@Valid @ModelAttribute UserRegisterRequest request,
                                                     @RequestParam(value = "picture", required = false) MultipartFile picture) {
        return authenticationService.register(request);
    }

    @PostMapping(path = "/login")
    public Mono<AuthenticationResponse> userLogin(@Valid @RequestBody UserLoginRequest request) {
        return authenticationService.login(request);
    }

}
