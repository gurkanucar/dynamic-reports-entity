package com.gucardev.dynamicreportsentity.reporting.query;

import com.gucardev.dynamicreportsentity.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.reporting.ReportField;
import com.gucardev.dynamicreportsentity.reporting.ReportReference;
import com.gucardev.dynamicreportsentity.reporting.ReportRowPolicy;
import com.gucardev.dynamicreportsentity.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.reporting.Reportable;
import com.gucardev.dynamicreportsentity.reporting.RowRestriction;
import com.gucardev.dynamicreportsentity.reporting.catalog.ReportCatalog;
import com.gucardev.dynamicreportsentity.reporting.query.ReportSort.Direction;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static com.gucardev.dynamicreportsentity.reporting.query.Aggregate.AVG;
import static com.gucardev.dynamicreportsentity.reporting.query.Aggregate.COUNT;
import static com.gucardev.dynamicreportsentity.reporting.query.Aggregate.MAX;
import static com.gucardev.dynamicreportsentity.reporting.query.Aggregate.SUM;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.BETWEEN;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.CONTAINS;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.EQ;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.GT;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.GTE;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.IN;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.IS_NULL;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.LAST_N_DAYS;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.NEXT_N_DAYS;
import static com.gucardev.dynamicreportsentity.reporting.query.FilterOperator.STARTS_WITH;
import static com.gucardev.dynamicreportsentity.reporting.query.ReportCondition.and;
import static com.gucardev.dynamicreportsentity.reporting.query.ReportCondition.or;
import static com.gucardev.dynamicreportsentity.reporting.query.ReportCondition.where;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportQueryBuilderTest {

    // ---- fixture: every relation kind --------------------------------------------------------
    //  Customer.referredBy        self-reference (inverse: referrals)
    //  CustomerAccount            one-to-one with Customer (inverse: account)
    //  PurchaseOrder.customer     many-to-one (inverse: orders = one-to-many)
    //  OrderLine                  many-to-many WITH fields between PurchaseOrder and Product
    //  ProductTag                 pure many-to-many junction, composite key
    //  Warehouse                  unrelated

    @Reportable(label = "Customer")
    @Table(name = "customer")
    static class Customer {
        @Id Long id;
        @ReportField(label = "Name") String name;
        @ReportField(label = "Region") String region;
        @ReportField(label = "Credit Limit", authority = "FINANCE") BigDecimal creditLimit;
        @ReportReference(target = "Customer", label = "Referred By", inverse = "referrals", inverseLabel = "Referrals")
        Long referredById;
        String email; // not annotated -> never reportable
    }

    @Reportable(label = "Account")
    static class CustomerAccount {
        @Id Long id;
        @ReportReference(target = "Customer", oneToOne = true, owner = true, inverse = "account") Long customerId;
        @ReportField(label = "Tier") String tier;
    }

    @Reportable(label = "Order")
    @Table(name = "purchase_order")
    static class PurchaseOrder {
        @Id Long id;
        @ReportReference(target = "Customer", owner = true, inverse = "orders", inverseLabel = "Orders") Long customerId;
        @ReportField(label = "Order Date") LocalDate orderDate;
        @ReportField(label = "Total") BigDecimal total;
    }

    @Reportable(label = "Order Line")
    static class OrderLine {
        @Id Long id;
        @ReportReference(target = "PurchaseOrder", inverse = "lines") Long orderId;
        @ReportReference(target = "Product", inverse = "orderLines") Long productId;
        @ReportField(label = "Quantity") Integer quantity;
    }

    @Reportable(label = "Product")
    static class Product {
        @Id Long id;
        @ReportField(label = "Name") String name;
    }

    @Reportable(label = "Tag")
    static class Tag {
        @Id Long id;
        @ReportField(label = "Name") String name;
    }

    @Reportable(label = "Product Tag")
    static class ProductTag {
        @Id @ReportReference(target = "Product", inverse = "tags") Long productId;
        @Id @ReportReference(target = "Tag", inverse = "products") Long tagId;
    }

    @Reportable(label = "Warehouse")
    static class Warehouse {
        @Id Long id;
        @ReportField(label = "City") String city;
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    private static final ReportViewer USER = new ReportViewer("user", Set.of());
    private static final ReportViewer FINANCE_USER = new ReportViewer("finance", Set.of("FINANCE"));

    private final ReportCatalog catalog = ReportCatalog.of(List.of(Customer.class, CustomerAccount.class,
            PurchaseOrder.class, OrderLine.class, Product.class, Tag.class, ProductTag.class, Warehouse.class));

    private ReportQueryBuilder builder(ReportRowPolicy... policies) {
        return new ReportQueryBuilder(catalog,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), List.of(policies));
    }

    private ReportQuery build(String root, List<ReportColumn> columns) {
        return build(new ReportDefinition(root, columns, null, null, null));
    }

    private ReportQuery build(ReportDefinition definition) {
        return builder().build(definition, USER);
    }

    private static List<ReportColumn> columns(String... fields) {
        return java.util.Arrays.stream(fields).map(ReportColumn::of).toList();
    }

    @Nested
    class Relations {

        @Test
        void rootOnly() {
            assertThat(build("Customer", columns("name")).sql())
                    .isEqualTo("SELECT t0.name\nFROM customer t0\nORDER BY t0.id");
        }

        @Test
        void manyToOne() {
            assertThat(build("PurchaseOrder", columns("total", "customer.name")).sql()).isEqualTo("""
                    SELECT t0.total, t1.name
                    FROM purchase_order t0
                    LEFT JOIN customer t1 ON t1.id = t0.customer_id
                    ORDER BY t0.id, t1.id""");
        }

        @Test
        void selfReferenceGetsItsOwnAlias() {
            assertThat(build("Customer", columns("name", "referredBy.name", "referredBy.referredBy.name")).sql())
                    .isEqualTo("""
                            SELECT t0.name, t1.name, t2.name
                            FROM customer t0
                            LEFT JOIN customer t1 ON t1.id = t0.referred_by_id
                            LEFT JOIN customer t2 ON t2.id = t1.referred_by_id
                            ORDER BY t0.id, t1.id, t2.id""");
        }

        @Test
        void oneToManyInverse() {
            assertThat(build("Customer", columns("name", "orders.total")).sql()).isEqualTo("""
                    SELECT t0.name, t1.total
                    FROM customer t0
                    LEFT JOIN purchase_order t1 ON t1.customer_id = t0.id
                    ORDER BY t0.id, t1.id""");
        }

        @Test
        void oneToOneInverseIsNotToManySoItCombinesWithOtherBranches() {
            ReportQuery query = build("Customer", List.of(ReportColumn.of("account.tier"), ReportColumn.of(SUM, "orders.total")));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t1.tier, SUM(t2.total)
                    FROM customer t0
                    LEFT JOIN customer_account t1 ON t1.customer_id = t0.id
                    LEFT JOIN purchase_order t2 ON t2.customer_id = t0.id
                    GROUP BY t1.tier
                    ORDER BY t1.tier""");
        }

        @Test
        void pureManyToManyThroughCompositeKeyJunction() {
            assertThat(build("Product", columns("name", "tags.tag.name")).sql()).isEqualTo("""
                    SELECT t0.name, t2.name
                    FROM product t0
                    LEFT JOIN product_tag t1 ON t1.product_id = t0.id
                    LEFT JOIN tag t2 ON t2.id = t1.tag_id
                    ORDER BY t0.id, t1.product_id, t1.tag_id, t2.id""");
        }

        @Test
        void manyToManyWithFieldsOnTheJunction() {
            ReportQuery query = build(new ReportDefinition("Product",
                    List.of(ReportColumn.of("name"), ReportColumn.of(SUM, "orderLines.quantity")), null, null,
                    List.of(new ReportSort("orderLines.quantity", SUM, Direction.DESC))));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.name, SUM(t1.quantity)
                    FROM product t0
                    LEFT JOIN order_line t1 ON t1.product_id = t0.id
                    GROUP BY t0.name
                    ORDER BY SUM(t1.quantity) DESC, t0.name""");
        }

        @Test
        void samePathIsJoinedOnce() {
            ReportQuery query = build(new ReportDefinition("PurchaseOrder", columns("customer.name", "customer.region"),
                    where("customer.region", EQ, "EU"), null, List.of(ReportSort.asc("customer.name"))));

            assertThat(query.sql()).containsOnlyOnce("LEFT JOIN");
        }
    }

    @Nested
    class AggregationAndConditions {

        @Test
        void groupByWhereHavingAndAggregateSort() {
            ReportQuery query = build(new ReportDefinition("Customer",
                    List.of(ReportColumn.of("region"), ReportColumn.count(), ReportColumn.of(SUM, "orders.total")),
                    where("orders.orderDate", GTE, "2026-01-01"),
                    where(SUM, "orders.total", GT, 1000),
                    List.of(new ReportSort("orders.total", SUM, Direction.DESC))));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.region, COUNT(*), SUM(t1.total)
                    FROM customer t0
                    LEFT JOIN purchase_order t1 ON t1.customer_id = t0.id
                    WHERE t1.order_date >= ?
                    GROUP BY t0.region
                    HAVING SUM(t1.total) > ?
                    ORDER BY SUM(t1.total) DESC, t0.region""");
            assertThat(query.parameters()).containsExactly(LocalDate.of(2026, 1, 1), 1000);
            assertThat(query.columns()).extracting(ResultColumn::label)
                    .containsExactly("Customer / Region", "Count of Customer", "Sum of Customer / Orders / Total");
        }

        @Test
        void onlyAggregatesGiveASingleTotalsRow() {
            ReportQuery query = build("PurchaseOrder", List.of(ReportColumn.of(SUM, "total"), ReportColumn.of(AVG, "total")));

            assertThat(query.sql()).isEqualTo("SELECT SUM(t0.total), AVG(t0.total)\nFROM purchase_order t0");
        }

        @Test
        void nestedAndOrGroups() {
            ReportQuery query = build(new ReportDefinition("Customer", columns("name"),
                    and(where("region", IN, List.of("EU", "US")),
                            or(where("name", STARTS_WITH, "Ac_"), where("referredBy.name", IS_NULL, null))),
                    null, null));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.name
                    FROM customer t0
                    LEFT JOIN customer t1 ON t1.id = t0.referred_by_id
                    WHERE (t0.region IN (?, ?) AND (t0.name LIKE ? ESCAPE '\\' OR t1.name IS NULL))
                    ORDER BY t0.id, t1.id""");
            assertThat(query.parameters()).containsExactly("EU", "US", "Ac\\_%");
        }

        @Test
        void dateOperators() {
            ReportQuery query = build(new ReportDefinition("PurchaseOrder", columns("total"),
                    and(where("orderDate", BETWEEN, List.of("2026-01-01", "2026-01-31")),
                            where("orderDate", LAST_N_DAYS, 7),
                            where("orderDate", NEXT_N_DAYS, 30)),
                    null, null));

            assertThat(query.sql()).contains("WHERE (t0.order_date BETWEEN ? AND ? AND t0.order_date BETWEEN ? AND ?"
                    + " AND t0.order_date BETWEEN ? AND ?)");
            assertThat(query.parameters()).containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    TODAY.minusDays(7), TODAY, TODAY, TODAY.plusDays(30));
        }

        @Test
        void coercesValuesToFieldType() {
            ReportQuery query = build(new ReportDefinition("PurchaseOrder", columns("total"),
                    where("total", GT, "99.5"), null, null));

            assertThat(query.parameters()).containsExactly(new BigDecimal("99.5"));
        }

        @Test
        void rejectsInvalidCombinations() {
            assertInvalid(new ReportDefinition("Customer", columns("name"), where(COUNT, null, GT, 1), null, null),
                    "Aggregates can only be filtered in 'having'");
            assertInvalid(new ReportDefinition("Customer", columns("name"), null, where(COUNT, null, GT, 1), null),
                    "'having' needs at least one aggregated column");
            assertInvalid(new ReportDefinition("Customer", List.of(ReportColumn.of("region"), ReportColumn.count()),
                    null, where("name", EQ, "x"), null), "must use an aggregate");
            assertInvalid(new ReportDefinition("Customer", List.of(ReportColumn.of("region"), ReportColumn.count()),
                    null, null, List.of(ReportSort.asc("name"))), "only grouped columns or aggregates can be sorted");
            assertInvalid(new ReportDefinition("Customer", List.of(ReportColumn.of(SUM, "name")), null, null, null),
                    "SUM needs a numeric field");
            assertInvalid(new ReportDefinition("Customer", columns("name"), where("name", NEXT_N_DAYS, 3), null, null),
                    "NEXT_N_DAYS cannot be used on TEXT");
            assertInvalid(new ReportDefinition("PurchaseOrder", columns("total"), where("orderDate", EQ, "yesterday"), null, null),
                    "not a valid DATE");
            assertInvalid(new ReportDefinition("PurchaseOrder", columns("total"), where("orderDate", BETWEEN, List.of("2026-01-01")), null, null),
                    "BETWEEN needs exactly two values");
            assertInvalid(new ReportDefinition("Customer", columns("name"), and(), null, null),
                    "must not be empty");
        }
    }

    @Nested
    class DateGrouping {

        private static final String MONTH_OF_ORDER = "CAST(DATE_TRUNC('MONTH', t0.order_date) AS DATE)";

        @Test
        void groupsByMonthAndSortsChronologically() {
            ReportQuery query = build(new ReportDefinition("PurchaseOrder",
                    List.of(ReportColumn.of("orderDate", DateGroup.MONTH), ReportColumn.count()), null, null,
                    List.of(new ReportSort("orderDate", null, Direction.ASC, DateGroup.MONTH))));

            assertThat(query.sql()).isEqualTo("SELECT " + MONTH_OF_ORDER + ", COUNT(*)\n"
                    + "FROM purchase_order t0\n"
                    + "GROUP BY " + MONTH_OF_ORDER + "\n"
                    + "ORDER BY " + MONTH_OF_ORDER + " ASC");
            assertThat(query.columns().getFirst())
                    .extracting(ResultColumn::label, ResultColumn::type, ResultColumn::dateGroup)
                    .containsExactly("Order / Order Date (Month)", com.gucardev.dynamicreportsentity.reporting.catalog.FieldType.DATE,
                            DateGroup.MONTH);
        }

        @Test
        void yearIsANumberUsableInWhere() {
            ReportQuery query = build(new ReportDefinition("PurchaseOrder",
                    List.of(ReportColumn.of("orderDate", DateGroup.YEAR), ReportColumn.of(SUM, "total")),
                    where("orderDate", DateGroup.YEAR, GTE, "2024"), null, null));

            assertThat(query.sql()).isEqualTo("""
                    SELECT EXTRACT(YEAR FROM t0.order_date), SUM(t0.total)
                    FROM purchase_order t0
                    WHERE EXTRACT(YEAR FROM t0.order_date) >= ?
                    GROUP BY EXTRACT(YEAR FROM t0.order_date)
                    ORDER BY EXTRACT(YEAR FROM t0.order_date)""");
            assertThat(query.parameters()).containsExactly(new BigDecimal("2024"));
        }

        @Test
        void seasonalityAndDistinctPeriods() {
            ReportQuery query = build("Customer", List.of(
                    ReportColumn.of("orders.orderDate", DateGroup.MONTH_OF_YEAR),
                    new ReportColumn("orders.orderDate", Aggregate.COUNT_DISTINCT, "Active years", DateGroup.YEAR)));

            assertThat(query.sql()).startsWith(
                    "SELECT EXTRACT(MONTH FROM t1.order_date), COUNT(DISTINCT EXTRACT(YEAR FROM t1.order_date))");
        }

        @Test
        void rejectsMisuse() {
            assertInvalid(new ReportDefinition("PurchaseOrder", List.of(ReportColumn.of("total", DateGroup.YEAR)), null, null, null),
                    "Date grouping YEAR needs a date field");
            assertInvalid(new ReportDefinition("PurchaseOrder",
                            List.of(ReportColumn.of("orderDate", DateGroup.YEAR), ReportColumn.count()), null, null,
                            List.of(ReportSort.asc("orderDate"))),
                    "only grouped columns or aggregates can be sorted");
            assertInvalid(new ReportDefinition("PurchaseOrder",
                            List.of(new ReportColumn(null, COUNT, null, DateGroup.YEAR)), null, null, null),
                    "A field is required");
        }
    }

    @Nested
    class FanOutProtection {

        @Test
        void twoIndependentOneToManyBranchesAreRejected() {
            assertInvalid(new ReportDefinition("Customer", columns("orders.total", "referrals.name"), null, null, null),
                    "'orders' and 'referrals' are separate one-to-many relations");
        }

        @Test
        void sumAboveAOneToManyJoinIsRejected() {
            assertInvalid(new ReportDefinition("PurchaseOrder",
                            List.of(ReportColumn.of("lines.quantity"), ReportColumn.of(SUM, "total")), null, null, null),
                    "SUM of 'total' would be inflated by the one-to-many relation 'lines'");
        }

        @Test
        void maxAboveAOneToManyJoinIsFine() {
            ReportQuery query = build("PurchaseOrder", List.of(ReportColumn.of("lines.quantity"), ReportColumn.of(MAX, "total")));

            assertThat(query.sql()).startsWith("SELECT t1.quantity, MAX(t0.total)");
        }

        @Test
        void chainedOneToManyIsFine() {
            ReportQuery query = build("Customer", List.of(ReportColumn.of("name"), ReportColumn.of(SUM, "orders.lines.quantity")));

            assertThat(query.sql()).contains("LEFT JOIN order_line t2 ON t2.order_id = t1.id");
        }
    }

    @Nested
    class Access {

        @Test
        void restrictedFieldIsDeniedEverywhere() {
            List<ReportDefinition> attempts = List.of(
                    new ReportDefinition("Customer", columns("creditLimit"), null, null, null),
                    new ReportDefinition("PurchaseOrder", columns("total"), where("customer.creditLimit", GT, 1), null, null),
                    new ReportDefinition("Customer", List.of(ReportColumn.of("region"), ReportColumn.count()),
                            null, where(MAX, "creditLimit", GT, 1), null),
                    new ReportDefinition("Customer", columns("name"), null, null, List.of(ReportSort.desc("creditLimit"))));

            for (ReportDefinition attempt : attempts) {
                assertThatThrownBy(() -> builder().build(attempt, USER))
                        .isInstanceOf(ReportAccessDeniedException.class)
                        .hasMessageContaining("Credit Limit");
            }
        }

        @Test
        void restrictedFieldIsAllowedWithAuthority() {
            ReportQuery query = builder().build(new ReportDefinition("Customer", columns("creditLimit"), null, null, null),
                    FINANCE_USER);

            assertThat(query.sql()).startsWith("SELECT t0.credit_limit");
        }

        private final ReportRowPolicy euOnly = viewer -> List.of(new RowRestriction("Customer", "region", "EU"));

        @Test
        void restrictionOnRootFiltersRows() {
            ReportQuery query = builder(euOnly).build(
                    new ReportDefinition("Customer", columns("name"), where("name", CONTAINS, "a"), null, null), USER);

            assertThat(query.sql()).contains("WHERE t0.name LIKE ? ESCAPE '\\'\n  AND t0.region = ?");
            assertThat(query.parameters()).containsExactly("%a%", "EU");
        }

        @Test
        void ownedRootInheritsRestrictionAndJoinedRestrictedEntityIsHiddenInOnClause() {
            ReportQuery query = builder(euOnly).build(
                    new ReportDefinition("PurchaseOrder", columns("total", "customer.name"), null, null, null), USER);

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.total, t1.name
                    FROM purchase_order t0
                    LEFT JOIN customer t1 ON t1.id = t0.customer_id AND t1.region = ?
                    WHERE t0.customer_id IN (SELECT o.id FROM customer o WHERE o.region = ?)
                    ORDER BY t0.id, t1.id""");
            assertThat(query.parameters()).containsExactly("EU", "EU");
        }

        @Test
        void multipleAllowedValuesBecomeIn() {
            ReportRowPolicy euOrUs = viewer -> List.of(new RowRestriction("Customer", "region", List.of("EU", "US")));

            ReportQuery query = builder(euOrUs).build(new ReportDefinition("Customer", columns("name"), null, null, null), USER);

            assertThat(query.sql()).contains("WHERE t0.region IN (?, ?)");
        }

        @Test
        void unrelatedDataIsNotRestricted() {
            ReportQuery query = builder(euOnly).build(new ReportDefinition("Warehouse", columns("city"), null, null, null), USER);

            assertThat(query.sql()).doesNotContain("WHERE");
        }
    }

    /** User input can only ever select catalog entries or become a bind parameter. */
    @Nested
    class SqlInjection {

        private static final String PAYLOAD = "x'; DROP TABLE customer; --";

        @Test
        void maliciousRootFieldOrRelationIsRejected() {
            assertInvalid(new ReportDefinition(PAYLOAD, columns("name"), null, null, null), "Unknown entity");
            assertInvalid(new ReportDefinition("Customer", columns(PAYLOAD), null, null, null), "Unknown field");
            assertInvalid(new ReportDefinition("Customer", columns("name) FROM customer; --"), null, null, null), "Unknown");
            assertInvalid(new ReportDefinition("Customer", columns(PAYLOAD + ".name"), null, null, null), "Unknown relation");
            assertInvalid(new ReportDefinition("Customer", columns("name"), null, null, List.of(ReportSort.asc(PAYLOAD))),
                    "Unknown field");
            assertInvalid(new ReportDefinition("Customer", columns("name"), where(PAYLOAD, EQ, 1), null, null),
                    "Unknown field");
        }

        @Test
        void maliciousValuesOnlyEverBecomeParameters() {
            ReportQuery query = build(new ReportDefinition("Customer", columns("name"),
                    or(where("name", EQ, PAYLOAD), where("name", CONTAINS, PAYLOAD), where("region", IN, List.of(PAYLOAD))),
                    null, null));

            assertThat(query.sql()).doesNotContain("DROP").doesNotContain("'x'");
            assertThat(query.parameters()).contains(PAYLOAD);
        }

        @Test
        void columnLabelsNeverReachTheSql() {
            ReportQuery query = build("Customer", List.of(new ReportColumn("name", null, PAYLOAD)));

            assertThat(query.sql()).isEqualTo("SELECT t0.name\nFROM customer t0\nORDER BY t0.id");
            assertThat(query.columns().getFirst().label()).isEqualTo(PAYLOAD);
        }

        @Reportable(label = "Evil")
        @Table(name = "evil; DROP TABLE x")
        static class EvilTable {
            @Id Long id;
        }

        @Reportable(label = "Evil")
        static class EvilColumn {
            @Id Long id;
            @ReportField(label = "Bad") @Column(name = "a\" OR 1=1 --") String bad;
        }

        @Test
        void unsafeIdentifiersInAnnotationsFailAtStartup() {
            assertThatThrownBy(() -> ReportCatalog.of(List.of(EvilTable.class)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a safe SQL identifier");
            assertThatThrownBy(() -> ReportCatalog.of(List.of(EvilColumn.class)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a safe SQL identifier");
        }
    }

    @Nested
    class CatalogValidation {

        @Test
        void unknownFieldRelationAndDepth() {
            assertInvalid(new ReportDefinition("Customer", columns("email"), null, null, null), "Unknown field: email");
            assertInvalid(new ReportDefinition("Customer", columns("nope.name"), null, null, null),
                    "Unknown relation 'nope' on Customer");
            assertInvalid(new ReportDefinition("Customer", columns("name", "Warehouse.city"), null, null, null),
                    "Unknown relation 'Warehouse'");
            assertInvalid(new ReportDefinition("Customer",
                    columns("referredBy.referredBy.referredBy.referredBy.referredBy.name"), null, null, null), "too deep");
        }

        @Reportable(label = "Broken")
        static class UnknownTarget {
            @Id Long id;
            @ReportReference(target = "DoesNotExist") Long otherId;
        }

        @Reportable(label = "Clash")
        static class NameClash {
            @Id Long id;
            @ReportField(label = "Customer") String customer;
            @ReportReference(target = "Customer") Long customerId;
        }

        @Reportable(label = "Composite Ref")
        static class ReferencesCompositeKey {
            @Id Long id;
            @ReportReference(target = "ProductTag") Long productTagId;
        }

        @Test
        void misconfigurationFailsAtStartup() {
            assertThatThrownBy(() -> ReportCatalog.of(List.of(Customer.class, UnknownTarget.class)))
                    .hasMessageContaining("unknown reportable entity 'DoesNotExist'");
            assertThatThrownBy(() -> ReportCatalog.of(List.of(Customer.class, NameClash.class)))
                    .hasMessageContaining("Name 'customer' is used twice");
            assertThatThrownBy(() -> ReportCatalog.of(List.of(Product.class, Tag.class, ProductTag.class,
                    ReferencesCompositeKey.class)))
                    .hasMessageContaining("composite key");
        }
    }

    private void assertInvalid(ReportDefinition definition, String message) {
        assertThatThrownBy(() -> build(definition))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining(message);
    }
}
