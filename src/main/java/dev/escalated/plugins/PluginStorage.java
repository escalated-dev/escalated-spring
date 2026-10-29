package dev.escalated.plugins;

import dev.escalated.models.PluginStoreRecord;
import dev.escalated.repositories.PluginStoreRecordRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code ctx.store} and {@code ctx.config}: rows of {@code escalated_plugin_store}, keyed by the
 * plugin's name. Queries are evaluated in this process, as NestJS does, so the operators behave the
 * same on every database and a field name is only ever a path lookup, never SQL.
 */
final class PluginStorage {

    static final String CONFIG = "__config__";

    /** A dotted field name. Anything else in a filter is an error. */
    private static final Pattern FIELD = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$");
    private static final Set<String> OPERATORS = Set.of("$gt", "$gte", "$lt", "$lte", "$ne", "$in", "$nin");

    private final PluginStoreRecordRepository records;
    private final TransactionTemplate transactions;

    PluginStorage(PluginStoreRecordRepository records, TransactionTemplate transactions) {
        this.records = records;
        this.transactions = transactions;
    }

    private PluginStoreRecord find(String plugin, String collection, String key) {
        return records.findFirstByPluginAndCollectionAndKeyOrderByIdAsc(plugin, collection, key).orElse(null);
    }

    private static void checkCollection(String collection) {
        if (collection == null || collection.isEmpty() || collection.length() > 100) {
            throw new IllegalArgumentException("a store collection is a name of 1 to 100 characters");
        }
    }

    private static void checkKey(String key) {
        if (key != null && key.length() > 191) {
            throw new IllegalArgumentException("a store key is at most 191 characters");
        }
    }

    Map<String, Object> configAll(String plugin) {
        PluginStoreRecord row = find(plugin, CONFIG, CONFIG);
        Map<String, Object> data = row == null ? null : PluginJson.record(PluginJson.read(row.getData()));
        return data == null ? new LinkedHashMap<>() : data;
    }

    Object configGet(String plugin, String key) {
        return configAll(plugin).get(key);
    }

    void configSet(String plugin, Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("ctx.config.set requires an object");
        }
        Map<String, Object> clean = PluginJson.plainRecord(data);
        synchronized (this) {
            transactions.executeWithoutResult(status -> {
                Map<String, Object> merged = configAll(plugin);
                merged.putAll(clean);
                write(plugin, CONFIG, CONFIG, merged);
            });
        }
    }

    Object get(String plugin, String collection, String key) {
        PluginStoreRecord row = find(plugin, collection, key);
        return row == null ? null : PluginJson.read(row.getData());
    }

    void set(String plugin, String collection, String key, Object value) {
        checkCollection(collection);
        checkKey(key);
        Object clean = PluginJson.plain(value);
        synchronized (this) {
            transactions.executeWithoutResult(status -> write(plugin, collection, key, clean));
        }
    }

    private void write(String plugin, String collection, String key, Object value) {
        PluginStoreRecord row = find(plugin, collection, key);
        if (row == null) {
            row = new PluginStoreRecord();
            row.setPlugin(plugin);
            row.setCollection(collection);
            row.setKey(key);
        }
        row.setData(PluginJson.write(value));
        records.save(row);
    }

    private static Map<String, Object> present(PluginStoreRecord row, Object data) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> fields = PluginJson.record(data);
        if (fields != null) {
            out.putAll(fields);
        }
        out.put("_id", row.getId());
        return out;
    }

    Map<String, Object> insert(String plugin, String collection, Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("ctx.store.insert requires an object");
        }
        checkCollection(collection);
        Map<String, Object> clean = PluginJson.plainRecord(data);
        String key = clean.get("key") == null ? null : PluginJson.string(clean, "key");
        checkKey(key);
        PluginStoreRecord row = new PluginStoreRecord();
        row.setPlugin(plugin);
        row.setCollection(collection);
        row.setKey(key);
        row.setData(PluginJson.write(clean));
        return present(records.save(row), clean);
    }

    Map<String, Object> update(String plugin, String collection, String key, Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("ctx.store.update requires an object");
        }
        Map<String, Object> clean = PluginJson.plainRecord(data);
        synchronized (this) {
            return transactions.execute(status -> {
                PluginStoreRecord row = find(plugin, collection, key);
                if (row == null) {
                    throw new IllegalArgumentException("no " + collection + " record with key " + key);
                }
                Map<String, Object> merged = new LinkedHashMap<>();
                Map<String, Object> existing = PluginJson.record(PluginJson.read(row.getData()));
                if (existing != null) {
                    merged.putAll(existing);
                }
                merged.putAll(clean);
                row.setData(PluginJson.write(merged));
                return present(records.save(row), merged);
            });
        }
    }

    void delete(String plugin, String collection, String key) {
        transactions.executeWithoutResult(
                status -> records.deleteAll(records.findByPluginAndCollectionAndKey(plugin, collection, key)));
    }

    /**
     * The records matching {@code filter}: equality (an array field matches when it contains the
     * value) and {@code $gt $gte $lt $lte $ne $in $nin}, on dotted field names. Options:
     * {@code limit}, {@code orderBy}, {@code order} ({@code asc} or {@code desc}).
     */
    List<Map<String, Object>> query(
            String plugin, String collection, Map<String, Object> filter, Map<String, Object> options) {
        Map<String, Object> conditions = filter == null ? Map.of() : PluginJson.plainRecord(filter);
        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            if (!FIELD.matcher(condition.getKey()).matches()) {
                throw new IllegalArgumentException("invalid store field name: " + condition.getKey());
            }
            Map<String, Object> ops = PluginJson.record(condition.getValue());
            if (ops != null) {
                for (String op : ops.keySet()) {
                    if (!OPERATORS.contains(op)) {
                        throw new IllegalArgumentException("unsupported store operator: " + op);
                    }
                }
            }
        }
        Map<String, Object> opts = options == null ? Map.of() : options;
        String orderBy = PluginJson.string(opts, "orderBy");
        if (orderBy != null && !orderBy.isEmpty() && !FIELD.matcher(orderBy).matches()) {
            throw new IllegalArgumentException("invalid store field name: " + orderBy);
        }

        record Found(PluginStoreRecord row, Object data) {
        }
        List<Found> found = new ArrayList<>();
        for (PluginStoreRecord row : records.findByPluginAndCollectionOrderByIdAsc(plugin, collection)) {
            Object data = PluginJson.read(row.getData());
            if (matches(data, conditions)) {
                found.add(new Found(row, data));
            }
        }
        if (orderBy != null && !orderBy.isEmpty()) {
            Comparator<Found> order = (a, b) -> compareSortKeys(fieldOf(a.data(), orderBy), fieldOf(b.data(), orderBy));
            found.sort("desc".equalsIgnoreCase(PluginJson.string(opts, "order")) ? order.reversed() : order);
        }
        Long limit = PluginJson.id(opts.get("limit"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Found item : found) {
            if (limit != null && out.size() >= Math.max(0, limit)) {
                break;
            }
            out.add(present(item.row(), item.data()));
        }
        return out;
    }

    private static boolean matches(Object data, Map<String, Object> conditions) {
        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            Object value = fieldOf(data, condition.getKey());
            Map<String, Object> ops = PluginJson.record(condition.getValue());
            if (ops != null) {
                for (Map.Entry<String, Object> op : ops.entrySet()) {
                    if (!matchOp(value, op.getKey(), op.getValue())) {
                        return false;
                    }
                }
            } else if (!contains(value, condition.getValue())) {
                return false;
            }
        }
        return true;
    }

    static Object fieldOf(Object data, String field) {
        Object value = data;
        for (String part : field.split("\\.")) {
            Map<String, Object> map = PluginJson.record(value);
            if (map == null) {
                return null;
            }
            value = map.get(part);
        }
        return value;
    }

    /** Equality as escalated-laravel's whereJsonContains: an array field contains the value. */
    private static boolean contains(Object value, Object wanted) {
        if (value instanceof List<?> list) {
            if (wanted instanceof List<?> wantedList) {
                return wantedList.stream().allMatch(item -> anySame(list, item));
            }
            return anySame(list, wanted);
        }
        return PluginJson.same(value, wanted);
    }

    private static boolean anySame(List<?> list, Object value) {
        return list.stream().anyMatch(item -> PluginJson.same(item, value));
    }

    private static boolean matchOp(Object value, String op, Object operand) {
        switch (op) {
            case "$ne":
                return !PluginJson.same(value, operand);
            case "$in":
            case "$nin":
                List<?> list = operand instanceof List<?> l ? l : List.of(operand);
                boolean in = anySame(list, value);
                return "$in".equals(op) == in;
            default:
                Integer c = compare(value, operand);
                if (c == null) {
                    return false;
                }
                return switch (op) {
                    case "$gt" -> c > 0;
                    case "$gte" -> c >= 0;
                    case "$lt" -> c < 0;
                    default -> c <= 0;
                };
        }
    }

    private static Integer compare(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return ((BigDecimal) PluginJson.canonical(x)).compareTo((BigDecimal) PluginJson.canonical(y));
        }
        if (a instanceof String x && b instanceof String y) {
            return Integer.signum(x.compareTo(y));
        }
        return null;
    }

    /** Numbers first, then strings, then everything else. */
    private static int compareSortKeys(Object a, Object b) {
        int rankA = a instanceof Number ? 0 : a instanceof String ? 1 : 2;
        int rankB = b instanceof Number ? 0 : b instanceof String ? 1 : 2;
        if (rankA != rankB) {
            return rankA - rankB;
        }
        Integer c = compare(a, b);
        return c == null ? 0 : c;
    }
}
