package ro.tweebyte.userservice.mapper;

import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.UserUpdateRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    @Autowired
    private BCryptPasswordEncoder encoder;

    public abstract UserDto mapToProfileDto(UserEntity entity, Long following, Long followers, List<TweetDto> tweets);

    @Mapping(target = "biography", ignore = true)
    @Mapping(target = "tweets", ignore = true)
    @Mapping(target = "following", ignore = true)
    @Mapping(target = "followers", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "birthDate", ignore = true)
    public abstract UserDto mapToSummaryDto(UserEntity entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void mapRequestToEntity(UserUpdateRequest request, @MappingTarget UserEntity entity);

    public UserEntity mapRequestToEntity(UserRegisterRequest request) {
        UserEntity userEntity = mapRegisterRequestToUserEntity(request);
        userEntity.setId(UUID.randomUUID());
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setPassword(encoder.encode(request.getPassword()));
        userEntity.setInsertable(true);
        return userEntity;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "password", ignore = true)
    protected abstract UserEntity mapRegisterRequestToUserEntity(UserRegisterRequest request);

}
