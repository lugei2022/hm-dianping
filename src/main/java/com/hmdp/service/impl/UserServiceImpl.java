package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Override
	public Result sendCode(String phone, HttpSession session) {
		// 1.校验手机号
		if (RegexUtils.isPhoneInvalid(phone)) {
			// 2.如果不符合,返回错误信息
			return Result.fail("手机号格式有误");
		}

		// 3.符合,生成验证码
		String code = RandomUtil.randomNumbers(6);

		// 4.保存到redis
		stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

		// 5.发送验证码
		// 需要平台发送,这里手动模拟
		log.debug("成功发送验证码: {}", code);

		// 6.成功
		return Result.ok();
	}

	@Override
	public Result login(LoginFormDTO loginForm, HttpSession session) {
		String phone = loginForm.getPhone();
		// 1.校验手机号
		if (RegexUtils.isPhoneInvalid(phone)) {
			// 不符合
			return Result.fail("手机号格式有误");
		}
		// 2.校验验证码,从 redis中获取
		String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
		String code = loginForm.getCode();
		if (ObjectUtils.isEmpty(cacheCode) || !cacheCode.equals(code)) {
			// 3.不一致,报错
			return Result.fail("验证码有误");
		}

		// 4.一致,根据手机号查询用户
		User user = this.query().eq("phone", phone).one();

		// 5.判断用户是否存在
		if (ObjectUtil.isEmpty(user)) {
			// 6.不存在,创建用户并保存
			user = createUserWithPhone(phone);

		}

		// 7.保存用户信息到redis

		// 7.1 随机生成token,作为登录令牌
		String token = UUID.randomUUID().toString(true);
		// 7.2 将User对象转换为hash存储
		UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
		Map<String, Object> userMap = BeanUtil.beanToMap(
				userDTO,
				new HashMap<>(),
				CopyOptions.create().
						setIgnoreNullValue(true).
						setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
		);
		// 7.3 存储
		stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
		// 设置有效期,时间类似于session,加上一个随机时间,防止大量值同时失效
		stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL + RandomUtil.randomInt(1, 10),
				TimeUnit.MINUTES);

		return Result.ok(token);
	}

	private User createUserWithPhone(String phone) {
		// 1.创建用户
		User user = new User();
		user.setPhone(phone);
		user.setNickName(USER_NICK_NAME_PREFIX
				+ RandomUtil.randomString(8));
		// 2.保存用户
		this.save(user);
		return user;
	}
}
