package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserDtoTest {

    @Test
    void getId() {
        UUID id = UUID.randomUUID();
        UserDto userDto = new UserDto();
        userDto.setId(id);
        assertEquals(id, userDto.getId());
    }

    @Test
    void getUserName() {
        String userName = "testUser";
        UserDto userDto = new UserDto();
        userDto.setUserName(userName);
        assertEquals(userName, userDto.getUserName());
    }

    @Test
    void getEmail() {
        String email = "test@example.com";
        UserDto userDto = new UserDto();
        userDto.setEmail(email);
        assertEquals(email, userDto.getEmail());
    }

    @Test
    void getBiography() {
        String biography = "Test biography";
        UserDto userDto = new UserDto();
        userDto.setBiography(biography);
        assertEquals(biography, userDto.getBiography());
    }

    @Test
    void getIsPrivate() {
        Boolean isPrivate = true;
        UserDto userDto = new UserDto();
        userDto.setIsPrivate(isPrivate);
        assertEquals(isPrivate, userDto.getIsPrivate());
    }

    @Test
    void getBirthDate() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        UserDto userDto = new UserDto();
        userDto.setBirthDate(birthDate);
        assertEquals(birthDate, userDto.getBirthDate());
    }

    @Test
    void getCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        UserDto userDto = new UserDto();
        userDto.setCreatedAt(createdAt);
        assertEquals(createdAt, userDto.getCreatedAt());
    }

    @Test
    void getFollowing() {
        Long following = 100L;
        UserDto userDto = new UserDto();
        userDto.setFollowing(following);
        assertEquals(following, userDto.getFollowing());
    }

    @Test
    void getFollowers() {
        Long followers = 200L;
        UserDto userDto = new UserDto();
        userDto.setFollowers(followers);
        assertEquals(followers, userDto.getFollowers());
    }

    @Test
    void getTweets() {
        List<TweetDto> tweets = List.of();
        UserDto userDto = new UserDto();
        userDto.setTweets(tweets);
        assertEquals(tweets, userDto.getTweets());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        UserDto userDto = new UserDto();
        userDto.setId(id);
        assertEquals(id, userDto.getId());
    }

    @Test
    void setUserName() {
        String userName = "testUser";
        UserDto userDto = new UserDto();
        userDto.setUserName(userName);
        assertEquals(userName, userDto.getUserName());
    }

    @Test
    void setEmail() {
        String email = "test@example.com";
        UserDto userDto = new UserDto();
        userDto.setEmail(email);
        assertEquals(email, userDto.getEmail());
    }

    @Test
    void setBiography() {
        String biography = "Test biography";
        UserDto userDto = new UserDto();
        userDto.setBiography(biography);
        assertEquals(biography, userDto.getBiography());
    }

    @Test
    void setIsPrivate() {
        Boolean isPrivate = true;
        UserDto userDto = new UserDto();
        userDto.setIsPrivate(isPrivate);
        assertEquals(isPrivate, userDto.getIsPrivate());
    }

    @Test
    void setBirthDate() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        UserDto userDto = new UserDto();
        userDto.setBirthDate(birthDate);
        assertEquals(birthDate, userDto.getBirthDate());
    }

    @Test
    void setCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        UserDto userDto = new UserDto();
        userDto.setCreatedAt(createdAt);
        assertEquals(createdAt, userDto.getCreatedAt());
    }

    @Test
    void setFollowing() {
        Long following = 100L;
        UserDto userDto = new UserDto();
        userDto.setFollowing(following);
        assertEquals(following, userDto.getFollowing());
    }

    @Test
    void setFollowers() {
        Long followers = 200L;
        UserDto userDto = new UserDto();
        userDto.setFollowers(followers);
        assertEquals(followers, userDto.getFollowers());
    }

    @Test
    void setTweets() {
        List<TweetDto> tweets = List.of();
        UserDto userDto = new UserDto();
        userDto.setTweets(tweets);
        assertEquals(tweets, userDto.getTweets());
    }

}