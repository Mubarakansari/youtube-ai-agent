package com.agent;

import com.agent.model.VideoResult;
import com.agent.service.EmailService;
import com.agent.service.YouTubeService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

/**
 * AgentApplication — Spring Boot entry point.
 *
 * Implements ApplicationRunner so the agent:
 *   1. Starts up
 *   2. Runs fetchTopVideos() + sendEmail()
 *   3. Exits automatically
 *
 * This is ideal for GitHub Actions (run once & exit).
 * For local scheduling, use @Scheduled in a separate profile.
 */
@SpringBootApplication
public class AgentApplication implements ApplicationRunner {

    private final YouTubeService youTubeService;
    private final EmailService   emailService;

    public AgentApplication(YouTubeService youTubeService, EmailService emailService) {
        this.youTubeService = youTubeService;
        this.emailService   = emailService;
    }

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("🚀 YouTube AI Monitoring Agent started...");

        try {
            List<VideoResult> topVideos = youTubeService.fetchTopVideos();

            if (topVideos.isEmpty()) {
                System.out.println("⚠️  No videos found for today. Skipping email.");
                return;
            }

            System.out.println("✅ Found " + topVideos.size() + " top videos. Sending email...");
            emailService.sendEmail(topVideos);
            System.out.println("📧 Email sent successfully!");

        } catch (Exception e) {
            System.err.println("❌ Agent failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
