/*=============================================================================

  GIFT-Cloud: A data storage and collaboration platform

  Copyright (c) University College London (UCL). All rights reserved.
  Released under the Modified BSD License
  github.com/gift-surg

  Author: Tom Doel
=============================================================================*/

package uk.ac.ucl.cs.cmic.giftcloud.uploader;

import java.util.ArrayList;
import java.util.List;

/**
 * Maintains a list of failures from task processing by (@link BackgroundService)
 * @param <T_taskType>
 */
public class BackgroundServiceFailureList<T_taskType> {

    private final List<FailureRecord> uploadFailures = new ArrayList<FailureRecord>();

    /**
     * Add a new failed task
     *
     * @param task the task which failed
     * @param errorRecord the cumulative error history from attempting to run the tasks
     */
    public final void addFailure(final T_taskType task, final BackgroundServiceErrorRecord errorRecord) {
        uploadFailures.add(new FailureRecord(task, errorRecord));
    }

    /**
     * gets all the failed tasks
     *
     * @return a list of failed tasks
     */
    public final List<FailureRecord> getFailures() {
        return uploadFailures;
    }

    /**
     * returns whether there are any failed tasks
     *
     * @return true if there are any failed tasks
     */
    protected final boolean isEmpty() {
        return uploadFailures.isEmpty();
    }

    /**
     * A record of a failed task
     */
    class FailureRecord {
        private T_taskType task;
        private BackgroundServiceErrorRecord errorRecord;

        FailureRecord(final T_taskType task, final BackgroundServiceErrorRecord errorRecord) {
            this.task = task;
            this.errorRecord = errorRecord;
        }

        public T_taskType getTask() {
            return task;
        }

        public BackgroundServiceErrorRecord getErrorRecord() {
            return errorRecord;
        }
    }

}
