package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
	private String businessName;
	private StringRedisTemplate stringRedisTemplate;

	public SimpleRedisLock(String businessName, StringRedisTemplate stringRedisTemplate) {
		this.businessName = businessName;
		this.stringRedisTemplate = stringRedisTemplate;
	}

	private static final String KEY_PREFIX = "lock:";

	@Override
	public boolean tryLock(long timeoutSec) {
		// 获取线程标识
		long threadId = Thread.currentThread().getId();

		// 获取锁
		Boolean success = stringRedisTemplate
				.opsForValue()
				.setIfAbsent(KEY_PREFIX + businessName, threadId + "", timeoutSec, TimeUnit.SECONDS);
		return Boolean.TRUE.equals(success);
	}

	@Override
	public void unlock() {
		stringRedisTemplate.delete(KEY_PREFIX + businessName);
	}
}
