package ro.tweebyte.userservice.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.UserUpdateRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class UserMapper {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    public UserDto mapToProfileDto(UserEntity entity, Long following, Long followers, List<TweetDto> tweets) {
        if (entity == null)
            return null;
        return UserDto.builder()
                .id(entity.getId())
                .userName(entity.getUserName())
                .email(entity.getEmail())
                .biography(entity.getBiography())
                .isPrivate(entity.getIsPrivate())
                .birthDate(entity.getBirthDate())
                .createdAt(entity.getCreatedAt())
                .following(following)
                .followers(followers)
                .tweets(tweets != null ? new ArrayList<>(tweets) : Collections.emptyList())
                .build();
    }

    public UserDto mapToSummaryDto(UserEntity entity) {
        if (entity == null)
            return null;
        return UserDto.builder()
                .id(entity.getId())
                .userName(entity.getUserName())
                .isPrivate(entity.getIsPrivate())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public void mapRequestToEntity(UserUpdateRequest request, UserEntity entity) {
        if (request == null || entity == null)
            return;
        if (request.getUserName() != null)
            entity.setUserName(request.getUserName());
        if (request.getEmail() != null)
            entity.setEmail(request.getEmail());
        if (request.getBiography() != null)
            entity.setBiography(request.getBiography());
        if (request.getPassword() != null)
            entity.setPassword(passwordEncoder.encode(request.getPassword()));
        if (request.getBirthDate() != null)
            entity.setBirthDate(request.getBirthDate());
        if (request.getIsPrivate() != null)
            entity.setIsPrivate(request.getIsPrivate());
    }

    public UserEntity mapRequestToEntity(ro.tweebyte.userservice.model.UserRegisterRequest request) {
        if (request == null)
            return null;
        return UserEntity.builder()
                .userName(request.getUserName())
                .email(request.getEmail())
                .biography(request.getBiography())
                .password(passwordEncoder.encode(request.getPassword()))
                .birthDate(request.getBirthDate())
                .isPrivate(request.getIsPrivate())
                .id(UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
