package menupick.db;

import menupick.json.JsonUtil;
import menupick.model.InquiryRecord;
import menupick.model.RecommendationRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

public final class FileDatabase {
    private final Path recommendationFile;
    private final Path inquiryFile;

    public FileDatabase(Path storageDir) throws IOException {
        Files.createDirectories(storageDir);
        this.recommendationFile = storageDir.resolve("recommendations.jsonl");
        this.inquiryFile = storageDir.resolve("inquiries.jsonl");
    }

    public synchronized RecommendationRecord saveRecommendation(
            String menuName,
            String reason,
            String recommendedMenu
    ) throws IOException {
        long nextId = nextRecommendationId();
        RecommendationRecord record = new RecommendationRecord(
                nextId,
                menuName,
                reason,
                recommendedMenu,
                Instant.now().toString()
        );
        appendLine(recommendationFile, JsonUtil.stringify(record.toMap()));
        return record;
    }

    public synchronized RecommendationRecord getLatestRecommendation() throws IOException {
        return readLatest(recommendationFile, RecommendationRecord::fromMap);
    }

    public synchronized InquiryRecord saveInquiry(String message, String adminEmail) throws IOException {
        long nextId = nextInquiryId();
        InquiryRecord record = new InquiryRecord(
                nextId,
                message,
                adminEmail,
                Instant.now().toString()
        );
        appendLine(inquiryFile, JsonUtil.stringify(record.toMap()));
        return record;
    }

    public synchronized InquiryRecord getLatestInquiry() throws IOException {
        return readLatest(inquiryFile, InquiryRecord::fromMap);
    }

    private long nextRecommendationId() throws IOException {
        RecommendationRecord latest = getLatestRecommendation();
        return latest == null ? 1L : latest.id() + 1L;
    }

    private long nextInquiryId() throws IOException {
        InquiryRecord latest = getLatestInquiry();
        return latest == null ? 1L : latest.id() + 1L;
    }

    private <T> T readLatest(Path file, Function<Map<String, String>, T> mapper) throws IOException {
        if (!Files.exists(file)) {
            return null;
        }

        String lastLine = null;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lastLine = line;
                }
            }
        }

        if (lastLine == null) {
            return null;
        }

        return mapper.apply(JsonUtil.parseFlatObject(lastLine));
    }

    private void appendLine(Path file, String line) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                Files.exists(file) ? new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND}
                        : new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND}
        )) {
            writer.write(line);
            writer.newLine();
        }
    }
}

