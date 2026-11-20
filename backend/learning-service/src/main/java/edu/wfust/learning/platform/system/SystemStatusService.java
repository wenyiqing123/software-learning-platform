package edu.wfust.learning.platform.system;

import edu.wfust.learning.platform.knowledge.PythonGraphClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class SystemStatusService {
    private static final List<Descriptor> DESCRIPTORS = List.of(
            new Descriptor("mysql", "MySQL", "课程、题库和学习记录无法读写"),
            new Descriptor("redis", "Redis", "推荐读取可能变慢，课程和题库仍可使用"),
            new Descriptor("rabbitmq", "RabbitMQ", "掌握度异步更新可能延迟，答题记录仍可提交"),
            new Descriptor("knowledge", "Python Bridge / 知识图谱", "知识图谱和图谱增强问答暂不可用，课程和题库仍可使用"),
            new Descriptor("ai", "大模型服务", "AI 助教和问答实验暂不可用，课程和题库仍可使用")
    );

    private final Map<String, Supplier<Boolean>> probes;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SystemStatusService(DataSource dataSource, StringRedisTemplate redis, RabbitTemplate rabbit,
                               PythonGraphClient knowledge,
                               @Value("${ai.service.base-url:http://127.0.0.1:8082}") String aiBaseUrl) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(3));
        RestClient aiClient = RestClient.builder().baseUrl(aiBaseUrl).requestFactory(factory).build();
        this.probes = Map.of(
                "mysql", () -> mysqlUp(dataSource),
                "redis", () -> redisUp(redis),
                "rabbitmq", () -> rabbitUp(rabbit),
                "knowledge", knowledge::isAvailable,
                "ai", () -> httpUp(aiClient)
        );
        this.clock = Clock.systemUTC();
    }

    SystemStatusService(Map<String, Supplier<Boolean>> probes, Clock clock) {
        this.probes = Map.copyOf(probes);
        this.clock = clock;
    }

    public Status current() {
        List<Dependency> dependencies = DESCRIPTORS.stream()
                .map(descriptor -> new Dependency(descriptor.key(), descriptor.label(), safeProbe(descriptor.key()), descriptor.impact()))
                .toList();
        String overall = dependencies.stream().anyMatch(dependency -> dependency.key().equals("mysql") && dependency.isDown())
                ? "DOWN"
                : dependencies.stream().anyMatch(Dependency::isDown) ? "DEGRADED" : "UP";
        return new Status(overall, Instant.now(clock), dependencies);
    }

    private String safeProbe(String key) {
        try {
            return Boolean.TRUE.equals(probes.getOrDefault(key, () -> false).get()) ? "UP" : "DOWN";
        } catch (RuntimeException ignored) {
            return "DOWN";
        }
    }

    private static boolean mysqlUp(DataSource dataSource) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return connection.isValid(2);
        } catch (Exception ignored) {
            return false;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static boolean redisUp(StringRedisTemplate redis) {
        if (redis.getConnectionFactory() == null) return false;
        var connection = redis.getConnectionFactory().getConnection();
        try {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            connection.close();
        }
    }

    private static boolean rabbitUp(RabbitTemplate rabbit) {
        try {
            return Boolean.TRUE.equals(rabbit.execute(channel -> channel != null && channel.isOpen()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean httpUp(RestClient client) {
        try {
            return client.get().uri("/actuator/health")
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record Descriptor(String key, String label, String impact) { }

    public record Status(String overallStatus, Instant checkedAt, List<Dependency> dependencies) { }

    public record Dependency(String key, String label, String status, String impact) {
        private boolean isDown() { return !"UP".equals(status); }
    }
}
