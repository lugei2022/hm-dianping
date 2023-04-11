package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

	private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Override
	public Result queryById(Long id) {

		/*// 1.从redis中查询商城缓存
		String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

		// 2.判断是否存在
		if (StrUtil.isNotBlank(shopJson)) {
			// 3.存在,直接返回
			Shop shop = JSONUtil.toBean(shopJson, Shop.class);
			return Result.ok(shop);
		}

		// 判断是否命中空值
		if (shopJson != null) {
			// 返回一个错误信息
			return Result.fail("店铺信息不存在");
		}

		// 4.不存在,根据id查询数据库
		Shop shop = this.getById(id);

		// 5.数据库中不存在,返回错误信息
		if (shop == null) {
			// 将空值写入redis
			stringRedisTemplate.opsForValue()
					.set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
			return Result.fail("店铺不存在");
		}

		// 6.存在,保存到redis
		stringRedisTemplate.opsForValue()
				.set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
						CACHE_SHOP_TTL + RandomUtil.randomLong(1, 10), TimeUnit.MINUTES);

		// 7.返回
		return Result.ok(shop);*/
		// 缓存穿透
		// Shop shop = this.queryWithPassThrough(id);
		// 互斥锁解决缓存击穿
		// Shop shop = this.queryWithMutex(id);

		// 逻辑过期解决缓存击穿
		Shop shop = this.queryWithLogicalExpire(id);

		if (shop == null) {
			return Result.fail("缓存不存在");
		}
		return Result.ok(shop);

	}

	public Shop queryWithLogicalExpire(Long id) {
		// 1.从redis中查询商铺
		String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
		// 2.判断是否存在
		if (StrUtil.isBlank(shopJson)) {
			// 3.存在
			return null;
		}

		// 4.命中
		RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
		JSONObject data = (JSONObject) redisData.getData();
		Shop shop = JSONUtil.toBean(data, Shop.class);
		LocalDateTime expireTime = redisData.getExpireTime();
		// 5.判断是否过期
		if (expireTime.isAfter(LocalDateTime.now())) {
			// 5.1未过期
			return shop;
		}
		// 5.2已过期

		// 6.缓存重建
		// 6.1 获取互斥锁
		boolean isLock = this.tryLock(LOCK_SHOP_KEY + id);
		// 6.2是否成功
		if (isLock) {
			// 6.3成功,开启独立线程,实现缓存重建
			CACHE_REBUILD_EXECUTOR.submit(() -> {
				try {
					this.saveShop2Redis(id, 20L);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} finally {
					this.unlock(LOCK_SHOP_KEY + id);
				}
			});
		}
		return shop;

	}

	private Shop queryWithMutex(Long id) {

		// 1.从redis中查询商城缓存
		String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

		// 2.判断是否存在
		if (StrUtil.isNotBlank(shopJson)) {
			// 3.存在,直接返回
			return JSONUtil.toBean(shopJson, Shop.class);
		}

		// 判断是否命中空值
		if (shopJson != null) {
			// 返回一个错误信息
			return null;
		}

		// 4.实现缓存重建
		// 4.1 获取互斥锁
		String lockKey = LOCK_SHOP_KEY + id;
		Shop shop = null;
		try {
			boolean isLock = this.tryLock(lockKey);
			// 4.2 判断是否获取成功
			if (!isLock) {
				// 4.3 失败则休眠并重试
				Thread.sleep(50);
				return this.queryWithMutex(id);
			}
			// 4.4不存在,根据id查询数据库
			shop = this.getById(id);
			Thread.sleep(200);

			// 5.数据库中不存在,返回错误信息
			if (shop == null) {
				// 将空值写入redis
				stringRedisTemplate.opsForValue()
						.set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
				return null;
			}

			// 6.存在,保存到redis
			stringRedisTemplate.opsForValue()
					.set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
							CACHE_SHOP_TTL + RandomUtil.randomLong(1, 10), TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			unlock(lockKey);
		}
		// 8.返回
		return shop;
	}

	private Shop queryWithPassThrough(Long id) {

		// 1.从redis中查询商城缓存
		String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

		// 2.判断是否存在
		if (StrUtil.isNotBlank(shopJson)) {
			// 3.存在,直接返回
			return JSONUtil.toBean(shopJson, Shop.class);
		}

		// 判断是否命中空值
		if (shopJson != null) {
			// 返回一个错误信息
			return null;
		}

		// 4.不存在,根据id查询数据库
		Shop shop = this.getById(id);

		// 5.数据库中不存在,返回错误信息
		if (shop == null) {
			// 将空值写入redis
			stringRedisTemplate.opsForValue()
					.set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
			return null;
		}

		// 6.存在,保存到redis
		stringRedisTemplate.opsForValue()
				.set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
						CACHE_SHOP_TTL + RandomUtil.randomLong(1, 10), TimeUnit.MINUTES);

		// 7.返回
		return shop;
	}

	private boolean tryLock(String key) {
		Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
		return BooleanUtil.isTrue(flag);
	}

	private void unlock(String key) {
		stringRedisTemplate.delete(key);
	}

	public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
		// 1.查询店铺数据
		Shop shop = this.getById(id);
		Thread.sleep(200);
		// 2.封装逻辑过期时间
		RedisData redisData = new RedisData();
		redisData.setData(shop);
		redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
		// 3.写入Redis
		stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
	}

	@Override
	@Transactional
	public Result update(Shop shop) {
		Long id = shop.getId();
		if (id == null) {
			return Result.fail("店铺id为空,无法查找");
		}

		// 1.更新数据库
		this.updateById(shop);
		// 2.删除缓存
		stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

		return Result.ok();
	}
}
