/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.markup;

import java.util.concurrent.TimeUnit;

public class TimeUtil {
    public static String formatMillisIntoMinutesAndSeconds(long millis) {
        return String.format("%d min %d sec",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }
}
