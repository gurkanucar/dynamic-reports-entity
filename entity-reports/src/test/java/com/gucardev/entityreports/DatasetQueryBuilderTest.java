package com.gucardev.entityreports;

import com.gucardev.entityreports.Dataset.AccessDeniedException;
import com.gucardev.entityreports.Dataset.Aggregate;
import com.gucardev.entityreports.Dataset.Column;
import com.gucardev.entityreports.Dataset.Condition;
import com.gucardev.entityreports.Dataset.DateGroup;
import com.gucardev.entityreports.Dataset.Definition;
import com.gucardev.entityreports.Dataset.FilterOperator;
import com.gucardev.entityreports.Dataset.InvalidException;
import com.gucardev.entityreports.Dataset.Query;
import com.gucardev.entityreports.Dataset.ResultColumn;
import com.gucardev.entityreports.Dataset.RowPolicy;
import com.gucardev.entityreports.Dataset.RowRestriction;
import com.gucardev.entityreports.Dataset.Sort;
import com.gucardev.entityreports.Dataset.Viewer;
import com.gucardev.entityreports.Dataset.Sort.Direction;
import com.gucardev.entityreports.DatasetQueryBuilder.SqlDialect;

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

import static com.gucardev.entityreports.Dataset.Aggregate.AVG;
import static com.gucardev.entityreports.Dataset.Aggregate.COUNT;
import static com.gucardev.entityreports.Dataset.Aggregate.MAX;
import static com.gucardev.entityreports.Dataset.Aggregate.SUM;
import static com.gucardev.entityreports.Dataset.Condition.and;
import static com.gucardev.entityreports.Dataset.Condition.or;
import static com.gucardev.entityreports.Dataset.Condition.where;
import static com.gucardev.entityreports.Dataset.FilterOperator.BETWEEN;
import static com.gucardev.entityreports.Dataset.FilterOperator.CONTAINS;
import static com.gucardev.entityreports.Dataset.FilterOperator.EQ;
import static com.gucardev.entityreports.Dataset.FilterOperator.GT;
import static com.gucardev.entityreports.Dataset.FilterOperator.GTE;
import static com.gucardev.entityreports.Dataset.FilterOperator.IN;
import static com.gucardev.entityreports.Dataset.FilterOperator.IS_NULL;
import static com.gucardev.entityreports.Dataset.FilterOperator.LAST_N_DAYS;
import static com.gucardev.entityreports.Dataset.FilterOperator.NEXT_N_DAYS;
import static com.gucardev.entityreports.Dataset.FilterOperator.STARTS_WITH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetQueryBuilderTest {

    // ---- fixture: every relation kind --------------------------------------------------------
    //  Customer.referredBy        self-reference (inverse: referrals)
    //  CustomerAccount            one-to-one with Customer (inverse: account)
    //  PurchaseOrder.customer     many-to-one (inverse: orders = one-to-many)
    //  OrderLine                  many-to-many WITH fields between PurchaseOrder and Product
    //  ProductTag                 pure many-to-many junction, composite key
    //  Warehouse                  unrelated

    @DatasetEntity(label = "Customer")
    @Table(name = "customer")
    static class Customer {
        @Id Long id;
        @DatasetField(label = "Name") String name;
        @DatasetField(label = "Region") String region;
        @DatasetField(label = "Credit Limit", authority = "FINANCE") BigDecimal creditLimit;
        @DatasetReference(target = "Customer", label = "Referred By", inverse = "referrals", inverseLabel = "Referrals")
        Long referredById;
        String email; // not annotated -> never dataset-ready
    }

    @DatasetEntity(label = "Account")
    static class CustomerAccount {
        @Id Long id;
        @DatasetReference(target = "Customer", oneToOne = true, owner = true, inverse = "account") Long customerId;
        @DatasetField(label = "Tier") String tier;
    }

    @DatasetEntity(label = "Order")
    @Table(name = "purchase_order")
    static class PurchaseOrder {
        @Id Long id;
        @DatasetReference(target = "Customer", owner = true, inverse = "orders", inverseLabel = "Orders") Long customerId;
        @DatasetField(label = "Order Date") LocalDate orderDate;
        @DatasetField(label = "Total") BigDecimal total;
    }

    @DatasetEntity(label = "Order Line")
    static class OrderLine {
        @Id Long id;
        @DatasetReference(target = "PurchaseOrder", inverse = "lines") Long orderId;
        @DatasetReference(target = "Product", inverse = "orderLines") Long productId;
        @DatasetField(label = "Quantity") Integer quantity;
    }

    @DatasetEntity(label = "Product")
    static class Product {
        @Id Long id;
        @DatasetField(label = "Name") String name;
    }

    @DatasetEntity(label = "Tag")
    static class Tag {
        @Id Long id;
        @DatasetField(label = "Name") String name;
    }

    @DatasetEntity(label = "Product Tag")
    static class ProductTag {
        @Id @DatasetReference(target = "Product", inverse = "tags") Long productId;
        @Id @DatasetReference(target = "Tag", inverse = "products") Long tagId;
    }

    @DatasetEntity(label = "Warehouse")
    static class Warehouse {
        @Id Long id;
        @DatasetField(label = "City") String city;
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    private static final Viewer USER = Viewer.of("user", Set.of());
    private static final Viewer FINANCE_USER = Viewer.of("finance", Set.of("FINANCE"));

    private final DatasetCatalog catalog = DatasetCatalog.of(List.of(Customer.class, CustomerAccount.class,
            PurchaseOrder.class, OrderLine.class, Product.class, Tag.class, ProductTag.class, Warehouse.class));

    private DatasetQueryBuilder builder(RowPolicy... policies) {
        return builder(SqlDialect.H2, policies);
    }

    private DatasetQueryBuilder builder(SqlDialect dialect, RowPolicy... policies) {
        return new DatasetQueryBuilder(catalog,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), List.of(policies), dialect);
    }

    private Query build(String root, List<Column> columns) {
        return build(new Definition(root, columns, null, null, null));
    }

    private Query build(Definition definition) {
        return builder().build(definition, USER);
    }

    private static List<Column> columns(String... fields) {
        return java.util.Arrays.stream(fields).map(Column::of).toList();
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
            Query query = build("Customer", List.of(Column.of("account.tier"), Column.of(SUM, "orders.total")));

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
            Query query = build(new Definition("Product",
                    List.of(Column.of("name"), Column.of(SUM, "orderLines.quantity")), null, null,
                    List.of(new Sort("orderLines.quantity", SUM, Direction.DESC))));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.name, SUM(t1.quantity)
                    FROM product t0
                    LEFT JOIN order_line t1 ON t1.product_id = t0.id
                    GROUP BY t0.name
                    ORDER BY SUM(t1.quantity) DESC, t0.name""");
        }

        /** COUNT(*) next to a one-to-many join counts the children, not the customers it is labelled with. */
        @Test
        void countOfRowsNextToAOneToManyJoinIsRejected() {
            assertThatThrownBy(() -> build(new Definition("Customer",
                    List.of(Column.of("region"), Column.count(), Column.of(SUM, "orders.total")),
                    null, null, List.of())))
                    .isInstanceOf(InvalidException.class).hasMessageContaining("COUNT_DISTINCT");
        }

        @Test
        void samePathIsJoinedOnce() {
            Query query = build(new Definition("PurchaseOrder", columns("customer.name", "customer.region"),
                    where("customer.region", EQ, "EU"), null, List.of(Sort.asc("customer.name"))));

            assertThat(query.sql()).containsOnlyOnce("LEFT JOIN");
        }
    }

    @Nested
    class AggregationAndConditions {

        @Test
        void groupByWhereHavingAndAggregateSort() {
            Query query = build(new Definition("Customer",
                    List.of(Column.of("region"), Column.of(Aggregate.COUNT_DISTINCT, "id"),
                            Column.of(SUM, "orders.total")),
                    where("orders.orderDate", GTE, "2026-01-01"),
                    where(SUM, "orders.total", GT, 1000),
                    List.of(new Sort("orders.total", SUM, Direction.DESC))));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.region, COUNT(DISTINCT t0.id), SUM(t1.total)
                    FROM customer t0
                    LEFT JOIN purchase_order t1 ON t1.customer_id = t0.id
                    WHERE t1.order_date >= ?
                    GROUP BY t0.region
                    HAVING SUM(t1.total) > ?
                    ORDER BY SUM(t1.total) DESC, t0.region""");
            assertThat(query.parameters()).containsExactly(LocalDate.of(2026, 1, 1), 1000);
            assertThat(query.columns()).extracting(ResultColumn::label)
                    .containsExactly("Customer / Region", "Distinct count of Customer / ID", "Sum of Customer / Orders / Total");
        }

        @Test
        void countQueryWrapsTheSameRowsWithoutOrderBy() {
            Query query = build(new Definition("Customer", columns("name", "referredBy.name"),
                    where("region", EQ, "EU"), null, null));

            assertThat(query.countSql()).isEqualTo("""
                    SELECT COUNT(*) FROM (SELECT 1 AS c0
                    FROM customer t0
                    LEFT JOIN customer t1 ON t1.id = t0.referred_by_id
                    WHERE t0.region = ?
                    ) dataset""");
        }

        @Test
        void onlyAggregatesGiveASingleTotalsRow() {
            Query query = build("PurchaseOrder", List.of(Column.of(SUM, "total"), Column.of(AVG, "total")));

            assertThat(query.sql()).isEqualTo("SELECT SUM(t0.total), AVG(t0.total)\nFROM purchase_order t0");
        }

        @Test
        void nestedAndOrGroups() {
            Query query = build(new Definition("Customer", columns("name"),
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
            Query query = build(new Definition("PurchaseOrder", columns("total"),
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
            Query query = build(new Definition("PurchaseOrder", columns("total"),
                    where("total", GT, "99.5"), null, null));

            assertThat(query.parameters()).containsExactly(new BigDecimal("99.5"));
        }

        @Test
        void rejectsInvalidCombinations() {
            assertInvalid(new Definition("Customer", columns("name"), where(COUNT, null, GT, 1), null, null),
                    "Aggregates can only be filtered in 'having'");
            assertInvalid(new Definition("Customer", columns("name"), null, where(COUNT, null, GT, 1), null),
                    "'having' needs at least one aggregated column");
            assertInvalid(new Definition("Customer", List.of(Column.of("region"), Column.count()),
                    null, where("name", EQ, "x"), null), "must use an aggregate");
            assertInvalid(new Definition("Customer", List.of(Column.of("region"), Column.count()),
                    null, null, List.of(Sort.asc("name"))), "only grouped columns or aggregates can be sorted");
            assertInvalid(new Definition("Customer", List.of(Column.of(SUM, "name")), null, null, null),
                    "SUM needs a numeric field");
            assertInvalid(new Definition("Customer", columns("name"), where("name", NEXT_N_DAYS, 3), null, null),
                    "NEXT_N_DAYS cannot be used on TEXT");
            assertInvalid(new Definition("PurchaseOrder", columns("total"), where("orderDate", EQ, "yesterday"), null, null),
                    "not a valid DATE");
            assertInvalid(new Definition("PurchaseOrder", columns("total"), where("orderDate", BETWEEN, List.of("2026-01-01")), null, null),
                    "BETWEEN needs exactly two values");
            assertInvalid(new Definition("Customer", columns("name"), and(), null, null),
                    "must not be empty");
        }
    }

    @Nested
    class DateGrouping {

        private static final String MONTH_OF_ORDER = "CAST(DATE_TRUNC('MONTH', t0.order_date) AS DATE)";

        @Test
        void groupsByMonthAndSortsChronologically() {
            Query query = build(new Definition("PurchaseOrder",
                    List.of(Column.of("orderDate", DateGroup.MONTH), Column.count()), null, null,
                    List.of(new Sort("orderDate", null, Direction.ASC, DateGroup.MONTH))));

            assertThat(query.sql()).isEqualTo("SELECT " + MONTH_OF_ORDER + ", COUNT(*)\n"
                    + "FROM purchase_order t0\n"
                    + "GROUP BY " + MONTH_OF_ORDER + "\n"
                    + "ORDER BY " + MONTH_OF_ORDER + " ASC");
            assertThat(query.columns().getFirst())
                    .extracting(ResultColumn::label, ResultColumn::type, ResultColumn::dateGroup)
                    .containsExactly("Order / Order Date (Month)", Dataset.FieldType.DATE,
                            DateGroup.MONTH);
        }

        @Test
        void yearIsANumberUsableInWhere() {
            Query query = build(new Definition("PurchaseOrder",
                    List.of(Column.of("orderDate", DateGroup.YEAR), Column.of(SUM, "total")),
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
            Query query = build("Customer", List.of(
                    Column.of("orders.orderDate", DateGroup.MONTH_OF_YEAR),
                    new Column("orders.orderDate", Aggregate.COUNT_DISTINCT, "Active years", DateGroup.YEAR)));

            assertThat(query.sql()).startsWith(
                    "SELECT EXTRACT(MONTH FROM t1.order_date), COUNT(DISTINCT EXTRACT(YEAR FROM t1.order_date))");
        }

        @Test
        void rejectsMisuse() {
            assertInvalid(new Definition("PurchaseOrder", List.of(Column.of("total", DateGroup.YEAR)), null, null, null),
                    "Date grouping YEAR needs a date field");
            assertInvalid(new Definition("PurchaseOrder",
                            List.of(Column.of("orderDate", DateGroup.YEAR), Column.count()), null, null,
                            List.of(Sort.asc("orderDate"))),
                    "only grouped columns or aggregates can be sorted");
            assertInvalid(new Definition("PurchaseOrder",
                            List.of(new Column(null, COUNT, null, DateGroup.YEAR)), null, null, null),
                    "A field is required");
        }
    }

    @Nested
    class NestedLists {

        @Test
        void listBecomesACorrelatedSubqueryOneRowPerParent() {
            Query query = build("Customer", List.of(Column.of("name"), Column.of(Aggregate.LIST, "orders.total")));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.name, (SELECT ARRAY_AGG(DISTINCT s1_0.total ORDER BY s1_0.total) FROM purchase_order s1_0 \
                    WHERE s1_0.customer_id = t0.id AND s1_0.total IS NOT NULL)
                    FROM customer t0
                    ORDER BY t0.id""");
            assertThat(query.countSql()).isEqualTo("SELECT COUNT(*) FROM (SELECT 1 AS c0\nFROM customer t0\n) dataset");
            assertThat(query.columns().get(1).label()).isEqualTo("Customer / Orders / Total (list)");
        }

        @Test
        void toOneHopsAreJoinedAndHopsAfterTheOneToManyStayInsideTheSubquery() {
            Query query = build("PurchaseOrder", List.of(Column.of("total"),
                    Column.of(Aggregate.LIST, "customer.referrals.name"),
                    Column.of(Aggregate.LIST, "lines.product.tags.tag.name")));

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
            RowPolicy euOnly = viewer -> List.of(new RowRestriction("Customer", "region", "EU"));

            Query query = builder(euOnly).build(new Definition("Customer",
                    List.of(Column.of("name"), Column.of(Aggregate.LIST, "referrals.name")), null, null, null), USER);

            assertThat(query.sql()).contains(
                    "FROM customer s1_0 WHERE s1_0.referred_by_id = t0.id AND s1_0.name IS NOT NULL AND s1_0.region = ?)",
                    "WHERE t0.region = ?");
            assertThat(query.parameters()).containsExactly("EU", "EU");
            assertThat(query.countParameters()).containsExactly("EU");
        }

        @Test
        void oneToManyFilterFallsBackToGroupedArrayAgg() {
            Query query = build(new Definition("Customer",
                    List.of(Column.of("name"), Column.of(Aggregate.LIST, "orders.total")),
                    where("orders.total", GT, 5), null, null));

            assertThat(query.sql()).contains("ARRAY_AGG(DISTINCT t1.total ORDER BY t1.total) FILTER (WHERE t1.total IS NOT NULL)",
                    "GROUP BY t0.name");
        }

        @Test
        void listOfAToOneFieldIsRejected() {
            assertInvalid(new Definition("PurchaseOrder",
                            List.of(Column.of("total"), Column.of(Aggregate.LIST, "customer.name")), null, null, null),
                    "needs a path through a one-to-many relation");
        }

        @Test
        void listsMayCombineIndependentOneToManyBranches() {
            Query query = build("Customer", List.of(Column.of("name"),
                    Column.of(Aggregate.LIST, "orders.orderDate"),
                    Column.of(Aggregate.LIST, "referrals.name"),
                    Column.of(Aggregate.COUNT_DISTINCT, "orders.total")));

            assertThat(query.sql()).contains("LEFT JOIN purchase_order t1", "LEFT JOIN customer t2 ON t2.referred_by_id = t0.id");
        }

        @Test
        void butNotTogetherWithDuplicateSensitiveAggregates() {
            assertInvalid(new Definition("Customer", List.of(Column.of("name"),
                            Column.of(Aggregate.LIST, "referrals.name"), Column.of(SUM, "orders.total")), null, null, null),
                    "separate one-to-many relations");
            assertInvalid(new Definition("Customer", List.of(Column.of("name"),
                            Column.of(Aggregate.LIST, "referrals.name"), Column.of(Aggregate.LIST, "orders.total"),
                            Column.count()), null, null, null),
                    "separate one-to-many relations");
        }

        @Test
        void listCannotBeFilteredOrSorted() {
            List<Column> cols = List.of(Column.of("name"), Column.of(Aggregate.LIST, "orders.total"));
            assertInvalid(new Definition("Customer", cols, null, where(Aggregate.LIST, "orders.total", EQ, 1), null),
                    "A LIST cannot be filtered");
            assertInvalid(new Definition("Customer", cols, null, null,
                    List.of(new Sort("orders.total", Aggregate.LIST, Direction.ASC))), "A LIST column cannot be sorted");
        }
    }

    @Nested
    class FieldComparison {

        @Test
        void comparesTwoFieldsAcrossJoins() {
            Query query = build(new Definition("Customer", columns("name"),
                    Condition.compare("region", FilterOperator.NOT_EQ, "referredBy.region"), null, null));

            assertThat(query.sql()).contains("LEFT JOIN customer t1 ON t1.id = t0.referred_by_id", "WHERE t0.region <> t1.region");
            assertThat(query.parameters()).isEmpty();
        }

        @Test
        void rejectsMismatchedTypesAndNonComparisonOperators() {
            assertInvalid(new Definition("Customer", columns("name"),
                    Condition.compare("name", EQ, "orders.total"), null, null), "Cannot compare TEXT 'name' with NUMBER");
            assertInvalid(new Definition("Customer", columns("name"),
                    Condition.compare("name", CONTAINS, "region"), null, null), "supports EQ, NOT_EQ");
        }
    }

    @Nested
    class FieldVersusAggregate {

        @Test
        void comparesWithTheAggregateOfARelatedGroup() {
            Query query = build(new Definition("PurchaseOrder", columns("total"),
                    Condition.compare("total", GT, AVG, "customer.orders.total", null), null, null));

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
            Query query = build(new Definition("PurchaseOrder", columns("total"),
                    Condition.compare("total", GTE, MAX, "total", where("orderDate", LAST_N_DAYS, 30)), null, null));

            assertThat(query.sql()).contains(
                    "WHERE t0.total >= (SELECT MAX(s1_0.total) FROM purchase_order s1_0 WHERE s1_0.order_date BETWEEN ? AND ?)");
            assertThat(query.parameters()).containsExactly(TODAY.minusDays(30), TODAY);
        }

        @Test
        void rowRestrictionsApplyToTheAggregate() {
            RowPolicy euOnly = viewer -> List.of(new RowRestriction("Customer", "region", "EU"));

            Query query = builder(euOnly).build(new Definition("Customer", columns("name"),
                    Condition.compare("name", EQ, Aggregate.MIN, "name", null), null, null), USER);

            assertThat(query.sql()).contains("WHERE t0.name = (SELECT MIN(s1_0.name) FROM customer s1_0 WHERE s1_0.region = ?)",
                    "AND t0.region = ?");
            assertThat(query.parameters()).containsExactly("EU", "EU");
        }

        @Test
        void rejectsInvalidComparisons() {
            assertInvalid(new Definition("PurchaseOrder", columns("total"),
                    Condition.compare("total", GT, Aggregate.LIST, "customer.orders.total", null), null, null),
                    "A LIST cannot be compared");
            assertInvalid(new Definition("PurchaseOrder", columns("total"),
                    Condition.compare("total", GT, AVG, "customer.creditLimit", null), null, null),
                    "must cross a one-to-many relation");
            assertInvalid(new Definition("PurchaseOrder", columns("total"),
                    Condition.compare("orderDate", GT, AVG, "total", null), null, null),
                    "Cannot compare DATE");
        }
    }

    @Nested
    class Exists {

        @Test
        void notExistsWithCondition() {
            Query query = build(new Definition("Customer", columns("name"),
                    Condition.notExists("orders", where("total", GT, 100)), null, null));

            assertThat(query.sql()).isEqualTo("""
                    SELECT t0.name
                    FROM customer t0
                    WHERE NOT EXISTS (SELECT 1 FROM purchase_order s1_0 WHERE s1_0.customer_id = t0.id AND s1_0.total > ?)
                    ORDER BY t0.id""");
            assertThat(query.parameters()).containsExactly(100);
        }

        @Test
        void existsThroughJunctionWithJoinInside() {
            Query query = build(new Definition("Product", columns("name"),
                    Condition.exists("tags", where("tag.name", EQ, "eco")), null, null));

            assertThat(query.sql()).contains("WHERE EXISTS (SELECT 1 FROM product_tag s1_0 "
                    + "LEFT JOIN tag s1_1 ON s1_1.id = s1_0.tag_id WHERE s1_0.product_id = t0.id AND s1_1.name = ?)");
        }

        @Test
        void rowRestrictionsApplyInsideExists() {
            RowPolicy euOnly = viewer -> List.of(new RowRestriction("Customer", "region", "EU"));

            Query query = builder(euOnly).build(new Definition("Customer", columns("name"),
                    Condition.exists("referrals", null), null, null), USER);

            assertThat(query.sql()).contains("EXISTS (SELECT 1 FROM customer s1_0 WHERE s1_0.referred_by_id = t0.id AND s1_0.region = ?)");
        }

        @Test
        void existsDoesNotFanOutSoListsStayPerRoot() {
            Query query = build(new Definition("Customer",
                    List.of(Column.of("name"), Column.of(Aggregate.LIST, "orders.total")),
                    Condition.exists("orders", where("total", GT, 100)), null, null));

            assertThat(query.sql()).doesNotContain("GROUP BY");
        }

        @Test
        void needsARelation() {
            assertInvalid(new Definition("Customer", columns("name"),
                    where("name", FilterOperator.EXISTS, null), null, null), "need a \"relation\"");
            assertInvalid(new Definition("Customer", columns("name"),
                    Condition.exists("referredBy", null), null, null), "needs a path through a one-to-many relation");
        }
    }

    @Nested
    class ListOptions {

        @Test
        void listOfObjects() {
            Query query = build("Customer", List.of(Column.of("name"), Column.objects("orders", "orderDate", "total")));

            assertThat(query.sql()).contains("(SELECT CAST(JSON_ARRAYAGG(JSON_OBJECT('orderDate' VALUE s1_0.order_date, "
                    + "'total' VALUE s1_0.total) ORDER BY s1_0.order_date, s1_0.total) AS VARCHAR) FROM purchase_order s1_0 "
                    + "WHERE s1_0.customer_id = t0.id AND (s1_0.order_date IS NOT NULL OR s1_0.total IS NOT NULL))");
            assertThat(query.columns().get(1).keys()).containsExactly("orderDate", "total");
        }

        @Test
        void objectFieldsMustNotFanOutAgainAndKeysMustBeCatalogPaths() {
            assertInvalid(new Definition("Customer",
                    List.of(Column.of("name"), Column.objects("orders", "lines.quantity")), null, null, null),
                    "reachable through to-one relations");
            assertInvalid(new Definition("Customer",
                    List.of(Column.of("name"), Column.objects("orders", "total') --")), null, null, null),
                    "Unknown field");
        }

        @Test
        void filteredSortedAndLimitedList() {
            Query query = build("Customer", List.of(Column.of("name"),
                    Column.of(Aggregate.LIST, "orders.total").where(where("total", GT, 5)).order(Direction.DESC).limit(3)));

            assertThat(query.sql()).contains("ARRAY_AGG(DISTINCT s1_0.total ORDER BY s1_0.total DESC) FROM purchase_order s1_0 "
                    + "WHERE s1_0.customer_id = t0.id AND s1_0.total > ? AND s1_0.total IS NOT NULL)");
            assertThat(query.parameters()).containsExactly(5);
            assertThat(query.columns().get(1).listLimit()).isEqualTo(3);
        }

        @Test
        void columnOptionsNeedPerRootMode() {
            assertInvalid(new Definition("Customer", List.of(Column.of("region"), Column.count(),
                            Column.of(Aggregate.LIST, "orders.total").order(Direction.DESC)), null, null, null),
                    "need per-root mode");
        }
    }

    @Nested
    class PerRoot {

        private final List<Column> perCustomer = List.of(Column.of("name"),
                Column.of(Aggregate.COUNT, "orders.total"),
                Column.of(SUM, "orders.total"),
                Column.of(Aggregate.LIST, "referrals.name"));

        @Test
        void childAggregatesBecomeSubqueriesWithoutGroupBy() {
            Query query = build(new Definition("Customer", perCustomer, null,
                    where(SUM, "orders.total", GT, 1000), List.of(new Sort("orders.total", SUM, Direction.DESC)), true));

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
            Query query = builder(SqlDialect.POSTGRESQL).build(new Definition("Customer", perCustomer, null,
                    where(SUM, "orders.total", GT, 1000), List.of(new Sort("orders.total", SUM, Direction.DESC)), true),
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
            Query query = builder(SqlDialect.POSTGRESQL).build(new Definition("Customer", perCustomer, null,
                    where(COUNT, "orders.total", FilterOperator.LT, 1), List.of(), true), USER);

            assertThat(query.sql()).contains("WHERE COALESCE(g5.v, 0) < ?");
        }

        @Test
        void sumAcrossIndependentBranchesIsSafePerRoot() {
            Query query = build(new Definition("Customer", perCustomer, null, null, null, true));

            assertThat(query.sql()).doesNotContain("LEFT JOIN");
        }

        @Test
        void rejectsWhatOnlyMakesSenseWhenGrouping() {
            assertInvalid(new Definition("Customer", List.of(Column.of("name"), Column.count()),
                    null, null, null, true), "perRoot needs every aggregate to go through a one-to-many relation");
            assertInvalid(new Definition("Customer", List.of(Column.of("orders.total"),
                    Column.of(Aggregate.LIST, "referrals.name")), null, null, null, true), "with EXISTS / NOT_EXISTS instead");
        }
    }

    @Nested
    class ColumnUsages {

        @Test
        void recordsHowEachColumnIsUsed() {
            Query query = build(new Definition("PurchaseOrder", columns("total", "customer.name"),
                    and(where("customer.name", CONTAINS, "a"), where("orderDate", DateGroup.YEAR, EQ, 2026),
                            where("total", GT, 5)),
                    null, List.of(Sort.desc("customer.region"))));

            assertThat(query.columnUsages()).containsExactlyInAnyOrder(
                    new Dataset.ColumnUsage("customer", "name", Dataset.ColumnUsage.Kind.FILTER_CONTAINS),
                    new Dataset.ColumnUsage("purchase_order", "order_date", Dataset.ColumnUsage.Kind.FILTER_EXPRESSION),
                    new Dataset.ColumnUsage("purchase_order", "total", Dataset.ColumnUsage.Kind.FILTER),
                    new Dataset.ColumnUsage("customer", "region", Dataset.ColumnUsage.Kind.SORT),
                    new Dataset.ColumnUsage("customer", "id", Dataset.ColumnUsage.Kind.JOIN));
        }
    }

    @Nested
    class FanOutProtection {

        @Test
        void twoIndependentOneToManyBranchesAreRejected() {
            assertInvalid(new Definition("Customer", columns("orders.total", "referrals.name"), null, null, null),
                    "'orders' and 'referrals' are separate one-to-many relations");
        }

        @Test
        void sumAboveAOneToManyJoinIsRejected() {
            assertInvalid(new Definition("PurchaseOrder",
                            List.of(Column.of("lines.quantity"), Column.of(SUM, "total")), null, null, null),
                    "SUM of 'total' would be inflated by the one-to-many relation 'lines'");
        }

        @Test
        void maxAboveAOneToManyJoinIsFine() {
            Query query = build("PurchaseOrder", List.of(Column.of("lines.quantity"), Column.of(MAX, "total")));

            assertThat(query.sql()).startsWith("SELECT t1.quantity, MAX(t0.total)");
        }

        @Test
        void chainedOneToManyIsFine() {
            Query query = build("Customer", List.of(Column.of("name"), Column.of(SUM, "orders.lines.quantity")));

            assertThat(query.sql()).contains("LEFT JOIN order_line t2 ON t2.order_id = t1.id");
        }
    }

    @Nested
    class Access {

        @Test
        void restrictedFieldIsDeniedEverywhere() {
            List<Definition> attempts = List.of(
                    new Definition("Customer", columns("creditLimit"), null, null, null),
                    new Definition("PurchaseOrder", columns("total"), where("customer.creditLimit", GT, 1), null, null),
                    new Definition("Customer", List.of(Column.of("region"), Column.count()),
                            null, where(MAX, "creditLimit", GT, 1), null),
                    new Definition("Customer", columns("name"), null, null, List.of(Sort.desc("creditLimit"))));

            for (Definition attempt : attempts) {
                assertThatThrownBy(() -> builder().build(attempt, USER))
                        .isInstanceOf(AccessDeniedException.class)
                        .hasMessageContaining("Credit Limit");
            }
        }

        @Test
        void restrictedFieldIsAllowedWithAuthority() {
            Query query = builder().build(new Definition("Customer", columns("creditLimit"), null, null, null),
                    FINANCE_USER);

            assertThat(query.sql()).startsWith("SELECT t0.credit_limit");
        }

        private final RowPolicy euOnly = viewer -> List.of(new RowRestriction("Customer", "region", "EU"));

        @Test
        void restrictionOnRootFiltersRows() {
            Query query = builder(euOnly).build(
                    new Definition("Customer", columns("name"), where("name", CONTAINS, "a"), null, null), USER);

            assertThat(query.sql()).contains("WHERE t0.name LIKE ? ESCAPE '\\'\n  AND t0.region = ?");
            assertThat(query.parameters()).containsExactly("%a%", "EU");
        }

        @Test
        void ownedRootInheritsRestrictionAndJoinedRestrictedEntityIsHiddenInOnClause() {
            Query query = builder(euOnly).build(
                    new Definition("PurchaseOrder", columns("total", "customer.name"), null, null, null), USER);

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
            RowPolicy euOrUs = viewer -> List.of(new RowRestriction("Customer", "region", List.of("EU", "US")));

            Query query = builder(euOrUs).build(new Definition("Customer", columns("name"), null, null, null), USER);

            assertThat(query.sql()).contains("WHERE t0.region IN (?, ?)");
        }

        @Test
        void unrelatedDataIsNotRestricted() {
            Query query = builder(euOnly).build(new Definition("Warehouse", columns("city"), null, null, null), USER);

            assertThat(query.sql()).doesNotContain("WHERE");
        }
    }

    /** User input can only ever select catalog entries or become a bind parameter. */
    @Nested
    class SqlInjection {

        private static final String PAYLOAD = "x'; DROP TABLE customer; --";

        @Test
        void maliciousRootFieldOrRelationIsRejected() {
            assertInvalid(new Definition(PAYLOAD, columns("name"), null, null, null), "Unknown entity");
            assertInvalid(new Definition("Customer", columns(PAYLOAD), null, null, null), "Unknown field");
            assertInvalid(new Definition("Customer", columns("name) FROM customer; --"), null, null, null), "Unknown");
            assertInvalid(new Definition("Customer", columns(PAYLOAD + ".name"), null, null, null), "Unknown relation");
            assertInvalid(new Definition("Customer", columns("name"), null, null, List.of(Sort.asc(PAYLOAD))),
                    "Unknown field");
            assertInvalid(new Definition("Customer", columns("name"), where(PAYLOAD, EQ, 1), null, null),
                    "Unknown field");
        }

        @Test
        void maliciousValuesOnlyEverBecomeParameters() {
            Query query = build(new Definition("Customer", columns("name"),
                    or(where("name", EQ, PAYLOAD), where("name", CONTAINS, PAYLOAD), where("region", IN, List.of(PAYLOAD))),
                    null, null));

            assertThat(query.sql()).doesNotContain("DROP").doesNotContain("'x'");
            assertThat(query.parameters()).contains(PAYLOAD);
        }

        @Test
        void columnLabelsNeverReachTheSql() {
            Query query = build("Customer", List.of(new Column("name", null, PAYLOAD)));

            assertThat(query.sql()).isEqualTo("SELECT t0.name\nFROM customer t0\nORDER BY t0.id");
            assertThat(query.columns().getFirst().label()).isEqualTo(PAYLOAD);
        }

        @DatasetEntity(label = "Evil")
        @Table(name = "evil; DROP TABLE x")
        static class EvilTable {
            @Id Long id;
        }

        @DatasetEntity(label = "Evil")
        static class EvilColumn {
            @Id Long id;
            @DatasetField(label = "Bad") @jakarta.persistence.Column(name = "a\" OR 1=1 --") String bad;
        }

        @Test
        void unsafeIdentifiersInAnnotationsFailAtStartup() {
            assertThatThrownBy(() -> DatasetCatalog.of(List.of(EvilTable.class)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a safe SQL identifier");
            assertThatThrownBy(() -> DatasetCatalog.of(List.of(EvilColumn.class)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a safe SQL identifier");
        }
    }

    @Nested
    class CatalogValidation {

        @Test
        void unknownFieldRelationAndDepth() {
            assertInvalid(new Definition("Customer", columns("email"), null, null, null), "Unknown field: email");
            assertInvalid(new Definition("Customer", columns("nope.name"), null, null, null),
                    "Unknown relation 'nope' on Customer");
            assertInvalid(new Definition("Customer", columns("name", "Warehouse.city"), null, null, null),
                    "Unknown relation 'Warehouse'");
            assertInvalid(new Definition("Customer",
                    columns("referredBy.referredBy.referredBy.referredBy.referredBy.name"), null, null, null), "too deep");
        }

        @DatasetEntity(label = "Broken")
        static class UnknownTarget {
            @Id Long id;
            @DatasetReference(target = "DoesNotExist") Long otherId;
        }

        @DatasetEntity(label = "Clash")
        static class NameClash {
            @Id Long id;
            @DatasetField(label = "Customer") String customer;
            @DatasetReference(target = "Customer") Long customerId;
        }

        @DatasetEntity(label = "Composite Ref")
        static class ReferencesCompositeKey {
            @Id Long id;
            @DatasetReference(target = "ProductTag") Long productTagId;
        }

        @Test
        void misconfigurationFailsAtStartup() {
            assertThatThrownBy(() -> DatasetCatalog.of(List.of(Customer.class, UnknownTarget.class)))
                    .hasMessageContaining("unknown dataset-ready entity 'DoesNotExist'");
            assertThatThrownBy(() -> DatasetCatalog.of(List.of(Customer.class, NameClash.class)))
                    .hasMessageContaining("Name 'customer' is used twice");
            assertThatThrownBy(() -> DatasetCatalog.of(List.of(Product.class, Tag.class, ProductTag.class,
                    ReferencesCompositeKey.class)))
                    .hasMessageContaining("composite key");
        }
    }

    private void assertInvalid(Definition definition, String message) {
        assertThatThrownBy(() -> build(definition))
                .isInstanceOf(InvalidException.class)
                .hasMessageContaining(message);
    }
}
