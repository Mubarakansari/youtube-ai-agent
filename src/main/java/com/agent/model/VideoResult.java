package com.agent.model;

/**
 * Represents a single YouTube video with all enriched fields
 * needed for ranking and displaying in the email digest.
 */
public class VideoResult {

    private String videoId;
    private String title;
    private String channelName;
    private String publishedAt;          // ISO-8601 string
    private String publishedAtFormatted; // e.g. "April 13, 2026"
    private String thumbnail;
    private long   viewCount;
    private String viewCountFormatted;   // e.g. "1,23,45,678"
    private int    durationSeconds;
    private String durationFormatted;    // e.g. "1h 23m 45s"
    private int    relevanceScore;
    private String url;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getVideoId()              { return videoId; }
    public void   setVideoId(String v)      { this.videoId = v; }

    public String getTitle()                { return title; }
    public void   setTitle(String v)        { this.title = v; }

    public String getChannelName()          { return channelName; }
    public void   setChannelName(String v)  { this.channelName = v; }

    public String getPublishedAt()          { return publishedAt; }
    public void   setPublishedAt(String v)  { this.publishedAt = v; }

    public String getPublishedAtFormatted()         { return publishedAtFormatted; }
    public void   setPublishedAtFormatted(String v) { this.publishedAtFormatted = v; }

    public String getThumbnail()            { return thumbnail; }
    public void   setThumbnail(String v)    { this.thumbnail = v; }

    public long   getViewCount()            { return viewCount; }
    public void   setViewCount(long v)      { this.viewCount = v; }

    public String getViewCountFormatted()           { return viewCountFormatted; }
    public void   setViewCountFormatted(String v)   { this.viewCountFormatted = v; }

    public int    getDurationSeconds()      { return durationSeconds; }
    public void   setDurationSeconds(int v) { this.durationSeconds = v; }

    public String getDurationFormatted()            { return durationFormatted; }
    public void   setDurationFormatted(String v)    { this.durationFormatted = v; }

    public int    getRelevanceScore()       { return relevanceScore; }
    public void   setRelevanceScore(int v)  { this.relevanceScore = v; }

    public String getUrl()                  { return url; }
    public void   setUrl(String v)          { this.url = v; }
}
