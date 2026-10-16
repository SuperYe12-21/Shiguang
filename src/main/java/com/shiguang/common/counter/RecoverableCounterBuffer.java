package com.shiguang.common.counter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;

@Slf4j
@Component
public class RecoverableCounterBuffer {

    private static final int BATCH_SIZE = 256;

    private static final DefaultRedisScript<List> STAGE = new DefaultRedisScript<>("""
            local batch = redis.call('GET', KEYS[3])
            if batch then
                local entries = redis.call('HGETALL', KEYS[2])
                table.insert(entries, 1, batch)
                return entries
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return redis.error_reply('counter inflight batch id missing')
            end
            local cursor = redis.call('GET', KEYS[4]) or '0'
            local scanned = redis.call('HSCAN', KEYS[1], cursor, 'COUNT', ARGV[2])
            redis.call('SET', KEYS[4], scanned[1])
            local entries = scanned[2]
            local result = {}
            for index = 1, math.min(#entries, tonumber(ARGV[2]) * 2), 2 do
                redis.call('HSET', KEYS[2], entries[index], entries[index + 1])
                redis.call('HDEL', KEYS[1], entries[index])
                table.insert(result, entries[index])
                table.insert(result, entries[index + 1])
            end
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('SET', KEYS[4], '0')
            end
            if #result == 0 then return {} end
            redis.call('SET', KEYS[3], ARGV[1])
            table.insert(result, 1, ARGV[1])
            return result
            """, List.class);

    private static final DefaultRedisScript<Long> ACK = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) == ARGV[1] then
                return redis.call('DEL', KEYS[1], KEYS[2])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<List> SNAPSHOT = new DefaultRedisScript<>("""
            local result = {redis.call('GET', KEYS[3]) or ''}
            for index, field in ipairs(ARGV) do
                table.insert(result, redis.call('HGET', KEYS[1], field) or '0')
                table.insert(result, redis.call('HGET', KEYS[2], field) or '0')
            end
            return result
            """, List.class);

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public RecoverableCounterBuffer(StringRedisTemplate redis, JdbcTemplate jdbc,
                                    PlatformTransactionManager transactionManager) {
        this.redis = redis;
        this.jdbc = jdbc;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setTimeout(30);
    }

    public void flush(String counterName, String pendingKey, BiConsumer<String, Long> applyDelta) {
        try {
            List<?> staged = redis.execute(STAGE, List.of(pendingKey, pendingKey + ":inflight",
                    pendingKey + ":batch", pendingKey + ":cursor"), UUID.randomUUID().toString(),
                    Integer.toString(BATCH_SIZE));
            if (staged == null || staged.isEmpty()) {
                return;
            }
            String batchId = staged.getFirst().toString();
            Map<String, Long> deltas = new TreeMap<>();
            for (int index = 1; index < staged.size(); index += 2) {
                deltas.put(staged.get(index).toString(), Long.parseLong(staged.get(index + 1).toString()));
            }
            transactions.executeWithoutResult(status -> {
                try {
                    jdbc.update("INSERT INTO counter_batch_receipt(counter_name, batch_id) VALUES (?, ?)",
                            counterName, batchId);
                } catch (DuplicateKeyException alreadyApplied) {
                    return;
                }
                deltas.forEach(applyDelta);
            });
            redis.execute(ACK, List.of(pendingKey + ":inflight", pendingKey + ":batch"), batchId);
        } catch (Exception exception) {
            log.error("Counter flush retained for retry: counter={}", counterName, exception);
        }
    }

    public Map<String, Long> pendingDeltas(String counterName, String pendingKey, Collection<String> fields) {
        if (fields.isEmpty()) {
            return Map.of();
        }
        List<String> distinct = fields.stream().distinct().toList();
        List<?> snapshot = redis.execute(SNAPSHOT,
                List.of(pendingKey, pendingKey + ":inflight", pendingKey + ":batch"), distinct.toArray());
        if (snapshot == null || snapshot.isEmpty()) {
            return Map.of();
        }
        String batchId = snapshot.getFirst().toString();
        boolean includeInflight = !batchId.isEmpty() && jdbc.queryForObject(
                "SELECT COUNT(*) FROM counter_batch_receipt WHERE counter_name = ? AND batch_id = ?",
                Integer.class, counterName, batchId) == 0;
        Map<String, Long> result = new HashMap<>();
        for (int index = 0; index < distinct.size(); index++) {
            long delta = parseDelta(snapshot.get(1 + index * 2));
            if (includeInflight) {
                delta += parseDelta(snapshot.get(2 + index * 2));
            }
            result.put(distinct.get(index), delta);
        }
        return result;
    }

    private long parseDelta(Object value) {
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException invalidDelta) {
            return 0;
        }
    }
}
