package menupick.repository;

import menupick.domain.AiAssessment;
import menupick.domain.Inquiry;
import menupick.domain.MenuDecision;
import menupick.domain.Recommendation;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 개인 로컬 개발용 파일 저장소(data/).
 * 시작 시 한 번 메모리로 읽고, 쓰기는 JSONL append, 삭제는 임시 파일에 다시 쓴 뒤 원자적으로 교체합니다.
 * 읽기는 여러 요청이 동시에, 쓰기는 하나씩 처리합니다.
 */
public final class FileMenuPickRepository implements MenuPickRepository {
    private final ObjectMapper json;
    private final Path recommendationFile;
    private final Path inquiryFile;
    private final Path monthlyLimitFile;
    private final Path decisionFile;
    private final Path aiAssessmentFile;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    // id 오름차순 = 저장 순서. 최신 조회/단건 조회/범위 조회 모두 O(log n).
    private final TreeMap<Long, Recommendation> recommendations = new TreeMap<>();
    private final TreeMap<Long, Inquiry> inquiries = new TreeMap<>();
    private final Set<String> monthlyLimitKeys = new HashSet<>();
    private final TreeMap<Long, AiAssessment> aiAssessments = new TreeMap<>();
    private MenuDecision decision;

    public FileMenuPickRepository(Path storageDir, ObjectMapper json) {
        this.json = json;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException ex) {
            throw new UncheckedIOException("저장소 디렉터리를 만들 수 없습니다: " + storageDir, ex);
        }
        this.recommendationFile = storageDir.resolve("recommendations.jsonl");
        this.inquiryFile = storageDir.resolve("inquiries.jsonl");
        this.monthlyLimitFile = storageDir.resolve("monthly-limits.jsonl");
        this.decisionFile = storageDir.resolve("decision.json");
        this.aiAssessmentFile = storageDir.resolve("ai-assessments.jsonl");
        load();
    }

    @Override
    public Optional<Recommendation> saveMonthlyRecommendation(String userKey, String monthKey, NewRecommendation input) {
        return write(() -> {
            String key = userKey + '\u0000' + monthKey;
            if (monthlyLimitKeys.contains(key)) {
                return Optional.empty();
            }
            Recommendation saved = appendRecommendation(input);
            append(monthlyLimitFile, new MonthlyLimit(userKey, monthKey, saved.id()));
            monthlyLimitKeys.add(key);
            return Optional.of(saved);
        });
    }

    @Override
    public Recommendation saveRecommendation(NewRecommendation input) {
        return write(() -> appendRecommendation(input));
    }

    /** 쓰기 잠금 안에서만 호출합니다. */
    private Recommendation appendRecommendation(NewRecommendation input) {
        long id = recommendations.isEmpty() ? 1L : recommendations.lastKey() + 1L;
        Recommendation saved = new Recommendation(id, input.menuName(), input.reason(),
                input.recommendedMenu(), input.category(), Instant.now().toString());
        append(recommendationFile, saved);
        recommendations.put(id, saved);
        return saved;
    }

    @Override
    public Optional<Recommendation> findLatestRecommendation() {
        return read(() -> Optional.ofNullable(recommendations.lastEntry()).map(Map.Entry::getValue));
    }

    @Override
    public Optional<Recommendation> findRecommendationById(long id) {
        return read(() -> Optional.ofNullable(recommendations.get(id)));
    }

    @Override
    public List<Recommendation> findRecentRecommendations(int limit) {
        return read(() -> newest(recommendations, limit));
    }

    @Override
    public int deleteRecommendations(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return write(() -> {
            int deleted = removeAll(recommendations, ids);
            if (deleted > 0) {
                replaceFile(recommendationFile, recommendations.values());
                if (decision != null && ids.contains(decision.recommendationId())) {
                    decision = null;
                    deleteFile(decisionFile);
                }
                if (removeAll(aiAssessments, ids) > 0) {
                    replaceFile(aiAssessmentFile, aiAssessments.values());
                }
            }
            return deleted;
        });
    }

    @Override
    public Optional<MenuDecision> findDecision() {
        return read(() -> Optional.ofNullable(decision));
    }

    @Override
    public void saveDecision(long recommendationId, String publishAt) {
        write(() -> {
            MenuDecision next = new MenuDecision(recommendationId, publishAt, Instant.now().toString());
            replaceFile(decisionFile, List.of(next));
            decision = next;
            return null;
        });
    }

    @Override
    public Inquiry saveInquiry(String message, String adminEmail) {
        return write(() -> {
            long id = inquiries.isEmpty() ? 1L : inquiries.lastKey() + 1L;
            Inquiry saved = new Inquiry(id, message, adminEmail, Instant.now().toString());
            append(inquiryFile, saved);
            inquiries.put(id, saved);
            return saved;
        });
    }

    @Override
    public Optional<Inquiry> findLatestInquiry() {
        return read(() -> Optional.ofNullable(inquiries.lastEntry()).map(Map.Entry::getValue));
    }

    @Override
    public List<Inquiry> findRecentInquiries(int limit) {
        return read(() -> newest(inquiries, limit));
    }

    @Override
    public int deleteInquiries(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return write(() -> {
            int deleted = removeAll(inquiries, ids);
            if (deleted > 0) {
                replaceFile(inquiryFile, inquiries.values());
            }
            return deleted;
        });
    }

    @Override
    public void saveAiAssessment(AiAssessment assessment) {
        write(() -> {
            // 새 항목은 append, 재평가(덮어쓰기)는 파일 전체를 원자적으로 교체합니다.
            boolean replaced = aiAssessments.put(assessment.recommendationId(), assessment) != null;
            if (replaced) {
                replaceFile(aiAssessmentFile, aiAssessments.values());
            } else {
                append(aiAssessmentFile, assessment);
            }
            return null;
        });
    }

    @Override
    public List<AiAssessment> findAiAssessments(Collection<Long> recommendationIds) {
        return read(() -> recommendationIds.stream()
                .distinct()
                .map(aiAssessments::get)
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    // ---- 내부 구현 ----

    private void load() {
        for (AiAssessment item : readLines(aiAssessmentFile, AiAssessment.class)) {
            aiAssessments.put(item.recommendationId(), item);
        }
        for (Recommendation item : readLines(recommendationFile, Recommendation.class)) {
            recommendations.put(item.id(), item);
        }
        for (Inquiry item : readLines(inquiryFile, Inquiry.class)) {
            inquiries.put(item.id(), item);
        }
        for (MonthlyLimit item : readLines(monthlyLimitFile, MonthlyLimit.class)) {
            monthlyLimitKeys.add(nullToEmpty(item.userKey()) + '\u0000' + nullToEmpty(item.monthKey()));
        }
        List<MenuDecision> decisions = readLines(decisionFile, MenuDecision.class);
        decision = decisions.isEmpty() ? null : decisions.getLast();
    }

    private static <T> List<T> newest(TreeMap<Long, T> source, int limit) {
        int count = Math.min(Math.max(limit, 0), source.size());
        List<T> result = new ArrayList<>(count);
        for (T item : source.descendingMap().values()) {
            if (result.size() == count) {
                break;
            }
            result.add(item);
        }
        return result;
    }

    private static int removeAll(TreeMap<Long, ?> source, Collection<Long> ids) {
        int deleted = 0;
        for (Long id : new HashSet<>(ids)) {
            if (id != null && source.remove(id) != null) {
                deleted++;
            }
        }
        return deleted;
    }

    private <T> List<T> readLines(Path file, Class<T> type) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank())
                    .map(line -> json.readValue(line, type))
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("저장 파일을 읽을 수 없습니다: " + file, ex);
        }
    }

    private void append(Path file, Object value) {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(json.writeValueAsString(value));
            writer.newLine();
        } catch (IOException ex) {
            throw new UncheckedIOException("저장 파일에 쓸 수 없습니다: " + file, ex);
        }
    }

    /** 임시 파일에 모두 쓴 뒤 교체하므로, 중간에 실패해도 기존 파일이 깨지지 않습니다. */
    private void replaceFile(Path file, Collection<?> values) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Object value : values) {
                    writer.write(json.writeValueAsString(value));
                    writer.newLine();
                }
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("저장 파일을 교체할 수 없습니다: " + file, ex);
        }
    }

    private static void deleteFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("저장 파일을 삭제할 수 없습니다: " + file, ex);
        }
    }

    private <T> T read(Supplier<T> action) {
        lock.readLock().lock();
        try {
            return action.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    private <T> T write(Supplier<T> action) {
        lock.writeLock().lock();
        try {
            return action.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record MonthlyLimit(String userKey, String monthKey, long recommendationId) {
    }
}
