package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@SpringBootTest
class HmDianPingApplicationTests {
	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Resource
	private UserServiceImpl userService;

	String generatorPhone() {
		return "13" + RandomUtil.randomNumbers(9);
	}

	User generatorUser(String phone) {
		return userService.createUserWithPhone(phone);
	}

	void writeToken(String token) {
		FileWriter fileWriter = new FileWriter("E:\\token.txt");
		fileWriter.append(token);
	}

	// @Test
	void generator() {
		for (int i = 0; i < 1000; i++) {
			String phone = generatorPhone();

			User user = generatorUser(phone);

			String token = UUID.randomUUID().toString(true);
			// System.out.println(token);
			writeToken(token + "\n");

			UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
			Map<String, Object> userMap = BeanUtil.beanToMap(
					userDTO,
					new HashMap<>(),
					CopyOptions.create().
							setIgnoreNullValue(true).
							setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
			);
			stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
			stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL,
					TimeUnit.DAYS);
		}
	}


}
