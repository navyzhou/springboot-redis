package com.yc.springboot.redis.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * redis工具类
 * 源辰信息
 * @author navy
 * @2020年8月15日
 */
@Component
public class RedisUtil {
	@Autowired
	private JedisPool jedisPool;

	/**
	 * 对于分布式锁的生成通常需要注意如下几个方面：
	 * 创建锁的策略：redis的普通key一般都允许覆盖，A用户set某个key后，B在set相同的key时同样能成功，如果是锁场景，
	 * 		那就无法知道到底是哪个用户set成功的；这里jedis的setnx方式为我们解决了这个问题，简单原理是：当A用户先set成功了，
	 * 		那B用户set的时候就返回失败，满足了某个时间点只允许一个用户拿到锁。
	 * 锁过期时间：某个抢购场景时候，如果没有过期的概念，当A用户生成了锁，但是后面的流程被阻塞了一直无法释放锁，
	 * 		那其他用户此时获取锁就会一直失败，无法完成抢购的活动；当然正常情况一般都不会阻塞，A用户流程会正常释放锁；过期时间只是为了更有保障。
	 * 下面来上段setnx操作的代码：
	 * NX：是否存在key，存在就不set成功
	 * PX：key过期时间单位设置为毫秒（EX：单位秒）
	 * @param key
	 * @param val
	 * @return
	 */
	public boolean setnx(String key, String val) {
		Jedis jedis = null;
		try {
			jedis = jedisPool.getResource();
			if (jedis == null) {
				return false;
			}
			
			if (jedis.setnx(key, val) > 0) {
				return true;
			}
			
			return false;
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}
		return false;
	}

	/**
	 * 上面是创建锁，同样的具有有效时间，但是我们不能完全依赖这个有效时间，场景如：有效时间设置1分钟，本身用户A获取锁后，没遇到什么特殊情况正常生成了抢购订单后，此时其他用户应该能正常下单了才对，但是由于有个1分钟后锁才能自动释放，那其他用户在这1分钟无法正常下单（因为锁还是A用户的），因此我们需要A用户操作完后，主动去解锁：
	 * @param key
	 * @param val
	 * @return
	 */
	public int delnx(String key, String val) {
		Jedis jedis = null;
		try {
			jedis = jedisPool.getResource();
			if (jedis == null) {
				return 0;
			}
			StringBuilder sbScript = new StringBuilder();
			sbScript.append("if redis.call('get','").append(key).append("')").append("=='").append(val).append("'").
			append(" then ").
			append("    return redis.call('del','").append(key).append("')").
			append(" else ").
			append("    return 0").
			append(" end");

			return Integer.valueOf(jedis.eval(sbScript.toString()).toString());
		} catch (Exception ex) {
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}
		return 0;
	}
}
