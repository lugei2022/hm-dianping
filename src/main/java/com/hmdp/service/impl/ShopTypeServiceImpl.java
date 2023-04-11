package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Override
	public Result querySort() {
		// 1.从redis中查询商城缓存
		String jsonList = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

		// 2.判断是否存在
		if (StrUtil.isNotBlank(jsonList)) {
			// 3.存在
			List<ShopType> shopTypeList = JSONUtil.toList(jsonList, ShopType.class);
			return Result.ok(shopTypeList);
		}
		// 4.不存在
		List<ShopType> shopTypeList = this.query().orderByAsc("sort").list();

		System.out.println(shopTypeList);

		// 加入redis缓存
		stringRedisTemplate.opsForValue()
				.set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypeList),
						CACHE_SHOP_TYPE_TTL + RandomUtil.randomLong(1, 10), TimeUnit.MINUTES);
		return Result.ok(shopTypeList);
	}
}
