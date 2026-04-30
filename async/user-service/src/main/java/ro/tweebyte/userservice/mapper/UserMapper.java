package ro.tweebyte.userservice.mapper;

import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// NOTE: deliberately NOT @Mapper-annotated. The MapStruct-generated impl
// can't bcrypt the password before field-copy (the encoder is a Spring bean
// not visible to the annotation processor); UserMapperImpl.java is owned
// by hand to bcrypt on both register AND update — without the explicit
// update-side bcrypt, password updates would land in the column as plaintext.
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

    /**
     * bcrypt the password BEFORE delegating to the MapStruct-generated
     * field copy. Without this, async stored the raw password on update, and
     * subsequent logins (which compare against the bcrypt hash) failed — the
     * reactive stack already encoded the password explicitly in its hand-rolled
     * mapper. This restores per-stack symmetry.
     */
    public void mapRequestToEntity(UserUpdateRequest request, @MappingTarget UserEntity entity) {
        if (request != null && request.getPassword() != null) {
            request.setPassword(encoder.encode(request.getPassword()));
        }
        copyRequestFieldsToEntity(request, entity);
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    protected abstract void copyRequestFieldsToEntity(UserUpdateRequest request, @MappingTarget UserEntity entity);

    public UserEntity mapRequestToEntity(UserRegisterRequest request) {
        UserEntity userEntity = mapRegisterRequestToUserEntity(request);
        userEntity.setId(UUID.randomUUID());
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setPassword(encoder.encode(request.getPassword()));
        return userEntity;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "password", ignore = true)
    protected abstract UserEntity mapRegisterRequestToUserEntity(UserRegisterRequest request);

}
