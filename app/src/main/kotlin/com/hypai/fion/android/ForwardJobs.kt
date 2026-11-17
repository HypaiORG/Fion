package com.hypai.fion.android

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CallLog
import com.hypai.fion.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object ForwardJobs {
    private val CALL_LOG_WATCHES = listOf(1, 4)
    private const val RETRY_JOB = 2
    private const val CALL_LOG_SYNC_JOB = 3
    private const val RETRY_BACKOFF_MS = 30_000L
    private const val CALL_LOG_UPDATE_DELAY_MS = 1_000L
    private const val CALL_LOG_MAX_DELAY_MS = 4_000L

    private fun Context.scheduler(): JobScheduler = getSystemService(JobScheduler::class.java)

    private fun Context.watchJob(id: Int): JobInfo {
        val trigger = JobInfo.TriggerContentUri(
            CallLog.Calls.CONTENT_URI,
            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
        )
        return JobInfo.Builder(id, ComponentName(this, CallLogJobService::class.java))
            .addTriggerContentUri(trigger)
            .setTriggerContentUpdateDelay(CALL_LOG_UPDATE_DELAY_MS)
            .setTriggerContentMaxDelay(CALL_LOG_MAX_DELAY_MS)
            .build()
    }

    fun ensureCallLogWatch(context: Context, runningJobId: Int? = null) {
        if (!context.isGranted(Manifest.permission.READ_CALL_LOG)) return
        val scheduler = context.scheduler()
        val idle = CALL_LOG_WATCHES.filter { it != runningJobId }
        if (idle.any { scheduler.getPendingJob(it) != null }) return
        scheduler.schedule(context.watchJob(idle.first()))
    }

    fun syncCallLogAsync(context: Context) {
        if (!context.isGranted(Manifest.permission.READ_CALL_LOG)) return
        val scheduler = context.scheduler()
        if (scheduler.getPendingJob(CALL_LOG_SYNC_JOB) != null) return
        val job = JobInfo.Builder(CALL_LOG_SYNC_JOB, ComponentName(context, CallLogJobService::class.java))
            .setOverrideDeadline(CALL_LOG_MAX_DELAY_MS)
            .setBackoffCriteria(RETRY_BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        scheduler.schedule(job)
    }

    fun cancelCallLogWatch(context: Context) {
        val scheduler = context.scheduler()
        (CALL_LOG_WATCHES + CALL_LOG_SYNC_JOB).forEach(scheduler::cancel)
    }

    fun scheduleRetry(context: Context, replace: Boolean = false) {
        val scheduler = context.scheduler()
        if (!replace && scheduler.getPendingJob(RETRY_JOB) != null) return
        val job = JobInfo.Builder(RETRY_JOB, ComponentName(context, RetryJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(RETRY_BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setPersisted(true)
            .setPriority(JobInfo.PRIORITY_HIGH)
            .build()
        scheduler.schedule(job)
    }

    fun cancelRetry(context: Context) {
        context.scheduler().cancel(RETRY_JOB)
    }
}

class CallLogJobService : JobService() {
    private val running = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val app = appContainer
        running[params.jobId] = app.scope.launch {
            if (app.coordinator.shouldWatchCallLog()) {
                ForwardJobs.ensureCallLogWatch(this@CallLogJobService, runningJobId = params.jobId)
                app.missedCalls.sync()
            } else {
                ForwardJobs.cancelCallLogWatch(this@CallLogJobService)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return params.stopReason != JobParameters.STOP_REASON_CANCELLED_BY_APP
    }
}

class RetryJobService : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = appContainer
        running = app.scope.launch { jobFinished(params, !app.forwarder.retryPending()) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }
}
