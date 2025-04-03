package org.jenkinsci.plugins.ansible_tower.util;

import com.sun.deploy.security.ruleset.ExceptionRule;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Enclosed.class)
public class TowerVersionTest {

    public static class TowerVersionTestInitialization {
        @Rule
        public ExpectedException expectedEx = ExpectedException.none();

        @Test
        public void init_validVersionNumber() throws AnsibleTowerException {
            new TowerVersion("3.3.0");
        }

        @Test
        public void init_invalidVersionNumberFormat() throws AnsibleTowerException {
            expectedEx.expectMessage("The version passed to TowerVersion must be in the format X.Y.Z");
            new TowerVersion("3.0");
        }

        @Test
        public void init_invalidVersionNumberMajor() throws AnsibleTowerException {
            expectedEx.expectMessage("The major version");
            new TowerVersion("a.3.0");
        }

        @Test
        public void init_invalidVersionNumberMinor() throws AnsibleTowerException {
            expectedEx.expectMessage("The minor version");
            new TowerVersion("3.b.0");
        }

        @Test
        public void init_invalidVersionNumberDot() throws AnsibleTowerException {
            expectedEx.expectMessage("The point version");
            new TowerVersion("3.3.a");
        }
    }

    @RunWith(Parameterized.class)
    public static class TowerVersionTestIsGreaterOrEqual {

        @Parameterized.Parameter
        public String myVersion;

        @Parameterized.Parameter(1)
        public String otherVersion;

        @Parameterized.Parameter(2)
        public boolean expectedResult;


        @Parameterized.Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][]{
                    {"3.3.0", "2.3.0", true},
                    {"3.3.0", "3.2.0", true},
                    {"3.3.0", "3.3.0", true},
                    {"3.3.1", "3.3.0", true},
                    {"2.3.1", "3.3.0", false},
                    {"3.2.1", "3.3.0", false},
                    {"3.3.0", "3.3.1", false}
            });
        }

        @Test
        public void is_greater_or_equal() throws Exception {
            TowerVersion myTower = new TowerVersion(this.myVersion);
            boolean result = myTower.is_greater_or_equal(this.otherVersion);
            Assert.assertThat(result, CoreMatchers.is(this.expectedResult));
        }
    }
}
