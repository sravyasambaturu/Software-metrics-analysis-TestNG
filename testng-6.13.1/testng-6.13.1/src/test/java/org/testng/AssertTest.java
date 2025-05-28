package org.testng;

import org.testng.annotations.Test;
import org.testng.collections.Maps;
import org.testng.collections.Sets;
import testhelper.PerformanceUtils;

import java.util.*;


/**
 * This class/interface
 */
public class AssertTest {
  @Test
  public void nullObjectArrayAssertEquals() {
    Object[] expected= null;
    Object[] actual= null;
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void nullObjectArrayAssertNoOrder() {
    Object[] expected= null;
    Object[] actual= null;
    Assert.assertEqualsNoOrder(actual, expected);
  }

  @Test
  public void nullCollectionAssertEquals() {
    Collection expected = null;
    Collection actual = null;
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void nullSetAssertEquals() {
    Set expected = null;
    Set actual = null;
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void nullMapAssertEquals() {
    Map expected = null;
    Map actual = null;
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void setAssertEquals() {
    Set expected = Sets.newHashSet();
    Set actual = Sets.newHashSet();

    expected.add(1);
    expected.add("a");
    actual.add("a");
    actual.add(1);

    Assert.assertEquals(actual, expected);
  }

  @Test
  public void mapAssertEquals() {
    Map expected = Maps.newHashMap();
    Map actual = Maps.newHashMap();

    expected.put(null, "a");
    expected.put("a", "a");
    expected.put("b", "c");
    actual.put("b", "c");
    actual.put(null, "a");
    actual.put("a", "a");

    Assert.assertEquals(actual, expected);
  }

  @Test
  public void oneNullMapAssertEquals() {
    Map expected = Maps.newHashMap();
    Map actual = null;
    try {
      Assert.assertEquals(actual, expected);
      Assert.fail("AssertEquals didn't fail");
    }
    catch (AssertionError error) {
      //do nothing
    }
  }

  @Test
  public void oneNullSetAssertEquals() {
    Set expected = null;
    Set actual = Sets.newHashSet();
    try {
      Assert.assertEquals(actual, expected);
      Assert.fail("AssertEquals didn't fail");
    }
    catch (AssertionError error) {
      //do nothing
    }
  }

  /**
   * Testing comparison algorithm using big arrays.
   *
   * @see <a href="https://github.com/cbeust/testng/issues/1384">Issue #1384 – Huge performance issue between 6.5.2
   * and 6.11</a>
   */
  @Test
  public void compareLargeArrays() {
    int length = 1024 * 100;
    byte[] first = new byte[length];
    byte[] second = new byte[length];

    Random rnd = new Random();
    rnd.nextBytes(first);
    System.arraycopy(first, 0, second, 0, length);

    long before = PerformanceUtils.measureAllocatedMemory();
    Assert.assertEquals(first, second);
    long memoryUsage = PerformanceUtils.measureAllocatedMemory() - before;

    // assertEquals() with primitive type arrays requires ~65Kb of memory
    // assertEquals() with Object-type requires ~3Mb of memory when comparing 100Kb arrays.
    // choosing 100Kb as a threshold
    Assert.assertTrue(memoryUsage < 100 * 1024, "Amount of used memory should be approximately 65Kb");
  }

  @Test
  public void compareShortArrays() {
    short[] actual = {Short.MIN_VALUE, 0, Short.MAX_VALUE};
    short[] expected = {Short.MIN_VALUE, 0, Short.MAX_VALUE};
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void compareIntArrays() {
    int[] actual = {Integer.MIN_VALUE, 0, Integer.MAX_VALUE};
    int[] expected = {Integer.MIN_VALUE, 0, Integer.MAX_VALUE};
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void compareLongArrays() {
    long[] actual = {Long.MIN_VALUE, 0, Long.MAX_VALUE};
    long[] expected = {Long.MIN_VALUE, 0, Long.MAX_VALUE};
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void compareBooleanArrays() {
    boolean[] actual = {true, false};
    boolean[] expected = {true, false};
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void compareCharacterArrays() {
    char[] actual = {'a', '1', '#'};
    char[] expected = {'a', '1', '#'};
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void compareFloatArrays() {
    float[] actual = {(float) Math.PI, (float) Math.E};
    float[] expected = {(float) Math.PI, (float) Math.E};
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void compareDoubleArrays() {
    double[] actual = {Math.PI, Math.E};
    double[] expected = {Math.PI, Math.E};
    Assert.assertEquals(actual, expected);
  }

  @Test(expectedExceptions = AssertionError.class)
  public void assertEqualsMapShouldFail() {
    Map<String, String> mapActual = new HashMap<String, String>() {{
      put("a","1");
    }};
    Map<String, String> mapExpected = new HashMap<String, String>() {{
      put("a","1");
      put("b","2");
    }};

    Assert.assertEquals(mapActual, mapExpected);
  }

  @Test(expectedExceptions = AssertionError.class)
  public void assertEqualsSymmetricScalar() {
    Assert.assertEquals(new Asymmetric(42, 'd'), new Contrived(42));
  }

  @Test(expectedExceptions = AssertionError.class)
  public void assertEqualsSymmetricArrays() {
    Object[] actual = {1, new Asymmetric(42, 'd'), "inDay"};
    Object[] expected = {1, new Contrived(42), "inDay"};
    Assert.assertEquals(actual, expected);
  }

  class Contrived {

    int integer;

    Contrived(int integer){
      this.integer = integer;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Contrived)) return false;

      Contrived contrived = (Contrived) o;

      if (integer != contrived.integer) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return integer;
    }
  }

  class Asymmetric extends Contrived {

      char character;

      Asymmetric(int integer, char character) {
          super(integer);
          this.character = character;
      }

      @Override
      public boolean equals(Object o) {
          if (this == o) return true;
          if (!(o instanceof Asymmetric)) return false;
          if (!super.equals(o)) return false;

          Asymmetric that = (Asymmetric) o;

          if (character != that.character) return false;

          return true;
      }

      @Override
      public int hashCode() {
          int result = super.hashCode();
          result = 31 * result + (int) character;
          return result;
      }
  }
}
