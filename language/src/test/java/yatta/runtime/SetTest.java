package yatta.runtime;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
public class SetTest {

  private static final int N = 1 << 18;
  private static final int M = 1 << 12;
  private static final long SEED = 0L;
  
  @Test
  public void testAddLookup() {
    Set set = Set.empty(Murmur3.INSTANCE, SEED);
    for (int i = 0; i < N; i++) {
      if (i % 2 == 0) {
        set = set.add(new O(i));
      }
    }
    for (int i = 0; i < N; i++) {
      if (i % 2 == 0) {
        assertTrue(set.contains(new O(i)));
      } else {
        assertFalse(set.contains(new O(i)));
      }
    }
  }

  @Test
  public void testRemoveLookup() {
    Set set = Set.empty(Murmur3.INSTANCE, SEED);
    for (int i = 0; i < N; i++) {
      set = set.add(new O(i));
    }
    for (int i = 0; i < N; i++) {
      if (i % 2 == 0) {
        set = set.remove(new O(i));
      }
    }
    for (int i = 0; i < N; i++) {
      if (i % 2 == 0) {
        assertFalse(set.contains(new O(i)));
      } else {
        assertTrue(set.contains(new O(i)));
        set = set.remove(new O(i));
      }
    }
    for (int i = 0; i < N; i++) {
      assertFalse(set.contains(new O(i)));
    }
  }

  @Test
  public void testEquality() {
    Set fst = Set.empty(Murmur3.INSTANCE, SEED);
    Set snd = Set.empty(Murmur3.INSTANCE, SEED);
    for (int i = 0; i < M; i++) {
      assertEquals(fst, snd);
      fst = fst.add(new O(i));
      assertNotEquals(fst, snd);
      snd = snd.add(new O(i));
    }
  }

  private static final class O {
    final long value;
    final int hash;

    O (final long value) {
      this.value = value;
      this.hash = (int) value & 0x3ff;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof O)) {
        return false;
      }
      final O that = (O) o;
      return this.value == that.value;
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }
}