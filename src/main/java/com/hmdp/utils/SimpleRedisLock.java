package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
	private String businessName;
	private StringRedisTemplate stringRedisTemplate;

	public SimpleRedisLock(String businessName, StringRedisTemplate stringRedisTemplate) {
		this.businessName = businessName;
		this.stringRedisTemplate = stringRedisTemplate;
	}

	private static final String KEY_PREFIX = "lock:";
	private static final String THREAD_ID_PREFIX = UUID.randomUUID().toString(true) + "-";
	private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

	static {
		UNLOCK_SCRIPT = new DefaultRedisScript<>();
		UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
		UNLOCK_SCRIPT.setResultType(Long.class);
	}

	@Override
	public boolean tryLock(long timeoutSec) {
		// 获取线程标识
		String threadId = THREAD_ID_PREFIX + Thread.currentThread().getId();

		// 获取锁
		Boolean success = stringRedisTemplate
				.opsForValue()
				.setIfAbsent(KEY_PREFIX + businessName, threadId, timeoutSec, TimeUnit.SECONDS);
		return Boolean.TRUE.equals(success);
	}

/*	@Override
	public void unlock() {
		// 获取线程标识
		String threadId = THREAD_ID_PREFIX + Thread.currentThread().getId();
		// 获取锁中的标识
		String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + businessName);
		// 判断是否一致
		if (threadId.equals(id)) {
			// 一致,释放锁
			stringRedisTemplate.delete(KEY_PREFIX + businessName);
		}

	}*/

	@Override
	public void unlock() {
		// 调用lua脚本
		stringRedisTemplate.execute(
				UNLOCK_SCRIPT,
				Collections.singletonList(KEY_PREFIX + businessName),
				THREAD_ID_PREFIX + Thread.currentThread().getId());
	}
}
