package com.yc.springboot.redis.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.yc.springboot.redis.util.RedisUtil;

@RestController
public class GoodsController {
	Logger logger = LoggerFactory.getLogger(GoodsController.class);
	
	@Autowired
	private RedisUtil redisUtil;

	// 总库存
	private long balance = 0;
	
	// 获取锁的超时时间 秒
	private int timeout = 30 * 1000;

	/**
	 * 访问如下测试url，正常来说第一次返回了true，第二次返回了false，由于第二次请求的时候redis的key已存在，所以无法set成功
	 * 127.0.0.1:8888/setnx/orderkey/1001
	 */
	@GetMapping("/setnx/{key}/{val}")
	public boolean setnx(@PathVariable String key, @PathVariable String val) {
		return redisUtil.setnx(key, val);
	}

	@GetMapping("/delnx/{key}/{val}")
	public int delnx(@PathVariable String key, @PathVariable String val) {
		return redisUtil.delnx(key, val);
	}

	/**
	 * 抢单的方法
	 * @return
	 */
	@GetMapping("/qiangdan/{key}")
	public List<String> qiangdan(@PathVariable String key) {
		// 抢到商品的用户
		List<String> shopUsers = new ArrayList<String>();

		// 构造很多用户
		List<String> users = new ArrayList<String>();
		
		IntStream.range(0, 100000).parallel().forEach(b -> {
			users.add("神牛 - " + b);
		});

		// 初始化库存
		balance = 10;

		// 模拟开抢  parallelStream: 并行流模拟多用户抢购
		users.parallelStream().forEach(b -> {
			String shopUser = qiang(key, b);
			if (!StringUtils.isEmpty(shopUser)) {
				shopUsers.add(shopUser);
			}
		});

		return shopUsers;
	}
	
	/**
     * 模拟抢单动作
     * @param b
     * @return
     */
    private String qiang(String goodsKey, String b) {
        //用户开抢时间
        long startTime = System.currentTimeMillis();
        
        // 未抢到的情况下，30秒内继续获取锁
        while ((startTime + timeout) >= System.currentTimeMillis()) { // 判断未抢成功的用户，timeout秒内继续获取锁
            // 商品是否剩余
            if (balance <= 0) {
                break;
            }
            if (redisUtil.setnx(goodsKey, b)) {  // redisUtil.setnx(shangpingKey, b)：用户获取抢购锁
                // 用户b拿到锁
                logger.info("用户{}拿到锁...", b);
                try {
                    // 商品是否剩余
                    if (balance <= 0) {
                        break;
                    }

                    // 模拟生成订单耗时操作，方便查看：神牛-50 多次获取锁记录
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    //抢购成功，商品递减，记录用户
                    balance -= 1;

                    //抢单成功跳出
                    logger.info("用户{}抢单成功跳出...所剩库存：{}", b, balance);
                    return b + "抢单成功，所剩库存：" + balance;
                } finally {
                    logger.info("用户{}释放锁...", b);
                    //释放锁
                    redisUtil.delnx(goodsKey, b); // 获取锁后并下单成功，最后释放锁：redisUtil.delnx(shangpingKey, b)
                }
            } else {
                //用户b没拿到锁，在超时范围内继续请求锁，不需要处理
            }
        }
        return "";
    }
}
