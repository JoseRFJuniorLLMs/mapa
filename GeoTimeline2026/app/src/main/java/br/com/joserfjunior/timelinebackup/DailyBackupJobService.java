package br.com.joserfjunior.timelinebackup;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/** Optional daily backup, enabled only after selecting a destination and explicitly opting in. */
public final class DailyBackupJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                if (getSharedPreferences(DriveFolderBackup.PREFS, 0)
                        .getBoolean(DriveFolderBackup.DAILY_KEY, false)) {
                    DriveFolderBackup.upload(this);
                }
            } catch (Exception exception) {
                Log.e("Timeline2026", "Backup não concluído: verifique pasta e autorização", exception);
            } finally { jobFinished(params, false); }
        }, "timeline-daily-backup").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}