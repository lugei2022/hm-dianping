package com.hmdp.utils;

public interface ILock {
	/**
	 * 尝试获取锁
	 *
	 * @param timeoutSec 锁持有的时间,过期自动释放锁
	 * @return true 成功获取锁,false 失败
	 */
	boolean tryLock(long timeoutSec);

	/**
	 * 释放锁
	 */
	void unlock();
}
