package com.gucardev.dynamicreportsentity.reports;

import com.gucardev.entityreports.IndexAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** Fails the build when someone adds a @DatasetReference without indexing its column. */
@SpringBootTest
class IndexAdvisorTest {

    @Autowired
    IndexAdvisor indexAdvisor;

    @Test
    void everyDatasetJoinColumnIsIndexed() throws SQLException {
        assertThat(indexAdvisor.findMissingIndexes()).isEmpty();
    }
}
