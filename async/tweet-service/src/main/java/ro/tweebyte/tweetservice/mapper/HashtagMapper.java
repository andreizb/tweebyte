package ro.tweebyte.tweetservice.mapper;

import org.mapstruct.Mapper;
import ro.tweebyte.tweetservice.entity.HashtagEntity;

@Mapper(componentModel = "spring")
public abstract class HashtagMapper {

    public abstract HashtagEntity mapTextToEntity(String text);

}
