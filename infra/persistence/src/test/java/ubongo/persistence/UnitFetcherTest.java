package ubongo.persistence;
/*
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import ubongo.common.datatypes.unit.FileUnitParameter;
import ubongo.common.datatypes.unit.StringUnitParameter;
import ubongo.common.datatypes.unit.Unit;
import ubongo.persistence.exceptions.UnitFetcherException;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(UnitFetcher.class)
@PowerMockIgnore("javax.management.*")*/
public class UnitFetcherTest {

    /*
    private static final int TEST_UNIT_ID = 7;
    private UnitFetcher unitFetcher;

    @Before
    public void init() throws Exception {
        unitFetcher = new UnitFetcher("src/test/resources/");
    }

    @Test
    public void testGetUnit() throws UnitFetcherException {
        Unit unit = (Unit) unitFetcher.getUnit(TEST_UNIT_ID);
        assertNotNull("Failed to unmarshal XML to Unit Object", unit);
        assertEquals("Unit created but with wrong ID", TEST_UNIT_ID, unit.getId());
        assertEquals("Unit created but with wrong description", "Unit-test unit", unit.getDescription());
        assertFalse("Unit created but with empty parameter list", unit.getParameters().isEmpty());
        String badParameter = "Unit created but with bad parameter";
        assertEquals(badParameter, FileUnitParameter.class, unit.getParameters().get(0).getClass());
        assertEquals(badParameter, "myFile", unit.getParameters().get(0).getName());
        assertEquals(badParameter, StringUnitParameter.class, unit.getParameters().get(1).getClass());
        assertEquals(badParameter, "myStr", unit.getParameters().get(1).getName());
    }

    @Test(expected=UnitFetcherException.class)
    public void testGetNonexistentUnit() throws UnitFetcherException {
        Unit unit = (Unit) unitFetcher.getUnit(TEST_UNIT_ID + 1);
    } */

}
