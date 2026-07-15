package org.jenkinsci.plugins.ansible_tower.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TowerVersionTest {

    @Test
    void init_validVersionNumber() {
        assertDoesNotThrow(() -> new TowerVersion("3.3.0"));
    }

    @ParameterizedTest
    @CsvSource({
        "3.0, 'The version passed to TowerVersion must be in the format X.Y.Z'",
        "a.3.0, 'The major version'",
        "3.b.0, 'The minor version'",
        "3.3.a, 'The point version'"
    })
    void init_invalidVersionNumber(String version, String expectedMessage) {
        AnsibleTowerException exception = assertThrows(
                AnsibleTowerException.class,
                () -> new TowerVersion(version));

        assertThat(exception.getMessage(), containsString(expectedMessage));
    }

    @ParameterizedTest
    @CsvSource({
        "3.3.0, 2.3.0, true",
        "3.3.0, 3.2.0, true",
        "3.3.0, 3.3.0, true",
        "3.3.1, 3.3.0, true",
        "2.3.1, 3.3.0, false",
        "3.2.1, 3.3.0, false",
        "3.3.0, 3.3.1, false"
    })
    void is_greater_or_equal(String myVersion, String otherVersion, boolean expectedResult) throws Exception {
        TowerVersion towerVersion = new TowerVersion(myVersion);

        boolean result = towerVersion.is_greater_or_equal(otherVersion);

        assertThat(result, is(expectedResult));
    }
}
