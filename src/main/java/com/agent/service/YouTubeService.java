package com.agent.service;

import com.agent.model.VideoResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * YouTubeService — fetches, filters, scores, and ranks AI-related videos
 * from the YouTube Data API v3 for the India region.
 *
 * Pipeline:
 *   1. Search 12 queries × 3 pages each (India / regionCode=IN)
 *   2. Deduplicate by videoId
 *   3. Fetch snippet + statistics + contentDetails for all unique IDs
 *   4. Manual 48-hour date verification
 *   5. Duration filter (≥ 8 minutes — excludes Shorts)
 *   6. Semantic relevance scoring (title + description)
 *   7. Sort by viewCount descending → Top 10
 */
@Service
public class YouTubeService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeService.class);

    private static final String BASE_URL        = "https://www.googleapis.com/youtube/v3";
    private static final int    MAX_PAGES        = 3;
    private static final int    MAX_RESULTS_PAGE = 50;
    private static final int    MIN_DURATION_SEC = 8 * 60;   // 8 minutes
    private static final int    MIN_SCORE        = 4;
    private static final int    TOP_N            = 10;
    private static final int    MIN_QUALITY_VIEWS = 5_000;
    private static final ZoneId IST              = ZoneId.of("Asia/Kolkata");

    @Value("${youtube.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Search Topics ─────────────────────────────────────────────────────────

    private static final List<String> SEARCH_QUERIES = List.of(
            "AI agent tutorial",
            "AI app development",
            "AI game development",
            "AI automation tools",
            "build app using AI",
            "no code AI agent",
            "GPT app build tutorial",
            "Gemini AI development",
            "Claude AI build",
            "LLM app development",
            "AI SaaS build",
            "AI workflow automation build"
    );

    // ── Semantic Vocabulary ───────────────────────────────────────────────────

    private static final List<String> AI_TERMS = List.of(
            "ai", "gpt", "chatgpt", "gemini", "claude", "llm", "openai",
            "copilot", "ai agent", "langchain", "autogpt", "ollama", "llama",
            "mistral", "machine learning", "deep learning", "generative ai",
            "hugging face", "stable diffusion"
    );

    private static final List<String> DEV_INTENT_TERMS = List.of(
            "build", "create", "develop", "code", "coding", "program",
            "tutorial", "step by step", "how to", "full guide", "from scratch",
            "deploy", "integrate", "app", "application", "android", "ios",
            "mobile", "web", "game", "saas", "api", "bot", "chatbot",
            "agent", "workflow", "automation", "software", "tool", "script", "automate"
    );

    private static final List<String> TUTORIAL_BONUS = List.of(
            "tutorial", "how to", "guide", "course", "beginner", "learn", "walkthrough"
    );

    private static final List<String> NOISE_TERMS = List.of(
            "news", "stock", "market", "prediction", "motivation",
            "tricks", "hacks", "mind blowing", "earning", "make money",
            "crypto", "investment", "will replace", "future of ai"
    );

    // ── Public Entry Point ────────────────────────────────────────────────────

    /**
     * Orchestrates the full pipeline and returns the top N AI-related videos
     * from India, sorted by viewCount descending.
     */
    public List<VideoResult> fetchTopVideos() {
        // ── API Key validation ────────────────────────────────────────────────
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("YOUTUBE_API_KEY is not set! Check application.properties.");
        }
        log.info("🔑 API Key loaded: {}...", apiKey.substring(0, Math.min(8, apiKey.length())));

        // Step 1 — Timestamp
        Instant cutoff = Instant.now().minusSeconds(48L * 60 * 60);
        String publishedAfter = cutoff.toString().replaceAll("\\.\\d+Z$", "Z");

        log.info("🕐 48-hour cutoff (UTC): {}", publishedAfter);
        log.info("📍 Region: India (IN) | Language: Hindi + English");

        // Step 2 — Search all queries (sequential for reliable error logging)
        log.info("🔍 Searching {} queries × up to {} pages...", SEARCH_QUERIES.size(), MAX_PAGES);

        // Run sequentially so error logs are visible in order
        List<String> allIds = new ArrayList<>();
        for (String query : SEARCH_QUERIES) {
            allIds.addAll(searchVideos(query, publishedAfter));
        }

        log.info("📦 Total raw IDs collected: {}", allIds.size());

        // Step 3 — Deduplicate
        List<String> uniqueIds = allIds.stream().distinct().collect(Collectors.toList());
        log.info("✂️  After deduplication:    {} unique videos", uniqueIds.size());

        if (uniqueIds.isEmpty()) {
            log.warn("⚠️  No videos found.");
            return Collections.emptyList();
        }

        // Step 4 — Fetch full details
        log.info("📡 Fetching details (snippet + statistics + contentDetails)...");
        List<VideoResult> allVideos = fetchVideoDetails(uniqueIds, cutoff);
        log.info("📋 Details fetched for:    {} videos", allVideos.size());

        // Step 5 — Manual 48h filter
        List<VideoResult> withinWindow = allVideos.stream()
                .filter(v -> Instant.parse(v.getPublishedAt()).isAfter(cutoff))
                .collect(Collectors.toList());
        log.info("📅 After 48h date filter:  {} videos", withinWindow.size());

        // Step 6 — Duration filter (≥ 8 min)
        List<VideoResult> longForm = withinWindow.stream()
                .filter(v -> v.getDurationSeconds() >= MIN_DURATION_SEC)
                .collect(Collectors.toList());
        log.info("🎬 After duration filter:  {} videos (≥ 8 min)", longForm.size());

        // Step 7 — Semantic relevance scoring
        List<VideoResult> relevant = longForm.stream()
                .filter(v -> v.getRelevanceScore() >= MIN_SCORE)
                .collect(Collectors.toList());
        log.info("🧠 After relevance filter: {} videos (score ≥ {})", relevant.size(), MIN_SCORE);

        if (relevant.isEmpty()) {
            log.warn("⚠️  No relevant videos — falling back to long-form sorted by views.");
            longForm.sort(Comparator.comparingLong(VideoResult::getViewCount).reversed());
            return longForm.stream().limit(TOP_N).collect(Collectors.toList());
        }

        // Step 8 — Sort by viewCount descending
        relevant.sort(Comparator.comparingLong(VideoResult::getViewCount).reversed());

        // Step 9 — Quality check
        if (!relevant.isEmpty() && relevant.get(0).getViewCount() < MIN_QUALITY_VIEWS) {
            log.warn("⚠️  Top video has only {} views — limited AI content today.",
                    relevant.get(0).getViewCountFormatted());
        }

        // Step 10 — Final top N
        List<VideoResult> topVideos = relevant.stream().limit(TOP_N).collect(Collectors.toList());

        log.info("\n🏆 Final Top {} Videos:\n", topVideos.size());
        for (int i = 0; i < topVideos.size(); i++) {
            VideoResult v = topVideos.get(i);
            log.info("  {:2d}. [Score:{}] [{} views] [{}] {}",
                    i + 1, v.getRelevanceScore(), v.getViewCountFormatted(),
                    v.getDurationFormatted(), v.getTitle());
        }

        return topVideos;
    }

    // ── Private: Search ───────────────────────────────────────────────────────

    private List<String> searchVideos(String query, String publishedAfter) {
        List<String> ids = new ArrayList<>();
        String pageToken = null;

        for (int page = 0; page < MAX_PAGES; page++) {
            try {
                UriComponentsBuilder builder = UriComponentsBuilder
                        .fromHttpUrl(BASE_URL + "/search")
                        .queryParam("key",            apiKey)
                        .queryParam("q",              query)
                        .queryParam("part",           "id")
                        .queryParam("type",           "video")
                        .queryParam("order",          "date")
                        .queryParam("publishedAfter", publishedAfter)
                        .queryParam("maxResults",     MAX_RESULTS_PAGE)
                        .queryParam("regionCode",     "IN")
                        .queryParam("safeSearch",     "none");

                if (pageToken != null) builder.queryParam("pageToken", pageToken);

                // Use .build().toUri() to avoid double-encoding
                JsonNode resp = restTemplate.getForObject(
                        builder.build().toUri(), JsonNode.class);

                if (resp == null) break;

                // ── Detect API-level errors (quota, invalid key, etc.) ────────
                JsonNode error = resp.get("error");
                if (error != null) {
                    log.error("❌ YouTube API error for query '{}': [{}] {}",
                            query,
                            error.path("code").asInt(),
                            error.path("message").asText());
                    // Log individual errors for diagnosis
                    JsonNode errors = error.path("errors");
                    if (errors.isArray()) {
                        for (JsonNode err : errors) {
                            log.error("   reason: {} | domain: {}",
                                    err.path("reason").asText(),
                                    err.path("domain").asText());
                        }
                    }
                    break; // Stop pagination on API error
                }

                JsonNode items = resp.get("items");
                if (items != null) {
                    for (JsonNode item : items) {
                        String vid = item.path("id").path("videoId").asText(null);
                        if (vid != null) ids.add(vid);
                    }
                    log.info("  ✅ Query '{}' page {}: {} results", query, page + 1, items.size());
                }

                JsonNode next = resp.get("nextPageToken");
                if (next != null && !next.isNull()) {
                    pageToken = next.asText();
                } else {
                    break; // No more pages
                }

            } catch (HttpClientErrorException e) {
                // 4xx errors — log the full response body for diagnosis
                log.error("❌ HTTP {} for query '{}': {}",
                        e.getStatusCode(), query, e.getResponseBodyAsString());
                break;
            } catch (Exception e) {
                log.error("❌ Search failed for query '{}': {}", query, e.getMessage());
                break;
            }
        }

        return ids;
    }

    // ── Private: Fetch Details ────────────────────────────────────────────────

    private List<VideoResult> fetchVideoDetails(List<String> videoIds, Instant cutoff) {
        List<VideoResult> results = new ArrayList<>();
        int batchSize = 50;

        for (int i = 0; i < videoIds.size(); i += batchSize) {
            List<String> batch = videoIds.subList(i, Math.min(i + batchSize, videoIds.size()));
            String ids = String.join(",", batch);

            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl(BASE_URL + "/videos")
                        .queryParam("key",  apiKey)
                        .queryParam("id",   ids)
                        .queryParam("part", "snippet,statistics,contentDetails")
                        .toUriString();

                JsonNode resp = restTemplate.getForObject(url, JsonNode.class);
                if (resp == null) continue;

                JsonNode items = resp.get("items");
                if (items != null) {
                    for (JsonNode item : items) {
                        VideoResult v = parseVideoItem(item);
                        if (v != null) results.add(v);
                    }
                }

            } catch (Exception e) {
                log.warn("  ⚠️  Batch fetch failed: {}", e.getMessage());
            }
        }

        return results;
    }

    // ── Private: Parse one video JSON item ────────────────────────────────────

    private VideoResult parseVideoItem(JsonNode item) {
        try {
            JsonNode snippet        = item.path("snippet");
            JsonNode statistics     = item.path("statistics");
            JsonNode contentDetails = item.path("contentDetails");

            String title        = snippet.path("title").asText("");
            String description  = snippet.path("description").asText("").substring(
                    0, Math.min(500, snippet.path("description").asText("").length()));
            String channelName  = snippet.path("channelTitle").asText("");
            String publishedAt  = snippet.path("publishedAt").asText("");
            long   viewCount    = statistics.path("viewCount").asLong(0L);
            String isoDuration  = contentDetails.path("duration").asText("PT0S");

            // Thumbnail — prefer high, fallback to medium / default
            JsonNode thumbs = snippet.path("thumbnails");
            String thumbnail = thumbs.path("high").path("url").asText(
                    thumbs.path("medium").path("url").asText(
                            thumbs.path("default").path("url").asText("")));

            int durationSec = parseDurationSeconds(isoDuration);
            int score       = scoreRelevance(title, description);

            VideoResult v = new VideoResult();
            v.setVideoId(item.path("id").asText(""));
            v.setTitle(title);
            v.setChannelName(channelName);
            v.setPublishedAt(publishedAt);
            v.setPublishedAtFormatted(formatDate(publishedAt));
            v.setThumbnail(thumbnail);
            v.setViewCount(viewCount);
            v.setViewCountFormatted(formatViewCount(viewCount));
            v.setDurationSeconds(durationSec);
            v.setDurationFormatted(formatDuration(durationSec));
            v.setRelevanceScore(score);
            v.setUrl("https://www.youtube.com/watch?v=" + v.getVideoId());

            return v;

        } catch (Exception e) {
            log.warn("  ⚠️  Failed to parse video item: {}", e.getMessage());
            return null;
        }
    }

    // ── Semantic Scoring ──────────────────────────────────────────────────────

    private int scoreRelevance(String title, String description) {
        String text = (title + " " + description).toLowerCase();

        boolean hasAI      = AI_TERMS.stream().anyMatch(text::contains);
        boolean hasDev     = DEV_INTENT_TERMS.stream().anyMatch(text::contains);
        boolean hasTutorial = TUTORIAL_BONUS.stream().anyMatch(text::contains);
        boolean hasNoise   = NOISE_TERMS.stream().anyMatch(text::contains);

        int score = 0;
        if (hasAI)       score += 2;
        if (hasDev)      score += 2;
        if (hasTutorial) score += 1;
        if (hasNoise && !hasAI) score -= 3;

        return score;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Parses ISO-8601 duration "PT1H23M45S" → total seconds. */
    private int parseDurationSeconds(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        Pattern p = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");
        Matcher m = p.matcher(iso);
        if (!m.matches()) return 0;
        int h   = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
        int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        int s   = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return h * 3600 + min * 60 + s;
    }

    /** Formats seconds → "1h 23m 45s". */
    private String formatDuration(int totalSeconds) {
        int h   = totalSeconds / 3600;
        int min = (totalSeconds % 3600) / 60;
        int s   = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h   > 0) sb.append(h).append("h ");
        if (min > 0) sb.append(min).append("m ");
        if (s   > 0) sb.append(s).append("s");
        return sb.toString().trim().isEmpty() ? "0s" : sb.toString().trim();
    }

    /** Formats view count with Indian number system commas. */
    private String formatViewCount(long count) {
        return NumberFormat.getNumberInstance(new Locale("en", "IN")).format(count);
    }

    /** Formats ISO-8601 date to "April 13, 2026" in IST. */
    private String formatDate(String isoString) {
        if (isoString == null || isoString.isEmpty()) return "";
        try {
            ZonedDateTime zdt = Instant.parse(isoString).atZone(IST);
            return zdt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
        } catch (Exception e) {
            return isoString;
        }
    }
}
