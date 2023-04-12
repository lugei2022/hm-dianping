package com.hmdp.utils;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * id自增器（雪花算法）
 */
public class SnowflakeIdWorker {
	private static final long twepoch = 1681226376104L;
	// 机器标识位数
	private static final long workerIdBits = 5L;
	// 数据中心标识位数
	private static final long datacenterIdBits = 5L;

	// 毫秒内自增位数
	private static final long sequenceBits = 12L;
	// 机器ID偏左移12位
	private static final long workerIdShift = sequenceBits;
	// 数据中心ID左移17位
	private static final long datacenterIdShift = sequenceBits + workerIdBits;
	// 时间毫秒左移22位
	private static final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
	// sequence掩码，确保sequence不会超出上限
	private static final long sequenceMask = ~(-1L << sequenceBits);
	// 上次时间戳
	private long lastTimestamp = -1L;
	// 序列
	private long sequence = 0L;
	// 服务器ID
	private long workerId = 1L;
	private static final long workerMask = ~(-1L << workerIdBits);
	// 进程编码
	private long processId = 1L;
	private static final long processMask = ~(-1L << datacenterIdBits);

	private static SnowflakeIdWorker snowFlake = null;

	static {
		snowFlake = new SnowflakeIdWorker();
	}

	public static synchronized long nextId() {
		return snowFlake.getNextId();
	}

	private SnowflakeIdWorker() {

		// 获取机器编码
		this.workerId = this.getMachineNum();
		// 获取进程编码
		RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		this.processId = Long.parseLong(runtimeMXBean.getName().split("@")[0]);

		// 避免编码超出最大值
		this.workerId = workerId & workerMask;
		this.processId = processId & processMask;
	}

	public synchronized long getNextId() {
		// 获取时间戳
		long timestamp = timeGen();
		// 如果时间戳小于上次时间戳则报错
		if (timestamp < lastTimestamp) {
			try {
				throw new Exception(
						"Clock moved backwards.  Refusing to generate id for " + (lastTimestamp - timestamp) + " milliseconds");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// 如果时间戳与上次时间戳相同
		if (lastTimestamp == timestamp) {
			// 当前毫秒内，则+1，与sequenceMask确保sequence不会超出上限
			sequence = (sequence + 1) & sequenceMask;
			if (sequence == 0) {
				// 当前毫秒内计数满了，则等待下一秒
				timestamp = tilNextMillis(lastTimestamp);
			}
		} else {
			sequence = 0;
		}
		lastTimestamp = timestamp;
		// ID偏移组合生成最终的ID，并返回ID
		return ((timestamp - twepoch) << timestampLeftShift) | (processId << datacenterIdShift) | (workerId << workerIdShift) | sequence;
	}

	private long tilNextMillis(final long lastTimestamp) {
		long timestamp = this.timeGen();
		while (timestamp <= lastTimestamp) {
			timestamp = this.timeGen();
		}
		return timestamp;
	}

	private long timeGen() {
		return System.currentTimeMillis();
	}

	private long getMachineNum() {
		long machinePiece;
		StringBuilder sb = new StringBuilder();
		Enumeration<NetworkInterface> e = null;
		try {
			e = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		while (e.hasMoreElements()) {
			NetworkInterface ni = e.nextElement();
			sb.append(ni.toString());
		}
		machinePiece = sb.toString().hashCode();
		return machinePiece;
	}

	public static void main(String[] args) {
		System.out.println(SnowflakeIdWorker.nextId());
	}
}