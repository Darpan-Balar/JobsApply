package com.jobsapply.adapters;

import com.jobsapply.adapters.http.Sleeper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class RecordingSleeper implements Sleeper {

    public final List<Duration> sleeps = new ArrayList<>();

    @Override
    public void sleep(Duration duration) {
        sleeps.add(duration);
    }
}
