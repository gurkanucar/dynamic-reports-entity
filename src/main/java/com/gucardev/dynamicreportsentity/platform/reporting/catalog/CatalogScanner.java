package com.gucardev.dynamicreportsentity.platform.reporting.catalog;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportField;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportHidden;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportReference;
import com.gucardev.dynamicreportsentity.platform.reporting.Reportable;
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
import java.util.regex.Pattern;

/** Reads the reporting annotations and turns them into a {@link ReportCatalog}. Fails fast on mistakes. */
final class CatalogScanner {

    private CatalogScanner() {
    }

    static ReportCatalog scan(Collection<Class<?>> candidates) {
        Map<String, Class<?>> classesByName = new LinkedHashMap<>();
        candidates.stream()
                .filter(type -> type.isAnnotationPresent(Reportable.class))
                .sorted(Comparator.comparing(CatalogScanner::entityName))
                .forEach(type -> {
                    Class<?> previous = classesByName.putIfAbsent(entityName(type), type);
                    if (previous != null) {
                        throw new IllegalStateException("Duplicate @Reportable name '" + entityName(type)
                                + "' on " + previous.getName() + " and " + type.getName());
                    }
                });

        Map<String, Draft> drafts = new LinkedHashMap<>();
        classesByName.forEach((name, type) -> drafts.put(name, draft(name, type)));
        classesByName.forEach((name, type) -> addReferences(drafts.get(name), type, drafts));

        return new ReportCatalog(drafts.values().stream().map(Draft::freeze).toList());
    }

    private static Draft draft(String name, Class<?> type) {
        Reportable reportable = type.getAnnotation(Reportable.class);
        boolean includeAll = reportable.includeAllFields();
        Draft draft = new Draft(name, reportable.label(), tableName(type));
        for (Field field : persistentFields(type)) {
            String column = columnName(field);
            draft.columns.put(field.getName(), column);
            if (field.isAnnotationPresent(Id.class)) {
                draft.idColumns.add(column);
            }
            ReportField reportField = field.getAnnotation(ReportField.class);
            if (reportField != null) {
                draft.fields.put(field.getName(), new CatalogField(field.getName(), reportField.label(), column,
                        FieldType.of(field.getType()), reportField.authority().isBlank() ? null : reportField.authority()));
            } else if (includeAll && !field.isAnnotationPresent(ReportHidden.class)
                    && !field.isAnnotationPresent(Id.class) && !field.isAnnotationPresent(ReportReference.class)) {
                draft.fields.put(field.getName(), new CatalogField(field.getName(), humanize(field.getName()), column,
                        FieldType.of(field.getType()), null));
            }
        }
        if (draft.idColumns.isEmpty()) {
            throw new IllegalStateException("@Reportable entity " + type.getName()
                    + " needs @Id field(s) (@EmbeddedId is not supported, use @IdClass)");
        }
        exposeId(draft, type);
        return draft;
    }

    /**
     * A single-column primary key is always reportable (label "ID"), listed first. It identifies rows
     * (COUNT_DISTINCT, filters by id) and makes every reference value reachable as a path, e.g.
     * {@code department.id} instead of a raw {@code departmentId} column.
     */
    private static void exposeId(Draft draft, Class<?> type) {
        List<Field> idFields = persistentFields(type).stream().filter(field -> field.isAnnotationPresent(Id.class)).toList();
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
            ReportReference reference = field.getAnnotation(ReportReference.class);
            if (reference == null) {
                continue;
            }
            String where = type.getSimpleName() + "." + field.getName();
            Draft target = drafts.get(reference.target());
            if (target == null) {
                throw new IllegalStateException("@ReportReference on " + where + " points at unknown reportable entity '"
                        + reference.target() + "'. Known entities: " + drafts.keySet());
            }
            if (target.idColumns.size() != 1) {
                throw new IllegalStateException("@ReportReference on " + where + " points at " + target.name
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
                throw new IllegalStateException("Name '" + navigation.name() + "' is used twice on reportable entity "
                        + name + "; set name/inverse on @ReportReference to disambiguate");
            }
            navigations.put(navigation.name(), navigation);
        }

        ReportableEntity freeze() {
            return new ReportableEntity(name, label, table, List.copyOf(idColumns),
                    Collections.unmodifiableMap(fields), Collections.unmodifiableMap(navigations),
                    Collections.unmodifiableMap(columns), Collections.unmodifiableMap(ownerColumns));
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
        String name = type.getAnnotation(Reportable.class).name();
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
     * Spring Boot's default physical naming strategy (CamelCaseToUnderscoresNamingStrategy) applies
     * to explicit names as well, so snake_case is applied in every case to stay in sync with it.
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

    /** Only plain identifiers may ever reach SQL text. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    /** {@code fullName} → {@code full_name}; rejects anything that is not a plain SQL identifier. */
    static String snakeCase(String name) {
        String identifier = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalStateException("'" + name + "' is not a safe SQL identifier for reporting"
                    + " (letters, digits and underscores only; quoted or schema-qualified names are not supported)");
        }
        return identifier;
    }
}
