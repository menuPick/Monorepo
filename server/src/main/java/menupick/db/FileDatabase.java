package menupick.db;

import menupick.json.JsonUtil;
import menupick.model.InquiryRecord;
import menupick.model.MenuCategoryClassifier;
import menupick.model.MenuDecisionSetting;
import menupick.model.RecommendationRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-user file storage. Files are loaded once; writes are appended and deletes rewrite the file. */
public final class FileDatabase implements DatabaseRepository {
    private final Path recommendationFile;
    private final Path inquiryFile;
    private final Path monthlyLimitFile;
    private final Path decisionFile;

    private final List<RecommendationRecord> recommendations = new ArrayList<>();
    private final Map<Long, RecommendationRecord> recommendationsById = new HashMap<>();
    private final List<InquiryRecord> inquiries = new ArrayList<>();
    private final Set<String> monthlyLimitKeys = new HashSet<>();
    private MenuDecisionSetting decision;

    public FileDatabase(Path storageDir) throws IOException {
        Files.createDirectories(storageDir);
        recommendationFile = storageDir.resolve("recommendations.jsonl");
        inquiryFile = storageDir.resolve("inquiries.jsonl");
        monthlyLimitFile = storageDir.resolve("monthly-limits.jsonl");
        decisionFile = storageDir.resolve("decision.json");
        load();
    }

    @Override
    public synchronized RecommendationRecord saveMonthlyRecommendation(String userKey, String monthKey,
            String menuName, String reason, String recommendedMenu, String category) throws IOException {
        String key = userKey + "\u0000" + monthKey;
        if (!monthlyLimitKeys.add(key)) return null;

        RecommendationRecord record = saveRecommendation(menuName, reason, recommendedMenu, category);
        Map<String, Object> limit = new HashMap<>();
        limit.put("userKey", userKey);
        limit.put("monthKey", monthKey);
        limit.put("recommendationId", record.id());
        append(monthlyLimitFile, JsonUtil.stringify(limit));
        return record;
    }

    @Override
    public synchronized RecommendationRecord saveRecommendation(String menuName, String reason,
            String recommendedMenu, String category) throws IOException {
        long id = recommendations.isEmpty() ? 1L : recommendations.get(recommendations.size() - 1).id() + 1L;
        RecommendationRecord record = new RecommendationRecord(id, menuName, reason, recommendedMenu,
                MenuCategoryClassifier.normalize(category, menuName, recommendedMenu), Instant.now().toString());
        append(recommendationFile, JsonUtil.stringify(record.toMap()));
        recommendations.add(record);
        recommendationsById.put(record.id(), record);
        return record;
    }

    @Override
    public synchronized RecommendationRecord getLatestRecommendation() {
        return recommendations.isEmpty() ? null : recommendations.get(recommendations.size() - 1);
    }

    @Override
    public synchronized RecommendationRecord getRecommendationById(long id) {
        return recommendationsById.get(id);
    }

    @Override
    public synchronized MenuDecisionSetting getMenuDecisionSetting() {
        return decision;
    }

    @Override
    public synchronized void upsertMenuDecisionSetting(long recommendationId, String publishAt) throws IOException {
        decision = new MenuDecisionSetting(recommendationId, publishAt, Instant.now().toString());
        Files.writeString(decisionFile, JsonUtil.stringify(decision.toMap()), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public synchronized InquiryRecord saveInquiry(String message, String adminEmail) throws IOException {
        long id = inquiries.isEmpty() ? 1L : inquiries.get(inquiries.size() - 1).id() + 1L;
        InquiryRecord record = new InquiryRecord(id, message, adminEmail, Instant.now().toString());
        append(inquiryFile, JsonUtil.stringify(record.toMap()));
        inquiries.add(record);
        return record;
    }

    @Override
    public synchronized InquiryRecord getLatestInquiry() {
        return inquiries.isEmpty() ? null : inquiries.get(inquiries.size() - 1);
    }

    @Override
    public synchronized List<RecommendationRecord> listRecommendations(int limit) {
        return newest(recommendations, limit);
    }

    @Override
    public synchronized int deleteRecommendations(List<Long> ids) throws IOException {
        Set<Long> targetIds = new HashSet<>(ids);
        int before = recommendations.size();
        recommendations.removeIf(item -> targetIds.contains(item.id()));
        recommendationsById.clear();
        for (RecommendationRecord item : recommendations) recommendationsById.put(item.id(), item);
        rewriteRecommendations();
        return before - recommendations.size();
    }

    @Override
    public synchronized List<InquiryRecord> listInquiries(int limit) {
        return newest(inquiries, limit);
    }

    @Override
    public synchronized int deleteInquiries(List<Long> ids) throws IOException {
        Set<Long> targetIds = new HashSet<>(ids);
        int before = inquiries.size();
        inquiries.removeIf(item -> targetIds.contains(item.id()));
        rewriteInquiries();
        return before - inquiries.size();
    }

    private void load() throws IOException {
        for (Map<String, String> map : readObjects(recommendationFile)) {
            RecommendationRecord item = RecommendationRecord.fromMap(map);
            recommendations.add(item);
            recommendationsById.put(item.id(), item);
        }
        for (Map<String, String> map : readObjects(inquiryFile)) inquiries.add(InquiryRecord.fromMap(map));
        for (Map<String, String> map : readObjects(monthlyLimitFile)) {
            monthlyLimitKeys.add(map.getOrDefault("userKey", "") + "\u0000" + map.getOrDefault("monthKey", ""));
        }
        if (Files.exists(decisionFile)) {
            Map<String, String> map = JsonUtil.parseFlatObject(Files.readString(decisionFile, StandardCharsets.UTF_8));
            decision = new MenuDecisionSetting(parseLong(map.get("recommendationId")), map.get("publishAt"), map.get("updatedAt"));
        }
    }

    private static <T> List<T> newest(List<T> source, int limit) {
        int count = Math.min(Math.max(limit, 0), source.size());
        List<T> result = new ArrayList<>(count);
        for (int index = source.size() - 1; index >= source.size() - count; index--) result.add(source.get(index));
        return result;
    }

    private void rewriteRecommendations() throws IOException {
        try (BufferedWriter writer = newWriter(recommendationFile)) {
            for (RecommendationRecord item : recommendations) {
                writer.write(JsonUtil.stringify(item.toMap()));
                writer.newLine();
            }
        }
    }

    private void rewriteInquiries() throws IOException {
        try (BufferedWriter writer = newWriter(inquiryFile)) {
            for (InquiryRecord item : inquiries) {
                writer.write(JsonUtil.stringify(item.toMap()));
                writer.newLine();
            }
        }
    }

    private static BufferedWriter newWriter(Path file) throws IOException {
        return Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void append(Path file, String line) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.newLine();
        }
    }

    private static List<Map<String, String>> readObjects(Path file) throws IOException {
        List<Map<String, String>> result = new ArrayList<>();
        if (!Files.exists(file)) return result;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) if (!line.isBlank()) result.add(JsonUtil.parseFlatObject(line));
        }
        return result;
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value == null ? "0" : value); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
