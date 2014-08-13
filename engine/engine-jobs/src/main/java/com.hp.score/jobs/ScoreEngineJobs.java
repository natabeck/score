package com.hp.score.jobs;

import com.hp.oo.engine.queue.services.CounterNames;
import com.hp.oo.engine.queue.services.cleaner.QueueCleanerService;
import com.hp.oo.engine.queue.services.recovery.ExecutionRecoveryService;
import com.hp.oo.engine.versioning.services.VersionService;
import com.hp.oo.orchestrator.services.SplitJoinService;
import com.hp.oo.partitions.services.PartitionTemplate;
import org.apache.commons.lang.time.StopWatch;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * This class will unite all periodic jobs needed by the score engine, to be triggered by a scheduler .
 * User: wahnonm
 * Date: 12/08/14
 * Time: 16:18
 */
public class ScoreEngineJobs {

    @Autowired
    private QueueCleanerService queueCleanerService;

    @Autowired
    private SplitJoinService splitJoinService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private ExecutionRecoveryService executionRecoveryService;

    @Autowired
    @Qualifier("OO_EXECUTION_STATES")
    private PartitionTemplate execStatesPartitionTemplate;

    private final Logger logger = Logger.getLogger(getClass());

    final private int QUEUE_BULK_SIZE = 500;

    private final Integer SPLIT_JOIN_BULK_SIZE = Integer.getInteger("splitjoin.job.bulk.size", 25);

    private final Integer SPLIT_JOIN_ITERATIONS = Integer.getInteger("splitjoin.job.iterations", 20);

    /**
     * Job that will handle the cleaning of queue table.
     */
    public void cleanQueueJob(){
        try {
            Set<Long> ids = queueCleanerService.getFinishedExecStateIds();
            if(logger.isDebugEnabled()) logger.debug("Will clean from queue the next Exec state ids amount:"+ids.size());

            Set<Long> execIds = new HashSet<>();

            for (Long id : ids) {
                execIds.add(id);
                if (execIds.size() >= QUEUE_BULK_SIZE) {
                    queueCleanerService.cleanFinishedSteps(execIds);
                    execIds.clear();
                }
            }

            if (execIds.size() > 0) {
                queueCleanerService.cleanFinishedSteps(execIds);
            }
        } catch (Exception e) {
            logger.error("Can't run queue cleaner job.", e);
        }
    }

    /**
     * Job that will handle the joining of finished branches.
     */
    public void joinFinishedSplitsJob(){
        try {
            if (logger.isDebugEnabled()) logger.debug("SplitJoinJob woke up at " + new Date());
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            // try sequentially at most 'ITERATIONS' attempts
            // quit when there aren't any more results to process
            boolean moreToJoin = true;
            for (int i = 0; i < SPLIT_JOIN_ITERATIONS && moreToJoin; i++) {
                int joinedSplits = splitJoinService.joinFinishedSplits(SPLIT_JOIN_BULK_SIZE);
                moreToJoin = (joinedSplits == SPLIT_JOIN_BULK_SIZE);
            }

            stopWatch.stop();
            if (logger.isDebugEnabled()) logger.debug("finished SplitJoinJob in " + stopWatch);
        } catch (Exception ex) {
            logger.error("SplitJoinJob failed", ex);
        }
    }

    /**
     * Job that will handle the rolling of Execution states rolling tables.
     */
    public void statesRollingJob(){
        execStatesPartitionTemplate.onRolling();
    }

    /**
     * Job that will increment the recovery version
     */
    public void recoveryVersionJob(){
        logger.debug("increment MSG_RECOVERY_VERSION Version");

        versionService.incrementVersion(CounterNames.MSG_RECOVERY_VERSION.name());
    }

    /**
     * Job to execute the recovery check.
     */
    public void executionRecoveryJob(){
        if (logger.isDebugEnabled()) {
            logger.debug("ExecutionRecoveryJob woke up at " + new Date());
        }

        try {
            executionRecoveryService.doRecovery();
        }
        catch (Exception e){
            logger.error("Can't run queue recovery job.",e);
        }
    }

}
