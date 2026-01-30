package com.example.echo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "local"})
class EchoApplicationTests {

	@Test
	void contextLoads() {
	}

}
