package ro.tweebyte.userservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.service.AuthenticationService;

import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/auth")
@AllArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping(path = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<AuthenticationResponse> userRegister(@Valid @ModelAttribute UserRegisterRequest request) {
        return authenticationService.register(request);
    }

    @PostMapping(path = "/login")
    public CompletableFuture<AuthenticationResponse> userLogin(@Valid @RequestBody UserLoginRequest request) {
        return authenticationService.login(request);
    }

}
