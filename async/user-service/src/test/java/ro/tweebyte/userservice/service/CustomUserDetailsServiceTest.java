package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private UserRepository userRepository;

    @Test
    void testLoadUserByUsername_ExistingUser() {
        UUID userId = UUID.randomUUID();
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setEmail("test@example.com");
        userEntity.setPassword("password");

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(userId.toString());

        assertNotNull(userDetails);
        assertEquals("password", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().isEmpty());

        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void testLoadUserByUsername_NonExistingUser() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
            UsernameNotFoundException.class,
            () -> customUserDetailsService.loadUserByUsername(userId.toString())
        );

        verify(userRepository, times(1)).findById(userId);
    }
}