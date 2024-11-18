package ro.tweebyte.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.model.CustomUserDetails;
import ro.tweebyte.userservice.repository.UserRepository;

import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    @Override
    public Mono<UserDetails> findByUsername(String userId) {
        return userRepository.findById(UUID.fromString(userId))
            .switchIfEmpty(Mono.error(new UsernameNotFoundException("User not found with id: " + userId)))
            .map(userEntity -> new CustomUserDetails(userEntity.getId(), userEntity.getEmail(), userEntity.getPassword(), new ArrayList<>()));
    }

}
