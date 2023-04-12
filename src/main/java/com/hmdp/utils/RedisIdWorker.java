package com.hmdp.utils;

import cn.hutool.core.lang.Snowflake;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
	// 开始时间戳
	private static final long BEGIN_TIMESTAMP = 1672531200L;

	// 序列化的比特位数
	private static final long ID_BITS = 32;

	private StringRedisTemplate stringRedisTemplate;

	public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}

	public Long nextWithRedisId(String keyPrefix) {
		// 1.生成时间戳
		LocalDateTime now = LocalDateTime.now();
		long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
		long timestamp = nowSecond - BEGIN_TIMESTAMP;
		String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

		// 2.生成序列号
		long id = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

		// 3.拼接并返回
		return timestamp << ID_BITS | id;
	}

	public static void main(String[] args) {
		System.out.println(System.currentTimeMillis());

	}

}
