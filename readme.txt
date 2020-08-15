SpringBoot + Redis 分布式锁：模拟抢单
本篇内容主要讲解的是redis分布式锁，这个在各大厂面试几乎都是必备的，下面结合模拟抢单的场景来使用她；
本篇不涉及到的redis环境搭建，快速搭建个人测试环境，这里建议使用docker；本篇内容节点如下：
	jedis的nx生成锁
	如何删除锁
	模拟抢单动作(10w个人开抢)
	jedis的nx生成锁
	对于java中想操作redis，好的方式是使用jedis，首先pom中引入依赖：
	<dependency>
    	<groupId>redis.clients</groupId>
    	<artifactId>jedis</artifactId>
	</dependency>
		
对于分布式锁的生成通常需要注意如下几个方面：
	创建锁的策略：redis的普通key一般都允许覆盖，A用户set某个key后，B在set相同的key时同样能成功，如果是锁场景，那就无法知道到底是哪个用户set成功的；
		这里jedis的setnx方式为我们解决了这个问题，简单原理是：当A用户先set成功了，那B用户set的时候就返回失败，满足了某个时间点只允许一个用户拿到锁。

	锁过期时间：某个抢购场景时候，如果没有过期的概念，当A用户生成了锁，但是后面的流程被阻塞了一直无法释放锁，那其他用户此时获取锁就会一直失败，无法完成抢购的活动；
		当然正常情况一般都不会阻塞，A用户流程会正常释放锁；过期时间只是为了更有保障。
	下面来上段setnx操作的代码：
		public boolean setnx(String key, String val) {
	        Jedis jedis = null;
	        try {
	            jedis = jedisPool.getResource();
	            if (jedis == null) {
	                return false;
	            }
	            return jedis.set(key, val, "NX", "PX", 1000 * 60).
	                    equalsIgnoreCase("ok");
	        } catch (Exception ex) {
	        } finally {
	            if (jedis != null) {
	                jedis.close();
	            }
	        }
	        return false;
	    }
	    
	    这里注意点在于jedis的set方法，其参数的说明如：
		NX：是否存在key，存在就不set成功
		PX：key过期时间单位设置为毫秒（EX：单位秒）
		setnx如果失败直接封装返回false即可，下面我们通过一个get方式的api来调用下这个setnx方法：

	    