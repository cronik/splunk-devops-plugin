package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.splunk.splunkjenkins.utils.LogEventHelper.nonEmpty;

public class EventRecord {
    private final static String METADATA_KEYS[] = {"index", "source", "host", "sourcetype"};
    private long time;
    private int retryCount;
    private Object message;
    private EventType eventType;
    private String source;

    public EventRecord(Object message, EventType eventType) {
        this.retryCount = 0;
        if (eventType == null) {
            this.eventType = EventType.GENERIC_TEXT;
        } else {
            this.eventType = eventType;
        }
        this.time = System.currentTimeMillis();
        this.message = message;
    }

    public void increase() {
        this.retryCount++;
    }

    public boolean discard() {
        return retryCount > SplunkJenkinsInstallation.get().getMaxRetries();
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public Object getMessage() {
        return message;
    }

    public String getMessageString() {
        if (message instanceof byte[]) {
            return new String((byte[]) message);
        } else {
            return message.toString();
        }
    }

    /**
     * @return short message, to be showed in debug message
     */
    public String getShortDescr() {
        if (message == null) { //should not happen
            return "NULL message";
        }
        if (message instanceof String) {
            return "{length:" + ((String) message).length() + StringUtils.substring((String) message, 0, 80)+" ...}";
        } else {
            return "{raw data" + StringUtils.substring("" + message, 0, 80)+" ...}";
        }
    }

    public String getTimestamp() {
        return String.format(Locale.US, "%.3f", time / 1000d);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return the event type
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * @return metdata information for http event collector
     */
    public Map<String, String> getMetaData() {
        LogEventHelper.UrlQueryBuilder metaDataBuilder = new LogEventHelper.UrlQueryBuilder();
        metaDataBuilder.putIfAbsent("source", source);
        SplunkJenkinsInstallation config = SplunkJenkinsInstallation.get();
        for (String metaKeyName : METADATA_KEYS) {
            //individual config(EventType) have higher priority over default config
            metaDataBuilder.putIfAbsent(metaKeyName, config.getMetaData(eventType.name().toLowerCase() + "." + metaKeyName));
            metaDataBuilder.putIfAbsent(metaKeyName, config.getMetaData(metaKeyName));
        }
        return metaDataBuilder.getQueryMap();
    }

    /**
     *
     * @param config Splunk config
     * @return the http event collector endpoint
     */
    public String getEndpoint(SplunkJenkinsInstallation config) {
        if (!config.isRawEventEnabled()) {
            return config.getJsonUrl();
        }
        Map queryMap = new HashMap();
        queryMap.putAll(getMetaData());
        if (!eventType.needSplit()) {
            queryMap.put("time", getTimestamp());
        }
        return config.getRawUrl() + "?" + LogEventHelper.UrlQueryBuilder.toString(queryMap);
    }
}