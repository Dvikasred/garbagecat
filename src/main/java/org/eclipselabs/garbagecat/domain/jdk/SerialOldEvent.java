/**********************************************************************************************************************
 * garbagecat                                                                                                         *
 *                                                                                                                    *
 * Copyright (c) 2008-2016 Red Hat, Inc.                                                                              *
 *                                                                                                                    * 
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse *
 * Public License v1.0 which accompanies this distribution, and is available at                                       *
 * http://www.eclipse.org/legal/epl-v10.html.                                                                         *
 *                                                                                                                    *
 * Contributors:                                                                                                      *
 *    Red Hat, Inc. - initial API and implementation                                                                  *
 *********************************************************************************************************************/
package org.eclipselabs.garbagecat.domain.jdk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipselabs.garbagecat.domain.BlockingEvent;
import org.eclipselabs.garbagecat.domain.OldCollection;
import org.eclipselabs.garbagecat.domain.OldData;
import org.eclipselabs.garbagecat.domain.PermCollection;
import org.eclipselabs.garbagecat.domain.PermData;
import org.eclipselabs.garbagecat.domain.TriggerData;
import org.eclipselabs.garbagecat.domain.YoungCollection;
import org.eclipselabs.garbagecat.domain.YoungData;
import org.eclipselabs.garbagecat.util.jdk.JdkMath;
import org.eclipselabs.garbagecat.util.jdk.JdkRegEx;
import org.eclipselabs.garbagecat.util.jdk.JdkUtil;

/**
 * <p>
 * SERIAL_OLD
 * </p>
 * 
 * <p>
 * Enabled with the <code>-XX:+UseSerialGC</code> JVM option. Uses a mark-sweep-compact algorithm.
 * </p>
 * 
 * <h3>Example Logging</h3>
 * 
 * <p>
 * 1) Standard format:
 * </p>
 * 
 * <pre>
 * 187.159: [Full GC 187.160: [Tenured: 97171K-&gt;102832K(815616K), 0.6977443 secs] 152213K-&gt;102832K(907328K), [Perm : 49152K-&gt;49154K(49158K)], 0.6929258 secs]
 * </pre>
 * 
 * <p>
 * 2) JDK 1.6 format (Note "Full GC" vs. "Full GC (System)"):
 * </p>
 * 
 * <pre>
 * 2.457: [Full GC (System) 2.457: [Tenured: 1092K-&gt;2866K(116544K), 0.0489980 secs] 11012K-&gt;2866K(129664K), [Perm : 8602K-&gt;8602K(131072K)], 0.0490880 secs]
 * </pre>
 * 
 * @author <a href="mailto:mmillson@redhat.com">Mike Millson</a>
 * @author jborelo
 * 
 */
public class SerialOldEvent implements BlockingEvent, YoungCollection, OldCollection, PermCollection, YoungData,
        OldData, PermData, TriggerData {

    /**
     * The log entry for the event. Can be used for debugging purposes.
     */
    private String logEntry;

    /**
     * The elapsed clock time for the GC event in milliseconds (rounded).
     */
    private int duration;

    /**
     * The time when the GC event happened in milliseconds after JVM startup.
     */
    private long timestamp;

    /**
     * Young generation size (kilobytes) at beginning of GC event.
     */
    private int young;

    /**
     * Young generation size (kilobytes) at end of GC event.
     */
    private int youngEnd;

    /**
     * Available space in young generation (kilobytes). Equals young generation allocation minus one survivor space.
     */
    private int youngAvailable;

    /**
     * Old generation size (kilobytes) at beginning of GC event.
     */
    private int old;

    /**
     * Old generation size (kilobytes) at end of GC event.
     */
    private int oldEnd;

    /**
     * Space allocated to old generation (kilobytes).
     */
    private int oldAllocation;

    /**
     * Permanent generation size (kilobytes) at beginning of GC event.
     */
    private int permGen;

    /**
     * Permanent generation size (kilobytes) at end of GC event.
     */
    private int permGenEnd;

    /**
     * Space allocated to permanent generation (kilobytes).
     */
    private int permGenAllocation;

    /**
     * The trigger for the GC event.
     */
    private String trigger;

    /**
     * Trigger(s) regular expression(s).
     */
    public static final String TRIGGER = "(" + JdkRegEx.TRIGGER_SYSTEM_GC + ")";

    /**
     * Regular expressions defining the logging.
     */
    private static final String REGEX = "^" + JdkRegEx.TIMESTAMP + ": \\[Full GC( )?(\\(" + TRIGGER + "\\))? "
            + JdkRegEx.TIMESTAMP + ": \\[Tenured: " + JdkRegEx.SIZE + "->" + JdkRegEx.SIZE + "\\(" + JdkRegEx.SIZE
            + "\\), " + JdkRegEx.DURATION + "\\] " + JdkRegEx.SIZE + "->" + JdkRegEx.SIZE + "\\(" + JdkRegEx.SIZE
            + "\\), \\[Perm : " + JdkRegEx.SIZE + "->" + JdkRegEx.SIZE + "\\(" + JdkRegEx.SIZE + "\\)\\], "
            + JdkRegEx.DURATION + "\\]" + JdkRegEx.TIMES_BLOCK + "?[ ]*$";

    private static Pattern pattern = Pattern.compile(SerialOldEvent.REGEX);

    /**
     * Default constructor
     */
    public SerialOldEvent() {
    }

    /**
     * 
     * @param logEntry
     *            The log entry for the event.
     */
    public SerialOldEvent(String logEntry) {
        this.logEntry = logEntry;
        Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            timestamp = JdkMath.convertSecsToMillis(matcher.group(1)).longValue();
            if (matcher.group(4) != null) {
                trigger = matcher.group(4);
            }
            old = Integer.parseInt(matcher.group(7));
            oldEnd = Integer.parseInt(matcher.group(8));
            oldAllocation = Integer.parseInt(matcher.group(9));
            int totalBegin = Integer.parseInt(matcher.group(11));
            young = totalBegin - getOldOccupancyInit();
            int totalEnd = Integer.parseInt(matcher.group(12));
            youngEnd = totalEnd - getOldOccupancyEnd();
            int totalAllocation = Integer.parseInt(matcher.group(13));
            youngAvailable = totalAllocation - getOldSpace();
            // Do not need total begin/end/allocation, as these can be calculated.
            permGen = Integer.parseInt(matcher.group(14));
            permGenEnd = Integer.parseInt(matcher.group(15));
            permGenAllocation = Integer.parseInt(matcher.group(16));
            duration = JdkMath.convertSecsToMillis(matcher.group(17)).intValue();
        }
    }

    /**
     * Alternate constructor. Create serial old detail logging event from values.
     * 
     * @param logEntry
     *            The log entry for the event.
     * @param timestamp
     *            The time when the GC event happened in milliseconds after JVM startup.
     * @param duration
     *            The elapsed clock time for the GC event in milliseconds.
     */
    public SerialOldEvent(String logEntry, long timestamp, int duration) {
        this.logEntry = logEntry;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    public String getLogEntry() {
        return logEntry;
    }

    protected void setLogEntry(String logEntry) {
        this.logEntry = logEntry;
    }

    public int getDuration() {
        return duration;
    }

    protected void setDuration(int duration) {
        this.duration = duration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    protected void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getYoungOccupancyInit() {
        return young;
    }

    protected void setYoungOccupancyInit(int young) {
        this.young = young;
    }

    public int getYoungOccupancyEnd() {
        return youngEnd;
    }

    protected void setYoungOccupancyEnd(int youngEnd) {
        this.youngEnd = youngEnd;
    }

    public int getYoungSpace() {
        return youngAvailable;
    }

    protected void setYoungSpace(int youngAvailable) {
        this.youngAvailable = youngAvailable;
    }

    public int getOldOccupancyInit() {
        return old;
    }

    protected void setOldOccupancyInit(int old) {
        this.old = old;
    }

    public int getOldOccupancyEnd() {
        return oldEnd;
    }

    protected void setOldOccupancyEnd(int oldEnd) {
        this.oldEnd = oldEnd;
    }

    public int getOldSpace() {
        return oldAllocation;
    }

    protected void setOldSpace(int oldAllocation) {
        this.oldAllocation = oldAllocation;
    }

    public int getPermOccupancyInit() {
        return permGen;
    }

    protected void setPermOccupancyInit(int permGen) {
        this.permGen = permGen;
    }

    public int getPermOccupancyEnd() {
        return permGenEnd;
    }

    protected void setPermOccupancyEnd(int permGenEnd) {
        this.permGenEnd = permGenEnd;
    }

    public int getPermSpace() {
        return permGenAllocation;
    }

    protected void setPermSpace(int permGenAllocation) {
        this.permGenAllocation = permGenAllocation;
    }

    public String getName() {
        return JdkUtil.LogEventType.SERIAL_OLD.toString();
    }

    public String getTrigger() {
        return trigger;
    }

    protected void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    /**
     * Determine if the logLine matches the logging pattern(s) for this event.
     * 
     * @param logLine
     *            The log line to test.
     * @return true if the log line matches the event pattern, false otherwise.
     */
    public static boolean match(String logLine) {
        return pattern.matcher(logLine).matches();
    }
}
