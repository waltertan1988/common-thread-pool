package com.walter.concurrent.test;

import com.walter.concurrent.core.ThreadPoolFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author walter.tan
 */
@Slf4j
public class CustomThreadPoolTest extends BaseTests{

    @Autowired
    private ThreadPoolFactory threadPoolFactory;

    @Test
    public void execute(){
        log.info("main thread start");

        for (int i = 0; i < 10; i++) {
            threadPoolFactory.getDefaultThreadPool().execute(() -> {
                log.info("sub thread is running...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        log.info("main thread end");
    }
}
