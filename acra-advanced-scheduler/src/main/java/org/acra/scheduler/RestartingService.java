package org.acra.scheduler;

import android.app.Activity;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import org.acra.ACRA;

/**
 * @author Lukas
 * @since 31.12.2018
 */
public class RestartingService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        String className = params.getExtras().getString(RestartingAdministrator.EXTRA_LAST_ACTIVITY);
        if (className != null) {
            try {
                //noinspection unchecked
                Class<? extends Activity> activityClass = (Class<? extends Activity>) Class.forName(className);
                final Intent intent = new Intent(this, activityClass);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                ACRA.log.w(ACRA.LOG_TAG, "Unable to find activity class" + className);
            }
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
