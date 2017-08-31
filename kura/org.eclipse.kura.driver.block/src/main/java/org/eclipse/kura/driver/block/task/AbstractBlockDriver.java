/*******************************************************************************
 * Copyright (c) 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/

package org.eclipse.kura.driver.block.task;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraRuntimeException;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.ChannelStatus;
import org.eclipse.kura.channel.listener.ChannelListener;
import org.eclipse.kura.driver.Driver;
import org.eclipse.kura.driver.PreparedRead;
import org.eclipse.kura.driver.block.Block;
import org.eclipse.kura.driver.block.BlockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Provides an helper class that can be extended for implementing a {@link Driver} that supports the aggregation of
 * requests using a {@link BlockTaskAggregator}.
 * </p>
 * <p>
 * This class introduces the concept of domain. In general tasks that operate on different domains cannot be aggregated
 * together. Examples of such domains can be a (unit id, function code) pair in the Modbus protocol or a data block
 * (DB) in the case of the S7Comm protocol.
 * </p>
 * <p>
 * Implementors of this class must provide an implementation for the
 * {@link #getTaskFactoryForDomain(Object, Mode)} and {@link #toTasks(List, Mode)}
 * methods, see the description of the two methods for more details:
 * </p>
 * <ul>
 * <li>{@link #getTaskFactoryForDomain(Object, Mode)} must provide a {@link BlockFactory} that can be used for creating
 * {@link ToplevelBlockTask} instances responsible of implementing the data transfer operations for the specified
 * domain</li>
 * <li>{@link #toTasks(List, Mode)} must convert the configuration contained in the provided list of
 * {@link ChannelRecord} instances into a {@link Stream} of (domain, {@link BlockTask}) pairs.</li>
 * </ul>
 * <p>
 * This class provides a default implementation for the {@link #read(List)}, {@link #write(List)} and
 * {@link #prepareRead(List)} methods of the {@link Driver} interface.
 * </p>
 *
 * @param <T>
 *            the type of the domain, can be any type suitable for being used as an {@link HashMap} key
 */
public abstract class AbstractBlockDriver<T> implements Driver {

    private static final Logger logger = LoggerFactory.getLogger(AbstractBlockDriver.class);

    /**
     * This method must provide a {@link BlockFactory} that can be used for creating {@link ToplevelBlockTask} instances
     * responsible of implementing the I/O operations for the specified domain.
     *
     * @param domain
     *            the domain
     * @param mode
     *            the {@link Mode} for the returned {@link ToplevelBlockTask}
     * @return the created {@link ToplevelBlockTask}
     */
    protected abstract BlockFactory<ToplevelBlockTask> getTaskFactoryForDomain(T domain, Mode mode);

    /**
     * <p>
     * This method must return a {@link Stream} that yields (domain, {@link BlockTask}) pairs obtained by converting the
     * {@link ChannelRecord} instances contained in the provided list.
     * </p>
     * <p>
     * The {@link BlockTask} instances obtained from the stream will be aggregated and assigned to a
     * {@link ToplevelBlockTask} parent. Tasks generated by the {@link Stream} returned by this method should not
     * generally perform I/O operation but use the {@link Buffer} of their parent to implement their operations instead.
     * </p>
     * <p>
     * If the specified mode is {@link Mode#READ}, only tasks having {@link Mode#READ} must be produces, if the
     * specified mode is {@link Mode#WRITE}, the returned stream is allowed to produce tasks that are either in
     * {@link Mode#WRITE} or {@link Mode#UPDATE} modes.
     * </p>
     *
     *
     * @param records
     *            the list of {@link ChannelRecord} instances to be converted into {@link BlockTask} instances
     * @param mode
     *            the mode, can be either {@link Mode#READ} or {@link Mode#WRITE}
     * @return a {@link Stream} yielding the converted tasks
     */
    protected abstract Stream<Pair<T, BlockTask>> toTasks(List<ChannelRecord> records, Mode mode);

    /**
     * Returns the minimum gap size parameter that will be used to aggregate tasks in {@link Mode#READ} for the
     * specified domain. The default is 0. Tasks in {@link Mode#WRITE} mode will always be aggregated using 0 as the
     * value of the {@code minimumGapSize} parameter.
     *
     * @param domain
     *            the domain
     * @return the minimum gap size for the provided domain
     */
    protected int getReadMinimumGapSizeForDomain(T domain) {
        return 0;
    }

    /**
     * This method is called immediately before an aggregation is performed for the specific domain and mode. This
     * method can be overridden by implementors in order to customize the {@link BlockTaskAggregator} provided as
     * argument, for example by adding prohibited blocks. The default implementation is no-op.
     *
     * @param domain
     *            the domain
     * @param mode
     *            the {@link Mode} of the tasks to be aggregated, can be either {@link Mode#READ} or {@link Mode#WRITE}
     * @param aggregator
     *            the {@link BlockTaskAggregator} that will perform the aggregation
     */
    protected void beforeAggregation(T domain, Mode mode, BlockTaskAggregator aggregator) {
    }

    /**
     * Perform the following operations:
     * <ol>
     * <li>The provided list of {@link ChannelRecods} instances are converted into {@link BlockTask} instances using the
     * {@link #toTasks(List, Mode)} method.</li>
     * <li>The resulting {@link BlockTask} instances are grouped by domain.</li>
     * <li>An aggregation process is performed for each domain. If the {@link Mode#WRITE} is provided as parameter, and
     * tasks in {@link Mode#UPDATE} mode are found in a domain, the aggregation will be performed using a
     * {@link UpdateBlockTaskAggregator}. Otherwise a {@link BlockTaskAggregator} will be used.<br>
     * The {@link BlockFactory} instances used for the aggregation will be obtained using the
     * {@link #getTaskFactoryForDomain(Object, Mode)} method.</li>
     * <li>The {@link ToplevelBlockTask} instances for all domains will be returned in the result list.</li>
     * </ol>
     *
     * @param records
     *            the {@link ChannelRecord} instances to be converted to {@link BlockTask} instances.
     * @param mode
     *            the mode
     * @return the list of {@link BlockTask} instances resulting from the aggregation.
     * @throws KuraException
     *             if any exception is thrown during the process
     */
    protected List<BlockTask> optimize(List<ChannelRecord> records, Mode mode) throws KuraException {
        try {
            final ArrayList<BlockTask> resultTasks = new ArrayList<>();
            final HashSet<T> domainsWithUpdateTasks = new HashSet<>();

            final Function<Pair<T, BlockTask>, T> classifier;
            if (mode == Mode.READ) {
                classifier = pair -> pair.first;
            } else {
                classifier = pair -> {
                    if (pair.second.getMode() == Mode.UPDATE) {
                        domainsWithUpdateTasks.add(pair.first);
                    }
                    return pair.first;
                };
            }

            final Map<T, ArrayList<Block>> groupedTasks = toTasks(records, mode).collect(Collectors.groupingBy(
                    classifier, Collectors.mapping(pair -> pair.second, Collectors.toCollection(ArrayList::new))));

            groupedTasks.entrySet().forEach(entry -> {
                final T domain = entry.getKey();
                final BlockTaskAggregator aggregator;
                if (domainsWithUpdateTasks.contains(domain)) {
                    aggregator = new UpdateBlockTaskAggregator(entry.getValue(),
                            getTaskFactoryForDomain(domain, Mode.READ), getTaskFactoryForDomain(domain, Mode.WRITE));
                    aggregator.setMinimumGapSize(getReadMinimumGapSizeForDomain(domain));
                } else {
                    aggregator = new BlockTaskAggregator(entry.getValue(), getTaskFactoryForDomain(domain, mode));
                    if (mode == Mode.READ) {
                        aggregator.setMinimumGapSize(getReadMinimumGapSizeForDomain(domain));
                    }
                }
                beforeAggregation(domain, mode, aggregator);
                aggregator.stream().forEach(resultTasks::add);
            });

            return resultTasks;
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.INVALID_PARAMETER, e);
        }
    }

    /**
     * Executes the provided {@link BlockTask}. Implementors can override this method, for example for catching any
     * exception thrown by the task and implement error handling.
     *
     * @param task
     *            the {@link BlockTask} to be run
     */
    protected void runTask(BlockTask task) {
        try {
            task.run();
        } catch (Exception e) {
            logger.warn("Task execution failed", e);
        }
    }

    @Override
    public void registerChannelListener(final Map<String, Object> channelConfig, final ChannelListener listener)
            throws ConnectionException {
        throw new KuraRuntimeException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    @Override
    public void unregisterChannelListener(final ChannelListener listener) throws ConnectionException {
        throw new KuraRuntimeException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    @Override
    public synchronized void read(final List<ChannelRecord> records) throws ConnectionException {
        connect();
        try {
            optimize(records, Mode.READ).forEach(this::runTask);
        } catch (Exception e) {
            logger.warn("Unexpected exception during read", e);
            for (ChannelRecord record : records) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
            }
        }
    }

    @Override
    public synchronized void write(final List<ChannelRecord> records) throws ConnectionException {
        connect();
        try {
            optimize(records, Mode.WRITE).forEach(this::runTask);
        } catch (Exception e) {
            logger.warn("Unexpected exception during write", e);
            for (ChannelRecord record : records) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
            }
        }
    }

    protected PreparedRead createPreparedRead(List<ChannelRecord> records, List<BlockTask> tasks) {
        return new BlockPreparedRead(records, tasks);
    }

    @Override
    public synchronized PreparedRead prepareRead(List<ChannelRecord> records) {
        try {
            return createPreparedRead(records, optimize(records, Mode.READ));
        } catch (KuraException e) {
            for (ChannelRecord record : records) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
            }
            return createPreparedRead(records, Collections.emptyList());
        }
    }

    public class BlockPreparedRead implements PreparedRead {

        private final List<ChannelRecord> records;
        private final List<BlockTask> tasks;

        public BlockPreparedRead(List<ChannelRecord> records, List<BlockTask> tasks) {
            this.records = records;
            this.tasks = tasks;
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public List<ChannelRecord> execute() throws ConnectionException, KuraException {
            synchronized (AbstractBlockDriver.this) {
                connect();
                for (BlockTask task : this.tasks) {
                    runTask(task);
                }
                return this.records;
            }
        }

        @Override
        public List<ChannelRecord> getChannelRecords() {
            return this.records;
        }

    }

    public static final class Pair<U, V> {

        private final U first;
        private final V second;

        public Pair(U first, V second) {
            requireNonNull(first);
            requireNonNull(second);
            this.first = first;
            this.second = second;
        }

        public U getFirst() {
            return first;
        }

        public V getSecond() {
            return second;
        }
    }
}
