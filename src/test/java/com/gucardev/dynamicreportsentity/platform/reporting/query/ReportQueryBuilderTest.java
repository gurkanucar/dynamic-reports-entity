package com.gucardev.dynamicreportsentity.platform.reporting.query;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportField;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportReference;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportRowPolicy;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.reporting.Reportable;
import com.gucardev.dynamicreportsentity.platform.reporting.RowRestriction;
import com.gucardev.dynamicreportsentity.platform.reporting.catalog.ReportCatalog;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportSort.Direction;
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

import static com.gucardev.dynamicreportsentity.platform.reporting.query.Aggregate.AVG;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.Aggregate.COUNT;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.Aggregate.MAX;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.Aggregate.SUM;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.BETWEEN;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.CONTAINS;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.EQ;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.GT;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.GTE;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.IN;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.IS_NULL;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.LAST_N_DAYS;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.NEXT_N_DAYS;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.FilterOperator.STARTS_WITH;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.ReportCondition.and;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.ReportCondition.or;
import static com.gucardev.dynamicreportsentity.platform.reporting.query.ReportCondition.where;
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
    private static final Actor USER = new Actor("user", Set.of());
    private static final Actor FINANCE_USER = new Actor("finance", Set.of("FINANCE"));

    private final ReportCatalog catalog = ReportCatalog.of(List.of(Customer.class, CustomerAccount.class,
            PurchaseOrder.class, OrderLine.class, Product.class, Tag.class, ProductTag.class, Warehouse.class));

    private ReportQueryBuilder builder(ReportRowPolicy... policies) {
        return builder(SqlDialect.H2, policies);
    }

    private ReportQueryBuilder builder(SqlDialect dialect, ReportRowPolicy... policies) {
        return new ReportQueryBuilder(catalog,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), List.of(policies), dialect);
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
        void countQueryWrapsTheSameRowsWithoutOrderBy() {
            ReportQuery query = build(new ReportDefinition("Customer", columns("name", "referredBy.name"),
                    where("region", EQ, "EU"), null, null));

            assertThat(query.countSql()).isEqualTo("""
                    SELECT COUNT(*) FROM (SELECT 1 AS c0
                    FROM customer t0
                    LEFT JOIN customer t1 ON t1.id = t0.referred_by_id
                    WHERE t0.region = ?
                    ) report""");
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
                    .containsExactly("Order / Order Date (Month)", com.gucardev.dynamicreportsentity.platform.reporting.catalog.FieldType.DATE,
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
    class NestedLists {

        @Test
        void listBecomesACorrelatedSubqueryOneRowPerParent() {
            ReportQuery query = build("Customer", List.of(ReportColumn.of("name"), ReportColumn.of(Aggregate.LIST, "orders.total")));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.name, (SELECT ARRAY_AGG(DISTINCT s1_0.total ORDER BY s1_0.total) FROM purchase_order s1_0 \
                    WHERE s1_0.customer_id = t0.id AND s1_0.total IS NOT NULL)
                    FROM customer t0
                    ORDER BY t0.id""");
            assertThat(query.countSql()).isEqualTo("SELECT COUNT(*) FROM (SELECT 1 AS c0\nFROM customer t0\n) report");
            assertThat(query.columns().get(1).label()).isEqualTo("Customer / Orders / Total (list)");
        }

        @Test
        void toOneHopsAreJoinedAndHopsAfterTheOneToManyStayInsideTheSubquery() {
            ReportQuery query = build("PurchaseOrder", List.of(ReportColumn.of("total"),
                    ReportColumn.of(Aggregate.LIST, "customer.referrals.name"),
                    ReportColumn.of(Aggregate.LIST, "lines.product.tags.tag.name")));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.total, \
                    (SELECT ARRAY_AGG(DISTINCT s1_0.name ORDER BY s1_0.name) FROM customer s1_0 \
                    WHERE s1_0.referred_by_id = t1.id AND s1_0.name IS NOT NULL), \
                    (SELECT ARRAY_AGG(DISTINCT s2_3.name ORDER BY s2_3.name) FROM order_line s2_0 \
                    LEFT JOIN product s2_1 ON s2_1.id = s2_0.product_id \
                    LEFT JOIN product_tag s2_2 ON s2_2.product_id = s2_1.id \
                    LEFT JOIN tag s2_3 ON s2_3.id = s2_2.tag_id \
                    WHERE s2_0.order_id = t0.id AND s2_3.name IS NOT NULL)
                    FROM purchase_order t0
                    LEFT JOIN customer t1 ON t1.id = t0.customer_id
                    ORDER BY t0.id, t1.id""");
        }

        @Test
        void rowRestrictionsApplyInsideTheSubquery() {
            ReportRowPolicy euOnly = viewer -> List.of(new RowRestriction("Customer", "region", "EU"));

            ReportQuery query = builder(euOnly).build(new ReportDefinition("Customer",
                    List.of(ReportColumn.of("name"), ReportColumn.of(Aggregate.LIST, "referrals.name")), null, null, null), USER);

            assertThat(query.sql()).contains(
                    "FROM customer s1_0 WHERE s1_0.referred_by_id = t0.id AND s1_0.name IS NOT NULL AND s1_0.region = ?)",
                    "WHERE t0.region = ?");
            assertThat(query.parameters()).containsExactly("EU", "EU");
            assertThat(query.countParameters()).containsExactly("EU");
        }

        @Test
        void oneToManyFilterFallsBackToGroupedArrayAgg() {
            ReportQuery query = build(new ReportDefinition("Customer",
                    List.of(ReportColumn.of("name"), ReportColumn.of(Aggregate.LIST, "orders.total")),
                    where("orders.total", GT, 5), null, null));

            assertThat(query.sql()).contains("ARRAY_AGG(DISTINCT t1.total ORDER BY t1.total) FILTER (WHERE t1.total IS NOT NULL)",
                    "GROUP BY t0.name");
        }

        @Test
        void listOfAToOneFieldIsRejected() {
            assertInvalid(new ReportDefinition("PurchaseOrder",
                            List.of(ReportColumn.of("total"), ReportColumn.of(Aggregate.LIST, "customer.name")), null, null, null),
                    "needs a path through a one-to-many relation");
        }

        @Test
        void listsMayCombineIndependentOneToManyBranches() {
            ReportQuery query = build("Customer", List.of(ReportColumn.of("name"),
                    ReportColumn.of(Aggregate.LIST, "orders.orderDate"),
                    ReportColumn.of(Aggregate.LIST, "referrals.name"),
                    ReportColumn.of(Aggregate.COUNT_DISTINCT, "orders.total")));

            assertThat(query.sql()).contains("LEFT JOIN purchase_order t1", "LEFT JOIN customer t2 ON t2.referred_by_id = t0.id");
        }

        @Test
        void butNotTogetherWithDuplicateSensitiveAggregates() {
            assertInvalid(new ReportDefinition("Customer", List.of(ReportColumn.of("name"),
                            ReportColumn.of(Aggregate.LIST, "referrals.name"), ReportColumn.of(SUM, "orders.total")), null, null, null),
                    "separate one-to-many relations");
            assertInvalid(new ReportDefinition("Customer", List.of(ReportColumn.of("name"),
                            ReportColumn.of(Aggregate.LIST, "referrals.name"), ReportColumn.of(Aggregate.LIST, "orders.total"),
                            ReportColumn.count()), null, null, null),
                    "separate one-to-many relations");
        }

        @Test
        void listCannotBeFilteredOrSorted() {
            List<ReportColumn> cols = List.of(ReportColumn.of("name"), ReportColumn.of(Aggregate.LIST, "orders.total"));
            assertInvalid(new ReportDefinition("Customer", cols, null, where(Aggregate.LIST, "orders.total", EQ, 1), null),
                    "A LIST cannot be filtered");
            assertInvalid(new ReportDefinition("Customer", cols, null, null,
                    List.of(new ReportSort("orders.total", Aggregate.LIST, Direction.ASC))), "A LIST column cannot be sorted");
        }
    }

    @Nested
    class FieldComparison {

        @Test
        void comparesTwoFieldsAcrossJoins() {
            ReportQuery query = build(new ReportDefinition("Customer", columns("name"),
                    ReportCondition.compare("region", FilterOperator.NOT_EQ, "referredBy.region"), null, null));

            assertThat(query.sql()).contains("LEFT JOIN customer t1 ON t1.id = t0.referred_by_id", "WHERE t0.region <> t1.region");
            assertThat(query.parameters()).isEmpty();
        }

        @Test
        void rejectsMismatchedTypesAndNonComparisonOperators() {
            assertInvalid(new ReportDefinition("Customer", columns("name"),
                    ReportCondition.compare("name", EQ, "orders.total"), null, null), "Cannot compare TEXT 'name' with NUMBER");
            assertInvalid(new ReportDefinition("Customer", columns("name"),
                    ReportCondition.compare("name", CONTAINS, "region"), null, null), "supports EQ, NOT_EQ");
        }
    }

    @Nested
    class FieldVersusAggregate {

        @Test
        void comparesWithTheAggregateOfARelatedGroup() {
            ReportQuery query = build(new ReportDefinition("PurchaseOrder", columns("total"),
                    ReportCondition.compare("total", GT, AVG, "customer.orders.total", null), null, null));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.total
                    FROM purchase_order t0
                    LEFT JOIN customer t1 ON t1.id = t0.customer_id
                    LEFT JOIN (SELECT s1_0.customer_id AS k, AVG(s1_0.total) AS v FROM purchase_order s1_0 \
                    GROUP BY s1_0.customer_id ORDER BY k LIMIT 2147483647) g2 ON g2.k = t1.id
                    WHERE t0.total > g2.v
                    ORDER BY t0.id, t1.id""");
        }

        @Test
        void comparesWithTheAggregateOfTheWholeTableFiltered() {
            ReportQuery query = build(new ReportDefinition("PurchaseOrder", columns("total"),
                    ReportCondition.compare("total", GTE, MAX, "total", where("orderDate", LAST_N_DAYS, 30)), null, null));

            assertThat(query.sql()).contains(
                    "WHERE t0.total >= (SELECT MAX(s1_0.total) FROM purchase_order s1_0 WHERE s1_0.order_date BETWEEN ? AND ?)");
            assertThat(query.parameters()).containsExactly(TODAY.minusDays(30), TODAY);
        }

        @Test
        void rowRestrictionsApplyToTheAggregate() {
            ReportRowPolicy euOnly = viewer -> List.of(new RowRestriction("Customer", "region", "EU"));

            ReportQuery query = builder(euOnly).build(new ReportDefinition("Customer", columns("name"),
                    ReportCondition.compare("name", EQ, Aggregate.MIN, "name", null), null, null), USER);

            assertThat(query.sql()).contains("WHERE t0.name = (SELECT MIN(s1_0.name) FROM customer s1_0 WHERE s1_0.region = ?)",
                    "AND t0.region = ?");
            assertThat(query.parameters()).containsExactly("EU", "EU");
        }

        @Test
        void rejectsInvalidComparisons() {
            assertInvalid(new ReportDefinition("PurchaseOrder", columns("total"),
                    ReportCondition.compare("total", GT, Aggregate.LIST, "customer.orders.total", null), null, null),
                    "A LIST cannot be compared");
            assertInvalid(new ReportDefinition("PurchaseOrder", columns("total"),
                    ReportCondition.compare("total", GT, AVG, "customer.creditLimit", null), null, null),
                    "must cross a one-to-many relation");
            assertInvalid(new ReportDefinition("PurchaseOrder", columns("total"),
                    ReportCondition.compare("orderDate", GT, AVG, "total", null), null, null),
                    "Cannot compare DATE");
        }
    }

    @Nested
    class Exists {

        @Test
        void notExistsWithCondition() {
            ReportQuery query = build(new ReportDefinition("Customer", columns("name"),
                    ReportCondition.notExists("orders", where("total", GT, 100)), null, null));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.name
                    FROM customer t0
                    WHERE NOT EXISTS (SELECT 1 FROM purchase_order s1_0 WHERE s1_0.customer_id = t0.id AND s1_0.total > ?)
                    ORDER BY t0.id""");
            assertThat(query.parameters()).containsExactly(100);
        }

        @Test
        void existsThroughJunctionWithJoinInside() {
            ReportQuery query = build(new ReportDefinition("Product", columns("name"),
                    ReportCondition.exists("tags", where("tag.name", EQ, "eco")), null, null));

            assertThat(query.sql()).contains("WHERE EXISTS (SELECT 1 FROM product_tag s1_0 "
                    + "LEFT JOIN tag s1_1 ON s1_1.id = s1_0.tag_id WHERE s1_0.product_id = t0.id AND s1_1.name = ?)");
        }

        @Test
        void rowRestrictionsApplyInsideExists() {
            ReportRowPolicy euOnly = viewer -> List.of(new RowRestriction("Customer", "region", "EU"));

            ReportQuery query = builder(euOnly).build(new ReportDefinition("Customer", columns("name"),
                    ReportCondition.exists("referrals", null), null, null), USER);

            assertThat(query.sql()).contains("EXISTS (SELECT 1 FROM customer s1_0 WHERE s1_0.referred_by_id = t0.id AND s1_0.region = ?)");
        }

        @Test
        void existsDoesNotFanOutSoListsStayPerRoot() {
            ReportQuery query = build(new ReportDefinition("Customer",
                    List.of(ReportColumn.of("name"), ReportColumn.of(Aggregate.LIST, "orders.total")),
                    ReportCondition.exists("orders", where("total", GT, 100)), null, null));

            assertThat(query.sql()).doesNotContain("GROUP BY");
        }

        @Test
        void needsARelation() {
            assertInvalid(new ReportDefinition("Customer", columns("name"),
                    where("name", FilterOperator.EXISTS, null), null, null), "need a \"relation\"");
            assertInvalid(new ReportDefinition("Customer", columns("name"),
                    ReportCondition.exists("referredBy", null), null, null), "needs a path through a one-to-many relation");
        }
    }

    @Nested
    class ListOptions {

        @Test
        void listOfObjects() {
            ReportQuery query = build("Customer", List.of(ReportColumn.of("name"), ReportColumn.objects("orders", "orderDate", "total")));

            assertThat(query.sql()).contains("(SELECT CAST(JSON_ARRAYAGG(JSON_OBJECT('orderDate' VALUE s1_0.order_date, "
                    + "'total' VALUE s1_0.total) ORDER BY s1_0.order_date, s1_0.total) AS VARCHAR) FROM purchase_order s1_0 "
                    + "WHERE s1_0.customer_id = t0.id AND (s1_0.order_date IS NOT NULL OR s1_0.total IS NOT NULL))");
            assertThat(query.columns().get(1).keys()).containsExactly("orderDate", "total");
        }

        @Test
        void objectFieldsMustNotFanOutAgainAndKeysMustBeCatalogPaths() {
            assertInvalid(new ReportDefinition("Customer",
                    List.of(ReportColumn.of("name"), ReportColumn.objects("orders", "lines.quantity")), null, null, null),
                    "reachable through to-one relations");
            assertInvalid(new ReportDefinition("Customer",
                    List.of(ReportColumn.of("name"), ReportColumn.objects("orders", "total') --")), null, null, null),
                    "Unknown field");
        }

        @Test
        void filteredSortedAndLimitedList() {
            ReportQuery query = build("Customer", List.of(ReportColumn.of("name"),
                    ReportColumn.of(Aggregate.LIST, "orders.total").where(where("total", GT, 5)).order(Direction.DESC).limit(3)));

            assertThat(query.sql()).contains("ARRAY_AGG(DISTINCT s1_0.total ORDER BY s1_0.total DESC) FROM purchase_order s1_0 "
                    + "WHERE s1_0.customer_id = t0.id AND s1_0.total > ? AND s1_0.total IS NOT NULL)");
            assertThat(query.parameters()).containsExactly(5);
            assertThat(query.columns().get(1).listLimit()).isEqualTo(3);
        }

        @Test
        void columnOptionsNeedPerRootMode() {
            assertInvalid(new ReportDefinition("Customer", List.of(ReportColumn.of("region"), ReportColumn.count(),
                            ReportColumn.of(Aggregate.LIST, "orders.total").order(Direction.DESC)), null, null, null),
                    "need per-root mode");
        }
    }

    @Nested
    class PerRoot {

        private final List<ReportColumn> perCustomer = List.of(ReportColumn.of("name"),
                ReportColumn.of(Aggregate.COUNT, "orders.total"),
                ReportColumn.of(SUM, "orders.total"),
                ReportColumn.of(Aggregate.LIST, "referrals.name"));

        @Test
        void childAggregatesBecomeSubqueriesWithoutGroupBy() {
            ReportQuery query = build(new ReportDefinition("Customer", perCustomer, null,
                    where(SUM, "orders.total", GT, 1000), List.of(new ReportSort("orders.total", SUM, Direction.DESC)), true));

            // H2: correlated subqueries everywhere (it cannot index a joined derived table)
            assertThat(query.sql())
                    .contains("(SELECT COUNT(s1_0.total) FROM purchase_order s1_0 WHERE s1_0.customer_id = t0.id)",
                            "(SELECT SUM(s2_0.total) FROM purchase_order s2_0 WHERE s2_0.customer_id = t0.id)",
                            "WHERE (SELECT SUM(s4_0.total) FROM purchase_order s4_0 WHERE s4_0.customer_id = t0.id) > ?",
                            "ORDER BY (SELECT SUM(s5_0.total) FROM purchase_order s5_0 WHERE s5_0.customer_id = t0.id) DESC, t0.id")
                    .doesNotContain("GROUP BY", "HAVING");
            assertThat(query.parameters()).containsExactly(1000);
        }

        @Test
        void postgresPreAggregatesHavingAndSortOnceAndSharesThem() {
            ReportQuery query = builder(SqlDialect.POSTGRESQL).build(new ReportDefinition("Customer", perCustomer, null,
                    where(SUM, "orders.total", GT, 1000), List.of(new ReportSort("orders.total", SUM, Direction.DESC)), true),
                    USER);

            // columns stay correlated (evaluated only for the page); filter and sort need every customer's
            // value: one GROUP BY pass, joined once, used by both
            assertThat(query.sql())
                    .contains("(SELECT SUM(s2_0.total) FROM purchase_order s2_0 WHERE s2_0.customer_id = t0.id)",
                            "\nLEFT JOIN (SELECT s4_0.customer_id AS k, SUM(s4_0.total) AS v FROM purchase_order s4_0"
                                    + " GROUP BY s4_0.customer_id) g5 ON g5.k = t0.id",
                            "\nWHERE g5.v > ?",
                            "\nORDER BY g5.v DESC, t0.id")
                    .doesNotContain("\nGROUP BY", "HAVING", "LIMIT");
            assertThat(query.sql().split("LEFT JOIN \\(SELECT", -1)).hasSize(2);
            assertThat(query.parameters()).containsExactly(1000);
        }

        @Test
        void postgresPerRootCountFilterTreatsNoChildrenAsZero() {
            ReportQuery query = builder(SqlDialect.POSTGRESQL).build(new ReportDefinition("Customer", perCustomer, null,
                    where(COUNT, "orders.total", FilterOperator.LT, 1), List.of(), true), USER);

            assertThat(query.sql()).contains("WHERE COALESCE(g5.v, 0) < ?");
        }

        @Test
        void sumAcrossIndependentBranchesIsSafePerRoot() {
            ReportQuery query = build(new ReportDefinition("Customer", perCustomer, null, null, null, true));

            assertThat(query.sql()).doesNotContain("LEFT JOIN");
        }

        @Test
        void rejectsWhatOnlyMakesSenseWhenGrouping() {
            assertInvalid(new ReportDefinition("Customer", List.of(ReportColumn.of("name"), ReportColumn.count()),
                    null, null, null, true), "perRoot needs every aggregate to go through a one-to-many relation");
            assertInvalid(new ReportDefinition("Customer", List.of(ReportColumn.of("orders.total"),
                    ReportColumn.of(Aggregate.LIST, "referrals.name")), null, null, null, true), "with EXISTS / NOT_EXISTS instead");
        }
    }

    @Nested
    class ColumnUsages {

        @Test
        void recordsHowEachColumnIsUsed() {
            ReportQuery query = build(new ReportDefinition("PurchaseOrder", columns("total", "customer.name"),
                    and(where("customer.name", CONTAINS, "a"), where("orderDate", DateGroup.YEAR, EQ, 2026),
                            where("total", GT, 5)),
                    null, List.of(ReportSort.desc("customer.region"))));

            assertThat(query.columnUsages()).containsExactlyInAnyOrder(
                    new com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage("customer", "name", com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage.Kind.FILTER_CONTAINS),
                    new com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage("purchase_order", "order_date", com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage.Kind.FILTER_EXPRESSION),
                    new com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage("purchase_order", "total", com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage.Kind.FILTER),
                    new com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage("customer", "region", com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage.Kind.SORT),
                    new com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage("customer", "id", com.gucardev.dynamicreportsentity.platform.reporting.catalog.ColumnUsage.Kind.JOIN));
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
