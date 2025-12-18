package ro.tweebyte.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import ro.tweebyte.userservice.config.BoundedElasticMetrics;

@SpringBootTest
class UserServiceApplicationTests {

	@MockBean
	private BoundedElasticMetrics boundedElasticMetrics;

	@MockBean
	private ro.tweebyte.userservice.mapper.UserMapper userMapper;

	@Test
	void contextLoads() {
		try {
			Class.forName("ro.tweebyte.userservice.model.UserRegisterRequest");
			System.out.println(">>> UserRegisterRequest LOADED SUCCESSFULLY");
		} catch (ClassNotFoundException e) {
			System.out.println(">>> UserRegisterRequest NOT FOUND");
			e.printStackTrace();
		}
	}

}
