package com.walter.concurrent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.*;

/**
 * 自定义线程池
 * @author walter.tan
 */
@Slf4j
public class CustomThreadPool {
    public static final String DEFAULT_KEY = "default";
    private String key;
    private long timeout;
    private String type;
    private int corePoolSize;
    private int maxPoolSize;
    private long keepAliveTime;
    private boolean fair;
    private int initQueueSize;
    private int showThreadQueueSize;
    private ThreadPoolExecutor taskPool;
    private BlockingQueue<Runnable> queue;
    private ScheduledExecutorService scdTaskPool;

    public CustomThreadPool(ThreadPoolConfigure conf) {
        this(conf, conf.getThreadFactory(), conf.getRejectedExecutionHandler());
    }

    public CustomThreadPool(ThreadPoolConfigure conf, ThreadFactory factory, RejectedExecutionHandler handler) {
        this.key = DEFAULT_KEY;
        this.timeout = 120000L;
        this.type = "1";
        this.corePoolSize = Runtime.getRuntime().availableProcessors();
        this.maxPoolSize = this.corePoolSize * 100;
        this.keepAliveTime = 120L;
        this.fair = false;
        this.initQueueSize = 100;
        this.showThreadQueueSize = 10;

        this.key = !StringUtils.hasText(conf.getKey()) ? this.key : conf.getKey();
        this.timeout = conf.getTimeout() == 0L ? this.timeout : conf.getTimeout();
        this.maxPoolSize = conf.getMaxPoolSize() == 0 ? this.maxPoolSize : conf.getMaxPoolSize();
        this.corePoolSize = conf.getCorePoolSize() == 0 ? this.corePoolSize : conf.getCorePoolSize();
        this.type = !StringUtils.hasText(conf.getType()) ? this.type : conf.getType();
        this.keepAliveTime = conf.getKeepAliveTime() == 0L ? this.keepAliveTime : conf.getKeepAliveTime();
        this.fair = conf.getFair() == null ? this.fair : conf.getFair();
        this.initQueueSize = conf.getInitQueueSize() == 0 ? this.initQueueSize : conf.getInitQueueSize();
        this.showThreadQueueSize = conf.getShowThreadQueueSize();
        this.init(factory, handler);
        log.info("Thread pool: {}, factory.class: {},handler.class:{}", this.toString(), null == factory ? null : factory.getClass(), null == handler ? null : handler.getClass());
    }

    public CustomThreadPool() {
        this.key = DEFAULT_KEY;
        this.timeout = 120000L;
        this.type = "1";
        this.corePoolSize = Runtime.getRuntime().availableProcessors();
        this.maxPoolSize = this.corePoolSize * 100;
        this.keepAliveTime = 120L;
        this.fair = false;
        this.initQueueSize = 100;
        this.showThreadQueueSize = 10;
        this.taskPool = null;
        this.queue = null;
        this.scdTaskPool = null;
        this.init(null, null);
    }

    private void init(ThreadFactory factory, RejectedExecutionHandler handler) {
        if (this.timeout > 0L) {
            this.scdTaskPool = Executors.newScheduledThreadPool(this.corePoolSize);
        }

        this.queue = this.getBlockQueue();
        if (null != factory && null != handler) {
            this.taskPool = new ThreadPoolExecutor(this.corePoolSize, this.maxPoolSize, this.keepAliveTime, TimeUnit.SECONDS, this.queue, factory, handler);
        } else if (null != factory) {
            this.taskPool = new ThreadPoolExecutor(this.corePoolSize, this.maxPoolSize, this.keepAliveTime, TimeUnit.SECONDS, this.queue, factory);
        } else if(null != handler){
            this.taskPool = new ThreadPoolExecutor(this.corePoolSize, this.maxPoolSize, this.keepAliveTime, TimeUnit.SECONDS, this.queue, handler);
        } else {
            this.taskPool = new ThreadPoolExecutor(this.corePoolSize, this.maxPoolSize, this.keepAliveTime, TimeUnit.SECONDS, this.queue);
        }
    }

    public Future<?> execute(Runnable task) {
        Future<?> future = this.taskPool.submit(task);
        int size = this.queue.size();
        if (this.showThreadQueueSize > -1 && size >= this.showThreadQueueSize) {
            log.info("task queue length <" + size + "> key<" + this.key + ">");
        }

        this.isOvertime(future);
        return future;
    }

    public <T> Future<T> submit(Callable<T> task) {
        Future<T> future = this.taskPool.submit(task);
        int size = this.queue.size();
        if (this.showThreadQueueSize > -1 && size >= this.showThreadQueueSize) {
            log.info("task queue length <" + size + "> key<" + this.key + ">");
        }

        this.isOvertime(future);
        return future;
    }

    public String getKey(){
        return this.key;
    }

    private BlockingQueue<Runnable> getBlockQueue() {
        BlockingQueue<Runnable> blockingQueue;
        if (this.type.equals(ThreadQueueType.SynchronousQueueWithFair.getValue())) {
            blockingQueue = new SynchronousQueue(this.fair);
        } else if (this.type.equals(ThreadQueueType.LinkedBlockingQueue.getValue())) {
            blockingQueue = new LinkedBlockingQueue();
        } else if (this.type.equals(ThreadQueueType.LinkedBlockingQueueWithQueueSize.getValue())) {
            blockingQueue = new LinkedBlockingQueue(this.initQueueSize);
        } else {
            blockingQueue = new SynchronousQueue();
        }

        return blockingQueue;
    }

    private void isOvertime(final Future future) {
        if (this.timeout > 0L) {
            this.scdTaskPool.schedule(()->{
                if (!future.isDone()) {
                    future.cancel(true);
                    log.warn("task cancel because out of time: over <" + CustomThreadPool.this.timeout + "ms> key<" + CustomThreadPool.this.key + ">");
                }
            }, this.timeout, TimeUnit.MILLISECONDS);
        }

    }

    public boolean awaitTermination(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return this.taskPool.awaitTermination(timeout, timeUnit);
    }

    public List<Runnable> shutdownNow() throws InterruptedException {
        return this.taskPool.shutdownNow();
    }

    public BlockingQueue<Runnable> getQueue() {
        return this.queue;
    }

    public void setQueue(BlockingQueue<Runnable> queue) {
        this.queue = queue;
    }

    public void setPoolSize(int corePoolSize, int maxPoolSize) {
        this.taskPool.setCorePoolSize(corePoolSize);
        this.taskPool.setMaximumPoolSize(maxPoolSize);
    }

    public int getActiveCount() {
        return this.taskPool.getActiveCount();
    }

    public int getPoolSize() {
        return this.taskPool.getPoolSize();
    }

    public ThreadPoolExecutor getTaskPool() {
        return this.taskPool;
    }

    public void shutdown() {
        this.taskPool.shutdown();
        log.info("CustomThreadPool[" + this.key + "] is shutdown:" + this.taskPool.isShutdown());
    }

    @Override
    public String toString() {
        return "CustomThreadPool{key='" + this.key + '\'' + ", timeout=" + this.timeout + ", type='" + this.type + '\'' + ", corePoolSize=" + this.corePoolSize + ", maxPoolSize=" + this.maxPoolSize + ", keepAliveTime=" + this.keepAliveTime + ", fair=" + this.fair + ", initQueueSize=" + this.initQueueSize + ", taskPool=" + this.taskPool + ", queue=" + this.queue + ", scdTaskPool=" + this.scdTaskPool + '}';
    }
}
