package menupick.config;

import com.zaxxer.hikari.HikariDataSource;
import menupick.repository.JdbcMenuPickRepository;
import menupick.repository.LegacyFileImporter;
import menupick.repository.MenuPickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * 저장소 선택: DB_URL이 있으면 MySQL/MariaDB, 없으면 로컬 내장 H2 DB(data/menupick.mv.db).
 * 두 경우 모두 같은 {@link JdbcMenuPickRepository}를 씁니다(H2는 MySQL 호환 모드).
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {
    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(MenuPickProperties properties) {
        MenuPickProperties.Db db = properties.db();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("menupick-db");
        dataSource.setMinimumIdle(1);
        if (StringUtils.hasText(db.url())) {
            dataSource.setJdbcUrl(db.url());
            dataSource.setUsername(db.user());
            dataSource.setPassword(db.password());
            dataSource.setMaximumPoolSize(Math.max(2, db.maxPoolSize()));
            log.info("Storage: database (pool size={})", dataSource.getMaximumPoolSize());
        } else {
            Path file = h2File(properties);
            // AUTO_SERVER=TRUE: 서버가 켜져 있어도 IntelliJ 등 다른 도구로 같은 DB 파일을 열 수 있습니다.
            // TRACE_LEVEL_FILE=0: 의도적으로 무시하는 스키마 오류(중복 컬럼)를 .trace.db 파일에 남기지 않습니다.
            dataSource.setJdbcUrl("jdbc:h2:file:" + file + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;TRACE_LEVEL_FILE=0");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setMaximumPoolSize(Math.max(2, db.maxPoolSize()));
            log.info("Storage: H2 ({}.mv.db)", file);
        }
        return dataSource;
    }

    @Bean
    JdbcTemplate jdbcTemplate(HikariDataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    DataSourceTransactionManager transactionManager(HikariDataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    MenuPickRepository menuPickRepository(JdbcTemplate jdbcTemplate, DataSourceTransactionManager transactionManager,
                                          MenuPickProperties properties, ObjectMapper objectMapper) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        JdbcMenuPickRepository repository = new JdbcMenuPickRepository(jdbcTemplate, tx);
        if (!StringUtils.hasText(properties.db().url())) {
            // 예전 파일 저장소(data/*.jsonl)가 있으면 H2가 비어 있을 때 한 번만 옮깁니다.
            new LegacyFileImporter(jdbcTemplate, tx, objectMapper).importIfEmpty(storageDir(properties));
        }
        return repository;
    }

    static Path storageDir(MenuPickProperties properties) {
        return Path.of(properties.storage().dir()).toAbsolutePath().normalize();
    }

    static Path h2File(MenuPickProperties properties) {
        return storageDir(properties).resolve("menupick");
    }
}
