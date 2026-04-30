package ro.tweebyte.userservice.mapper;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.UserUpdateRequest;

@Generated(value = "org.mapstruct.ap.MappingProcessor", date = "2025-12-18T19:57:47+0200", comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.7 (Eclipse Adoptium)")
@Component
public class UserMapperImpl extends UserMapper {

    @Override
    public UserDto mapToProfileDto(UserEntity entity, Long following, Long followers, List<TweetDto> tweets) {
        if (entity == null && following == null && followers == null && tweets == null) {
            return null;
        }

        UserDto.UserDtoBuilder userDto = UserDto.builder();

        if (entity != null) {
            userDto.id(entity.getId());
            userDto.userName(entity.getUserName());
            userDto.email(entity.getEmail());
            userDto.biography(entity.getBiography());
            userDto.isPrivate(entity.getIsPrivate());
            userDto.birthDate(entity.getBirthDate());
            userDto.createdAt(entity.getCreatedAt());
        }
        userDto.following(following);
        userDto.followers(followers);
        List<TweetDto> list = tweets;
        if (list != null) {
            userDto.tweets(new ArrayList<TweetDto>(list));
        }

        return userDto.build();
    }

    @Override
    public UserDto mapToSummaryDto(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        UserDto.UserDtoBuilder userDto = UserDto.builder();

        userDto.id(entity.getId());
        userDto.userName(entity.getUserName());
        userDto.isPrivate(entity.getIsPrivate());
        userDto.createdAt(entity.getCreatedAt());

        return userDto.build();
    }

    @Override
    protected void copyRequestFieldsToEntity(UserUpdateRequest request, UserEntity entity) {
        if (request == null) {
            return;
        }

        if (request.getUserName() != null) {
            entity.setUserName(request.getUserName());
        }
        if (request.getEmail() != null) {
            entity.setEmail(request.getEmail());
        }
        if (request.getPassword() != null) {
            entity.setPassword(request.getPassword());
        }
        if (request.getBiography() != null) {
            entity.setBiography(request.getBiography());
        }
        if (request.getIsPrivate() != null) {
            entity.setIsPrivate(request.getIsPrivate());
        }
        if (request.getBirthDate() != null) {
            entity.setBirthDate(request.getBirthDate());
        }
    }

    @Override
    protected UserEntity mapRegisterRequestToUserEntity(UserRegisterRequest request) {
        if (request == null) {
            return null;
        }

        UserEntity.UserEntityBuilder userEntity = UserEntity.builder();

        userEntity.userName(request.getUserName());
        userEntity.email(request.getEmail());
        userEntity.biography(request.getBiography());
        userEntity.isPrivate(request.getIsPrivate());
        userEntity.birthDate(request.getBirthDate());

        return userEntity.build();
    }
}
