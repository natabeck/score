/*
 * Copyright © 2014-2017 EntIT Software LLC, a Micro Focus company (L.P.)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cloudslang.worker.management.services;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newFixedThreadPool;

public class SimpleRunnableContinuationDelegate implements SimpleRunnableContinuation, DisposableBean {

    private static final Logger logger = Logger.getLogger(SimpleExecutionRunnable.class);

    private final ExecutorService threadPoolExecutor;

    public SimpleRunnableContinuationDelegate() {
        final ThreadFactory delegateThreadPoolFactory = new ThreadFactoryBuilder().setNameFormat("run-continuation-%d")
                .build();
        this.threadPoolExecutor = newFixedThreadPool(1, delegateThreadPoolFactory);
    }

    @Override
    public Future<?> continueAsync(Runnable runnable) {
        try {
            return threadPoolExecutor.submit(runnable);
        } catch (Exception ex) {
            logger.error("Cannot continue : ", ex);
        }
        return null;
    }

    @Override
    public void destroy() throws Exception {
        threadPoolExecutor.shutdown();
        if (!threadPoolExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            logger.info("Did not finish waiting for run-continuation thread task.");
        }
        threadPoolExecutor.shutdownNow();
    }

}