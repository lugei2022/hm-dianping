package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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

		// 4.保存到session
		session.setAttribute("code", code);

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
		// 2.校验验证码
		Object cacheCode = session.getAttribute("code");
		String code = loginForm.getCode();
		if (ObjectUtils.isEmpty(cacheCode) || !cacheCode.toString().equals(code)) {
			// 3.不一致,报错
			return Result.fail("验证码有误");
		}

		// 4.一致,根据手机号查询用户
		User user = this.query().eq("phone", phone).one();

		// 5.判断用户是否存在
		if (ObjectUtils.isEmpty(user)) {
			// 6.不存在,创建用户并保存
			user = createUserWithPhone(phone);

		}

		// 7.保存用户信息到session
		session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
		return Result.ok();
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
