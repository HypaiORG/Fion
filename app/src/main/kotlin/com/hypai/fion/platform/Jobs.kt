package com.hypai.fion.platform

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobWorkItem
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import com.hypai.fion.graph
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object Jobs {
    private const val CALL_LOG_WATCH_JOB = 1
    private const val RETRY_JOB = 2
    private const val CALL_LOG_SYNC_JOB = 3
    private const val RETRY_BACKOFF_MS = 30_000L

    private fun Context.scheduler(): JobScheduler = getSystemService(JobScheduler::class.java)

    private fun Context.callLogJob(id: Int): JobInfo.Builder =
        JobInfo.Builder(id, ComponentName(this, CallLogJobService::class.java))

    fun watchCallLog(context: Context) {
        if (!context.isGranted(Manifest.permission.READ_CALL_LOG)) return
        val trigger = JobInfo.TriggerContentUri(
            CallLog.Calls.CONTENT_URI,
            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
        )
        val job = context.callLogJob(CALL_LOG_WATCH_JOB)
            .addTriggerContentUri(trigger)
            .setTriggerContentUpdateDelay(500)
            .setTriggerContentMaxDelay(3_000)
            .build()
        context.scheduler().schedule(job)
    }

    fun syncCallLogSoon(context: Context) {
        context.scheduler().schedule(context.callLogJob(CALL_LOG_SYNC_JOB).setOverrideDeadline(0).build())
    }

    fun scheduleRetry(context: Context) {
        val job = JobInfo.Builder(RETRY_JOB, ComponentName(context, RetryJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(RETRY_BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        context.scheduler().enqueue(job, JobWorkItem(Intent()))
    }
}

class CallLogJobService : JobService() {
    private val running = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = graph.scope.launch {
            graph.missedCalls.sync()
            jobFinished(params, false)
            Jobs.watchCallLog(this@CallLogJobService)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        if (params.stopReason != JobParameters.STOP_REASON_CANCELLED_BY_APP) {
            Jobs.watchCallLog(this)
            Jobs.syncCallLogSoon(this)
        }
        return false
    }
}

class RetryJobService : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = graph.scope.launch {
            var batch = generateSequence { params.dequeueWork() }.toList()
            while (batch.isNotEmpty()) {
                if (!graph.forwarder.retryPending()) {
                    jobFinished(params, true)
                    return@launch
                }
                batch.forEach(params::completeWork)
                batch = generateSequence { params.dequeueWork() }.toList()
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }
}
