/*
 * This file is part of OpenTSDB.
 * Copyright (C) 2021 Yahoo.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.opentsdb.horizon.alerts.store;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.google.common.collect.Lists;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import net.opentsdb.configuration.Configuration;
import net.opentsdb.configuration.UnitTestConfiguration;
import net.opentsdb.core.Registry;
import net.opentsdb.core.TSDB;
import net.opentsdb.query.QueryContext;
import net.opentsdb.stats.BlackholeStatsCollector;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.threadpools.TSDBThreadPoolExecutor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class MockTSDB implements TSDB {
    public UnitTestConfiguration config;
    public Registry registry;
    public BlackholeStatsCollector stats;
    public FakeTaskTimer maint_timer;
    public FakeTaskTimer query_timer;
    public TSDBThreadPoolExecutor query_pool;
    public List<Runnable> runnables;

    public MockTSDB() {
        config = (UnitTestConfiguration) UnitTestConfiguration.getConfiguration();
        registry = mock(Registry.class);
        stats = new BlackholeStatsCollector();
        maint_timer = spy(new FakeTaskTimer());
        maint_timer.multi_task = true;
        query_timer = spy(new FakeTaskTimer());
        query_timer.multi_task = true;
        query_pool = mock(TSDBThreadPoolExecutor.class);
        runnables = Lists.newArrayList();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                runnables.add((Runnable) invocation.getArguments()[0]);
                return null;
            }
        }).when(query_pool).submit(any(Runnable.class));
    }

    @Override
    public Configuration getConfig() {
        return config;
    }

    @Override
    public Registry getRegistry() {
        return registry;
    }

    @Override
    public StatsCollector getStatsCollector() {
        return stats;
    }

    @Override
    public Timer getMaintenanceTimer() {
        return maint_timer;
    }

    @Override
    public TSDBThreadPoolExecutor getQueryThreadPool() {
        return query_pool;
    }

    @Override
    public ExecutorService quickWorkPool() {
        return null;
    }

    @Override
    public Timer getQueryTimer() {
        return query_timer;
    }

    @Override
    public boolean registerRunningQuery(long l, QueryContext queryContext) {
        return false;
    }

    @Override
    public boolean completeRunningQuery(long l) {
        return false;
    }

    public static class FakeTaskTimer extends HashedWheelTimer {
        public boolean multi_task;
        public TimerTask newPausedTask = null;
        public TimerTask pausedTask = null;
        public Timeout timeout = null;

        @Override
        public synchronized Timeout newTimeout(final TimerTask task,
                                               final long delay,
                                               final TimeUnit unit) {
            if (pausedTask == null) {
                pausedTask = task;
            } else if (newPausedTask == null) {
                newPausedTask = task;
            } else if (!multi_task) {
                throw new IllegalStateException("Cannot Pause Two Timer Tasks");
            }
            timeout = mock(Timeout.class);
            return timeout;
        }

        @Override
        public Set<Timeout> stop() {
            return null;
        }

        public boolean continuePausedTask() {
            if (pausedTask == null) {
                return false;
            }
            try {
                if (!multi_task && newPausedTask != null) {
                    throw new IllegalStateException("Cannot be in this state");
                }
                pausedTask.run(null);  // Argument never used in this code base
                pausedTask = newPausedTask;
                newPausedTask = null;
                return true;
            } catch (Exception e) {
                throw new RuntimeException("Timer task failed: " + pausedTask, e);
            }
        }
    }
}


