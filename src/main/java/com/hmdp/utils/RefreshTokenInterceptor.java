package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {
	// 不能使用 @Resource,该类不由spring创建,只在需要时被创建,所以也不能添加@Compent来获取spring注入
	// 由其它被spring注解的类通过构造函数注入,如springMVC配置类
	private StringRedisTemplate stringRedisTemplate;

	public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
							 Object handler) throws Exception {
		// 1.获取请求头中token
		String token = request.getHeader("authorization");
		if (StrUtil.isBlank(token)) {
			return true;
		}

		// 2.基于token获取redis中的用户
		Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);

		// 3.判断用户是否存在
		if (userMap.isEmpty()) {
			return true;
		}

		// 5.将查询到的hash值转换为UserDTO对象
		UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

		// 6.存在,保存用户信息到ThreadLocal
		UserHolder.saveUser(userDTO);

		// 7.刷新 token有效期
		stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

		// 8.放行
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
								Exception ex) throws Exception {
		UserHolder.removeUser();
	}
}
