package com.shiftcal.widget;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Fires when anything in the phone's calendar changes, redraws the widgets and re-arms itself. */
public class UpdateJob extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        ShiftWidget.refreshAll(this);
        ShiftWidget.schedule(this, true); // content-trigger jobs fire once, so re-arm
        return false;
    }
    @Override public boolean onStopJob(JobParameters params) { return false; }
}
