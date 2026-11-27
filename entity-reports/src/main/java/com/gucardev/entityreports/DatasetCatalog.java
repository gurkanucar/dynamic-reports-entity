package com.gucardev.entityreports;

import com.gucardev.entityreports.Dataset.FieldType;
import com.gucardev.entityreports.Dataset.Viewer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The "dataset map": which entities, fields and navigations analysts may use. Built once at startup from the
 * annotations ({@link DatasetEntity}, {@link DatasetField}, {@link DatasetReference}, {@link DatasetHidden}) on the
 * host's JPA entities; mistakes fail the startup with a clear message.
 * <p>
 * The auto-configuration finds the entities through the JPA metamodel; without JPA (or in tests) build it with
 * {@link #of(Collection)}. Table and column names follow Spring Boot's default naming (camelCase → snake_case).
 */
public final class DatasetCatalog {

    /**
     * An entity exposed to the dataset builder.
     *
     * @param idColumns    primary key column(s); more than one for composite keys (e.g. pure junction tables)
     * @param fields       dataset-ready fields by name
     * @param navigations  outgoing navigations by name (references and their inverses)
     * @param columns      column of every persistent field by field name (used by row restrictions)
     * @param ownerColumns for each owner entity name, the column on this entity that points at it
     */
    public record CatalogEntity(String name, String label, String table, List<String> idColumns,
                                Map<String, CatalogField> fields, Map<String, Navigation> navigations,
                                Map<String, String> columns, Map<String, String> ownerColumns) {
    }

    /**
     * A dataset-ready field.
     *
     * @param name      Java field name, used in paths ({@code manager.fullName})
     * @param column    physical column name
     * @param authority authority required to use the field, or {@code null} if unrestricted
     */
    public record CatalogField(String name, String label, String column, FieldType type, String authority) {

        public boolean isVisibleTo(Viewer viewer) {
            return authority == null || viewer.unrestricted() || viewer.hasAuthority(authority);
        }
    }

    /**
     * A named way to get from one entity to another, derived from a {@link DatasetReference}. Joining it produces
     * {@code joined.joinColumn = current.parentColumn}.
     *
     * @param toMany       whether one current row may match many target rows (fan-out)
     * @param joinColumn   column on the target entity
     * @param parentColumn column on the current entity
     */
    public record Navigation(String name, String label, String targetEntity, boolean toMany,
                             String joinColumn, String parentColumn) {
    }

    private final Map<String, CatalogEntity> entities;

    private DatasetCatalog(List<CatalogEntity> entities) {
        Map<String, CatalogEntity> byName = new LinkedHashMap<>();
        entities.forEach(entity -> byName.put(entity.name(), entity));
        this.entities = Collections.unmodifiableMap(byName);
    }

    /** Builds a catalog from the given classes; classes without {@link DatasetEntity} are ignored. */
    public static DatasetCatalog of(Collection<Class<?>> candidates) {
        return new DatasetCatalog(Scanner.scan(candidates));
    }

    public List<CatalogEntity> entities() {
        return List.copyOf(entities.values());
    }

    public Optional<CatalogEntity> entity(String name) {
        return Optional.ofNullable(entities.get(name));
    }

    // ==== reading the annotations =========================================================================

    private static final class Scanner {

        /** Only plain identifiers may ever reach SQL text. */
        private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

        static List<CatalogEntity> scan(Collection<Class<?>> candidates) {
            Map<String, Class<?>> classesByName = new LinkedHashMap<>();
            candidates.stream()
                    .filter(type -> type.isAnnotationPresent(DatasetEntity.class))
                    .sorted(Comparator.comparing(Scanner::entityName))
                    .forEach(type -> {
                        Class<?> previous = classesByName.putIfAbsent(entityName(type), type);
                        if (previous != null) {
                            throw new IllegalStateException("Duplicate @DatasetEntity name '" + entityName(type)
                                    + "' on " + previous.getName() + " and " + type.getName());
                        }
                    });

            Map<String, Draft> drafts = new LinkedHashMap<>();
            classesByName.forEach((name, type) -> drafts.put(name, draft(name, type)));
            classesByName.forEach((name, type) -> addReferences(drafts.get(name), type, drafts));
            return drafts.values().stream().map(Draft::freeze).toList();
        }

        private static Draft draft(String name, Class<?> type) {
            DatasetEntity annotation = type.getAnnotation(DatasetEntity.class);
            boolean includeAll = annotation.includeAllFields();
            Draft draft = new Draft(name, annotation.label(), tableName(type));
            for (Field field : persistentFields(type)) {
                String column = columnName(field);
                draft.columns.put(field.getName(), column);
                if (field.isAnnotationPresent(Id.class)) {
                    draft.idColumns.add(column);
                }
                DatasetField datasetField = field.getAnnotation(DatasetField.class);
                if (datasetField != null) {
                    draft.fields.put(field.getName(), new CatalogField(field.getName(), datasetField.label(), column,
                            FieldType.of(field.getType()),
                            datasetField.authority().isBlank() ? null : datasetField.authority()));
                } else if (includeAll && !field.isAnnotationPresent(DatasetHidden.class)
                        && !field.isAnnotationPresent(Id.class) && !field.isAnnotationPresent(DatasetReference.class)) {
                    draft.fields.put(field.getName(), new CatalogField(field.getName(), humanize(field.getName()),
                            column, FieldType.of(field.getType()), null));
                }
            }
            if (draft.idColumns.isEmpty()) {
                throw new IllegalStateException("@DatasetEntity entity " + type.getName()
                        + " needs @Id field(s) (@EmbeddedId is not supported, use @IdClass)");
            }
            exposeId(draft, type);
            return draft;
        }

        /**
         * A single-column primary key is always dataset-ready (label "ID"), listed first. It identifies rows
         * (COUNT_DISTINCT, filters by id) and makes every reference value reachable as a path, e.g.
         * {@code department.id} instead of a raw {@code departmentId} column.
         */
        private static void exposeId(Draft draft, Class<?> type) {
            List<Field> idFields = persistentFields(type).stream().filter(f -> f.isAnnotationPresent(Id.class)).toList();
            if (idFields.size() != 1 || draft.fields.containsKey(idFields.getFirst().getName())) {
                return;
            }
            Field id = idFields.getFirst();
            Map<String, CatalogField> withId = new LinkedHashMap<>();
            withId.put(id.getName(), new CatalogField(id.getName(), "ID", columnName(id), FieldType.of(id.getType()), null));
            withId.putAll(draft.fields);
            draft.fields.clear();
            draft.fields.putAll(withId);
        }

        private static void addReferences(Draft source, Class<?> type, Map<String, Draft> drafts) {
            for (Field field : persistentFields(type)) {
                DatasetReference reference = field.getAnnotation(DatasetReference.class);
                if (reference == null) {
                    continue;
                }
                String where = type.getSimpleName() + "." + field.getName();
                Draft target = drafts.get(reference.target());
                if (target == null) {
                    throw new IllegalStateException("@DatasetReference on " + where + " points at unknown dataset-ready"
                            + " entity '" + reference.target() + "'. Known entities: " + drafts.keySet());
                }
                if (target.idColumns.size() != 1) {
                    throw new IllegalStateException("@DatasetReference on " + where + " points at " + target.name
                            + ", which has a composite key; only single-column keys can be referenced");
                }
                String column = columnName(field);
                String targetId = target.idColumns.getFirst();

                source.addNavigation(new Navigation(
                        reference.name().isBlank() ? navigationName(field) : reference.name(),
                        reference.label().isBlank() ? target.label : reference.label(),
                        target.name, false, targetId, column));

                if (!reference.inverse().isBlank()) {
                    target.addNavigation(new Navigation(reference.inverse(),
                            reference.inverseLabel().isBlank() ? source.label : reference.inverseLabel(),
                            source.name, !reference.oneToOne(), column, targetId));
                }
                if (reference.owner()) {
                    source.ownerColumns.put(target.name, column);
                }
            }
        }

        /** Fields of the class and its superclasses (e.g. {@code @MappedSuperclass}), superclass first. */
        private static List<Field> persistentFields(Class<?> type) {
            LinkedList<Field> fields = new LinkedList<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                Field[] declared = current.getDeclaredFields();
                for (int i = declared.length - 1; i >= 0; i--) {
                    Field field = declared[i];
                    int modifiers = field.getModifiers();
                    if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()
                            && !field.isAnnotationPresent(Transient.class)) {
                        fields.addFirst(field);
                    }
                }
            }
            return fields;
        }

        private static String entityName(Class<?> type) {
            String name = type.getAnnotation(DatasetEntity.class).name();
            return name.isBlank() ? type.getSimpleName() : name;
        }

        /** {@code validityMonths} → {@code Validity Months} (label for includeAllFields). */
        static String humanize(String name) {
            String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
            return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
        }

        /** {@code managerId → manager}; other names get a {@code Ref} suffix to avoid clashing with fields. */
        private static String navigationName(Field field) {
            String name = field.getName();
            return name.endsWith("Id") && name.length() > 2 ? name.substring(0, name.length() - 2) : name + "Ref";
        }

        /*
         * Spring Boot's default physical naming strategy (CamelCaseToUnderscoresNamingStrategy) applies to explicit
         * names as well, so snake_case is applied in every case to stay in sync with it.
         */

        private static String tableName(Class<?> type) {
            Table table = type.getAnnotation(Table.class);
            if (table != null && !table.name().isBlank()) {
                return snakeCase(table.name());
            }
            Entity entity = type.getAnnotation(Entity.class);
            return snakeCase(entity != null && !entity.name().isBlank() ? entity.name() : type.getSimpleName());
        }

        private static String columnName(Field field) {
            Column column = field.getAnnotation(Column.class);
            return snakeCase(column != null && !column.name().isBlank() ? column.name() : field.getName());
        }

        /** {@code fullName} → {@code full_name}; rejects anything that is not a plain SQL identifier. */
        static String snakeCase(String name) {
            String identifier = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
                throw new IllegalStateException("'" + name + "' is not a safe SQL identifier for datasets"
                        + " (letters, digits and underscores only; quoted or schema-qualified names are not supported)");
            }
            return identifier;
        }
    }

    /** Mutable state while scanning; navigations of an entity can be added by other entities (inverses). */
    private static final class Draft {
        final String name;
        final String label;
        final String table;
        final List<String> idColumns = new ArrayList<>();
        final Map<String, CatalogField> fields = new LinkedHashMap<>();
        final Map<String, Navigation> navigations = new LinkedHashMap<>();
        final Map<String, String> columns = new LinkedHashMap<>();
        final Map<String, String> ownerColumns = new LinkedHashMap<>();

        Draft(String name, String label, String table) {
            this.name = name;
            this.label = label;
            this.table = table;
        }

        void addNavigation(Navigation navigation) {
            if (fields.containsKey(navigation.name()) || navigations.containsKey(navigation.name())) {
                throw new IllegalStateException("Name '" + navigation.name() + "' is used twice on dataset-ready entity "
                        + name + "; set name/inverse on @DatasetReference to disambiguate");
            }
            navigations.put(navigation.name(), navigation);
        }

        CatalogEntity freeze() {
            return new CatalogEntity(name, label, table, List.copyOf(idColumns),
                    Collections.unmodifiableMap(fields), Collections.unmodifiableMap(navigations),
                    Collections.unmodifiableMap(columns), Collections.unmodifiableMap(ownerColumns));
        }
    }
}
