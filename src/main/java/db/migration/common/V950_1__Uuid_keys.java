package db.migration.common;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the numeric keys of flows, runs, surveys, responses, approvals, mail templates, saved datasets and assistant
 * requests into UUIDs, so ids in URLs and APIs cannot be guessed or counted. One vendor-neutral Java migration (H2
 * and PostgreSQL), because the same old → new ids must also be rewritten inside text: source ids ("flow-step" + step
 * run id), step references, "survey:&lt;id&gt;" topics and the JSON of flow definitions, run payloads and step outputs.
 * <p>
 * Existing rows get version 7 UUIDs (time-ordered, 74 random bits) in the order of their old ids, dated just before
 * the migration, so "order by id" keeps meaning creation order and rows created later sort after them. A reference
 * to a row that no longer exists (a run of a deleted flow) gets a name-based UUID, so it still points nowhere.
 * <p>
 * Unfinished event publications of the changed modules are dropped: their serialized events carry the old ids.
 */
public class V950_1__Uuid_keys extends BaseJavaMigration {

    /** Tables whose {@code id} becomes a UUID, parents before children. */
    private static final List<String> TABLES = List.of(
            "flow_definition", "flow_schedule", "flow_trigger", "flow_run", "flow_step_run", "flow_artifact",
            "flow_mail_delivery", "flow_run_event", "flow_trigger_event",
            "survey", "survey_response", "survey_response_note",
            "approval_group", "approval_request", "notification_mail_template", "dataset_suggestion");

    /** Foreign key columns: table.column → the table they point at. */
    private static final List<String[]> REFERENCES = List.of(
            ref("flow_run", "flow_id", "flow_definition", true), ref("flow_run", "schedule_id", "flow_schedule", false),
            ref("flow_step_run", "run_id", "flow_run", true),
            ref("flow_schedule", "flow_id", "flow_definition", true), ref("flow_schedule", "last_run_id", "flow_run", false),
            ref("flow_trigger", "flow_id", "flow_definition", true), ref("flow_trigger", "last_run_id", "flow_run", false),
            ref("flow_trigger_event", "trigger_id", "flow_trigger", true), ref("flow_trigger_event", "run_id", "flow_run", false),
            ref("flow_artifact", "run_id", "flow_run", true), ref("flow_artifact", "step_run_id", "flow_step_run", true),
            ref("flow_mail_delivery", "run_id", "flow_run", true),
            ref("flow_mail_delivery", "step_run_id", "flow_step_run", true),
            ref("flow_mail_delivery", "body_file_id", "flow_artifact", false),
            ref("flow_run_event", "run_id", "flow_run", true),
            ref("flow_step_reference", "flow_id", "flow_definition", true),
            ref("survey_response", "survey_id", "survey", true),
            ref("survey_response_note", "survey_id", "survey", true),
            ref("survey_response_note", "response_id", "survey_response", true),
            ref("survey_invitation", "survey_id", "survey", true),
            ref("survey_invitation", "response_id", "survey_response", false),
            ref("approval_group_member", "group_id", "approval_group", true),
            ref("approval_request", "group_id", "approval_group", true));

    /** JSON keys holding ids (flow definitions, trigger payloads, step outputs) → the table they point at. */
    private static final Map<String, String> JSON_KEYS = Map.of(
            "templateId", "notification_mail_template", "groupId", "approval_group", "surveyId", "survey",
            "responseId", "survey_response", "requestId", "approval_request", "artifactId", "flow_artifact",
            "bodyFileId", "flow_artifact", "runId", "flow_run", "flowId", "flow_definition");

    /** Step reference kinds whose ref_id is a numeric id. */
    private static final Map<String, String> REFERENCE_KINDS = Map.of(
            "mail-template", "notification_mail_template", "approval-group", "approval_group", "survey", "survey");

    /** Constraints and indexes on the changed columns, dropped first and created again on the UUID columns. */
    private static final List<String> DROP = List.of(
            "alter table flow_mail_delivery drop constraint if exists uk_mail_delivery_step",
            "alter table flow_trigger_event drop constraint if exists uk_flow_trigger_event",
            "alter table survey_response drop constraint if exists uk_survey_response_respondent",
            "drop index if exists ix_flow_artifact_run", "drop index if exists ix_flow_artifact_step",
            "drop index if exists ix_flow_run_flow", "drop index if exists ix_flow_run_event_run",
            "drop index if exists ix_flow_schedule_flow", "drop index if exists ix_flow_step_run_run",
            "drop index if exists ix_flow_trigger_flow", "drop index if exists ix_approval_request_group",
            "drop index if exists ix_survey_response_survey", "drop index if exists ix_survey_response_follow",
            "drop index if exists ix_survey_note_response");
    private static final List<String> CREATE = List.of(
            "alter table flow_mail_delivery add constraint uk_mail_delivery_step unique (step_run_id)",
            "alter table flow_trigger_event add constraint uk_flow_trigger_event unique (trigger_id, event_key)",
            "alter table survey_response add constraint uk_survey_response_respondent unique (survey_id, respondent_key)",
            "alter table flow_step_reference add constraint pk_flow_step_reference primary key (flow_id, kind, ref_id)",
            "alter table flow_step_reference add constraint fk_flow_step_reference_flow foreign key (flow_id) "
                    + "references flow_definition (id)",
            "alter table approval_group_member add constraint fk_approval_group_member_group foreign key (group_id) "
                    + "references approval_group (id)",
            "create index ix_flow_artifact_run on flow_artifact (run_id)",
            "create index ix_flow_artifact_step on flow_artifact (step_run_id)",
            "create index ix_flow_run_flow on flow_run (flow_id, status)",
            "create index ix_flow_run_event_run on flow_run_event (run_id, id)",
            "create index ix_flow_schedule_flow on flow_schedule (flow_id)",
            "create index ix_flow_step_run_run on flow_step_run (run_id)",
            "create index ix_flow_trigger_flow on flow_trigger (flow_id)",
            "create index ix_approval_request_group on approval_request (group_id, status, created_at)",
            "create index ix_survey_response_survey on survey_response (survey_id, submitted_at)",
            "create index ix_survey_response_follow on survey_response (survey_id, follow_status)",
            "create index ix_survey_note_response on survey_response_note (response_id)");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final java.util.regex.Pattern UNQUOTED_RUN_ID =
            java.util.regex.Pattern.compile("(:\\s*)\\{\\{run\\.id}}(?!\")");
    private static final SecureRandom RANDOM = new SecureRandom();

    private boolean postgres;
    /** table → old id → new id */
    private final Map<String, Map<Long, UUID>> ids = new HashMap<>();

    private static String[] ref(String table, String column, String target, boolean required) {
        return new String[]{table, column, target, Boolean.toString(required)};
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");

        for (String table : TABLES) {
            assignIds(connection, table);
        }
        for (String[] reference : REFERENCES) {
            mapColumn(connection, reference[0], reference[1], reference[2]);
        }
        rewriteText(connection);

        dropForeignKeysAndPrimaryKeys(connection);
        for (String statement : DROP) {
            execute(connection, statement);
        }
        for (String table : TABLES) {
            execute(connection, "alter table " + table + " drop column id");
            rename(connection, table, "new_id", "id");
            execute(connection, "alter table " + table + " alter column id set not null");
            execute(connection, "alter table " + table + " add constraint pk_" + table + " primary key (id)");
        }
        for (String[] reference : REFERENCES) {
            execute(connection, "alter table " + reference[0] + " drop column " + reference[1]);
            rename(connection, reference[0], "new_" + reference[1], reference[1]);
            if (Boolean.parseBoolean(reference[3])) {
                execute(connection, "alter table " + reference[0] + " alter column " + reference[1] + " set not null");
            }
        }
        for (String statement : CREATE) {
            execute(connection, statement);
        }
        savedDatasetIds(connection);
        execute(connection, "delete from event_publication where completion_date is null and ("
                + "event_type like '%.platform.flow%' or event_type like '%.platform.survey.%' "
                + "or event_type like '%.platform.approval.%')");
    }

    // ---- ids -------------------------------------------------------------------------------------------------

    /** Adds {@code new_id} and fills it with time-ordered UUIDs in the order of the old ids. */
    private void assignIds(Connection connection, String table) throws SQLException {
        execute(connection, "alter table " + table + " add column new_id uuid");
        List<Long> old = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select id from " + table + " order by id")) {
            while (rows.next()) {
                old.add(rows.getLong(1));
            }
        }
        Map<Long, UUID> map = new LinkedHashMap<>();
        long start = System.currentTimeMillis() - old.size() - 1;
        try (PreparedStatement update = connection.prepareStatement("update " + table + " set new_id = ? where id = ?")) {
            for (int i = 0; i < old.size(); i++) {
                UUID id = version7(start + i);
                map.put(old.get(i), id);
                update.setObject(1, id);
                update.setLong(2, old.get(i));
                update.addBatch();
                if (i % 500 == 499) {
                    update.executeBatch();
                }
            }
            update.executeBatch();
        }
        ids.put(table, map);
    }

    /** Adds {@code new_<column>} holding the UUID of the row the old value pointed at. */
    private void mapColumn(Connection connection, String table, String column, String target) throws SQLException {
        execute(connection, "alter table " + table + " add column new_" + column + " uuid");
        List<Long> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select distinct " + column + " from " + table
                     + " where " + column + " is not null")) {
            while (rows.next()) {
                values.add(rows.getLong(1));
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
                "update " + table + " set new_" + column + " = ? where " + column + " = ?")) {
            for (Long value : values) {
                update.setObject(1, newId(target, value));
                update.setLong(2, value);
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private UUID newId(String table, long old) {
        UUID id = ids.getOrDefault(table, Map.of()).get(old);
        return id != null ? id : UUID.nameUUIDFromBytes((table + ":" + old).getBytes(StandardCharsets.UTF_8));
    }

    /** RFC 9562 version 7: 48-bit millisecond timestamp, 74 random bits. */
    static UUID version7(long millis) {
        long msb = (millis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    // ---- ids inside text -------------------------------------------------------------------------------------

    private void rewriteText(Connection connection) throws SQLException {
        // "flow-step" sources are step runs
        for (String table : List.of("approval_request", "survey_invitation", "survey_response")) {
            String key = table.equals("survey_invitation") ? "token" : "id";
            rewriteColumn(connection, table, key, "source_id", "source_type = 'flow-step'",
                    value -> numeric(value) ? newId("flow_step_run", Long.parseLong(value)).toString() : value);
        }
        for (Map.Entry<String, String> kind : REFERENCE_KINDS.entrySet()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "update flow_step_reference set ref_id = ? where kind = ? and ref_id = ?")) {
                for (String value : distinct(connection, "select distinct ref_id from flow_step_reference where kind = '"
                        + kind.getKey() + "'")) {
                    if (numeric(value)) {
                        update.setString(1, newId(kind.getValue(), Long.parseLong(value)).toString());
                        update.setString(2, kind.getKey());
                        update.setString(3, value);
                        update.addBatch();
                    }
                }
                update.executeBatch();
            }
        }
        rewriteColumn(connection, "flow_trigger", "id", "topic", "topic like 'survey:%'", value -> {
            String id = value.substring("survey:".length());
            return numeric(id) ? "survey:" + newId("survey", Long.parseLong(id)) : value;
        });
        rewriteColumn(connection, "flow_definition", "id", "definition_json", null, this::rewriteJson);
        rewriteColumn(connection, "flow_run", "id", "definition_json", null, this::rewriteJson);
        rewriteColumn(connection, "flow_run", "id", "trigger_payload_json", null, this::rewriteJson);
        rewriteColumn(connection, "flow_step_run", "id", "output_json", null, this::rewriteJson);
    }

    private void rewriteColumn(Connection connection, String table, String key, String column, String where,
                               java.util.function.UnaryOperator<String> change) throws SQLException {
        Map<Object, String> changed = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select " + key + ", " + column + " from " + table
                     + " where " + column + " is not null" + (where == null ? "" : " and " + where))) {
            while (rows.next()) {
                String value = rows.getString(2);
                String next = change.apply(value);
                if (!next.equals(value)) {
                    changed.put(rows.getObject(1), next);
                }
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
                "update " + table + " set " + column + " = ? where " + key + " = ?")) {
            int count = 0;
            for (Map.Entry<Object, String> entry : changed.entrySet()) {
                update.setString(1, entry.getValue());
                update.setObject(2, entry.getKey());
                update.addBatch();
                if (++count % 500 == 0) {
                    update.executeBatch();
                }
            }
            update.executeBatch();
        }
    }

    /** Replaces numeric values of the id keys anywhere in the document; text that is not JSON stays as it is. */
    private String rewriteJson(String text) {
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (RuntimeException e) {
            return text;
        }
        return rewriteNode(root) ? JSON.writeValueAsString(root) : text;
    }

    private boolean rewriteNode(JsonNode node) {
        boolean changed = false;
        if (node instanceof ObjectNode object) {
            for (String name : Set.copyOf(object.propertyNames())) {
                JsonNode value = object.get(name);
                String table = JSON_KEYS.get(name);
                if (table != null && value.isIntegralNumber()) {
                    object.put(name, newId(table, value.asLong()).toString());
                    changed = true;
                } else if ("payload".equals(name) && value.isString()
                        && UNQUOTED_RUN_ID.matcher(value.asString()).find()) {
                    // Kafka payload templates wrote the numeric run id bare ({"run": {{run.id}}}); a UUID needs quotes
                    object.put(name, UNQUOTED_RUN_ID.matcher(value.asString()).replaceAll("$1\"{{run.id}}\""));
                    changed = true;
                } else {
                    changed |= rewriteNode(value);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode item : array) {
                changed |= rewriteNode(item);
            }
        }
        return changed;
    }

    // ---- schema ----------------------------------------------------------------------------------------------

    /** The two foreign keys Hibernate named, and the primary keys of the changed tables (names differ by vendor). */
    private void dropForeignKeysAndPrimaryKeys(Connection connection) throws SQLException {
        List<String> statements = new ArrayList<>();
        for (String table : List.of("flow_step_reference", "approval_group_member")) {
            try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, name(table))) {
                while (keys.next()) {
                    statements.add("alter table " + table + " drop constraint " + keys.getString("FK_NAME"));
                }
            }
        }
        List<String> withPrimaryKey = new ArrayList<>(TABLES);
        withPrimaryKey.add("flow_step_reference");
        for (String table : withPrimaryKey) {
            try (ResultSet keys = connection.getMetaData().getPrimaryKeys(null, null, name(table))) {
                if (keys.next()) {
                    statements.add("alter table " + table + " drop constraint " + keys.getString("PK_NAME"));
                }
            }
        }
        for (String statement : statements.stream().distinct().toList()) {
            execute(connection, statement);
        }
    }

    /** saved_dataset was keyed by its name; it gets an id like the rest (names stay unique: flows use them). */
    private void savedDatasetIds(Connection connection) throws SQLException {
        execute(connection, "alter table saved_dataset add column id uuid");
        List<String> names = distinct(connection, "select name from saved_dataset order by created_at, name");
        long start = System.currentTimeMillis() - names.size() - 1;
        try (PreparedStatement update = connection.prepareStatement("update saved_dataset set id = ? where name = ?")) {
            for (int i = 0; i < names.size(); i++) {
                update.setObject(1, version7(start + i));
                update.setString(2, names.get(i));
                update.addBatch();
            }
            update.executeBatch();
        }
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(null, null, name("saved_dataset"))) {
            if (keys.next()) {
                execute(connection, "alter table saved_dataset drop constraint " + keys.getString("PK_NAME"));
            }
        }
        execute(connection, "alter table saved_dataset alter column id set not null");
        execute(connection, "alter table saved_dataset add constraint pk_saved_dataset primary key (id)");
        execute(connection, "alter table saved_dataset add constraint uk_saved_dataset_name unique (name)");
    }

    private void rename(Connection connection, String table, String from, String to) throws SQLException {
        execute(connection, postgres
                ? "alter table " + table + " rename column " + from + " to " + to
                : "alter table " + table + " alter column " + from + " rename to " + to);
    }

    /** Metadata lookups want the name as the database stores it: upper case in H2, lower case in PostgreSQL. */
    private String name(String table) {
        return postgres ? table : table.toUpperCase();
    }

    private static List<String> distinct(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private static boolean numeric(String value) {
        return value != null && !value.isEmpty() && value.length() < 19 && value.chars().allMatch(Character::isDigit);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
