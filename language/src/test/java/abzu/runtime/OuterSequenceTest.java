package abzu.runtime;

import org.junit.Test;

import static abzu.runtime.OuterSequence.*;
import static org.junit.Assert.assertEquals;

public class OuterSequenceTest {

  private static final byte UTF8_1B = (byte) 0x05;
  private static final byte UTF8_2B = (byte) 0xc5;
  private static final byte UTF8_3B = (byte) 0xe5;
  private static final byte UTF8_4B = (byte) 0xf5;
  private static final byte UTF8_CC = (byte) 0x85;

  private static final OuterSequence NIL = OuterSequence.sequence();

  @Test
  public void testOffsetOf() {
    byte[] bytes;
    // leftmost, U+0000 - U+007F
    bytes = new byte[] { 0, 0, 0, 0, UTF8_1B, 0 };
    writeMeta(bytes, (1 << 31) | 2);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 0)]);
    // leftmost, U+0080 - U+07FF
    bytes = new byte[] { 0, 0, 0, 0, UTF8_2B, UTF8_CC, 0 };
    writeMeta(bytes, (1 << 31) | 2);
    assertEquals(UTF8_2B, bytes[offsetOf(bytes, 0)]);
    // leftmost, U+0800 - U+FFFF
    bytes = new byte[] { 0, 0, 0, 0, UTF8_3B, UTF8_CC, UTF8_CC, 0 };
    writeMeta(bytes, (1 << 31) | 2);
    assertEquals(UTF8_3B, bytes[offsetOf(bytes, 0)]);
    // leftmost, U+10000 - U+10FFFF
    bytes = new byte[] { 0, 0, 0, 0, UTF8_4B, UTF8_CC, UTF8_CC, UTF8_CC, 0 };
    writeMeta(bytes, (1 << 31) | 2);
    assertEquals(UTF8_4B, bytes[offsetOf(bytes, 0)]);
    // rightmost, U+0000 - U+007F
    bytes = new byte[] { 0, 0, 0, 0, 0, UTF8_1B };
    writeMeta(bytes, (1 << 31) | 2);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 1)]);
    // rightmost, U+0080 - U+07FF
    bytes = new byte[] { 0, 0, 0, 0, 0, UTF8_2B, UTF8_CC };
    writeMeta(bytes, (1 << 31) | 2);
    assertEquals(UTF8_2B, bytes[offsetOf(bytes, 1)]);
    // rightmost, U+0800 - U+FFFF
    bytes = new byte[] { 0, 0, 0, 0, 0, UTF8_3B, UTF8_CC, UTF8_CC };
    writeMeta(bytes, (1 << 31) | 2);
    assertEquals(UTF8_3B, bytes[offsetOf(bytes, 1)]);
    // rightmost, U+10000 - U+10FFFF
    bytes = new byte[] { 0, 0, 0, 0, 0, UTF8_4B, UTF8_CC, UTF8_CC, UTF8_CC };
    writeMeta(bytes, (1 << 31) | 2);
    assertEquals(UTF8_4B, bytes[offsetOf(bytes, 1)]);
    // mid-left, U+0000 - U+007F
    bytes = new byte[] { 0, 0, 0, 0, 0, UTF8_1B, 0, 0 };
    writeMeta(bytes, (1 << 31) | 4);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 1)]);
    // mid-left, U+0080 - U+07FF
    bytes = new byte[] { 0, 0, 0, 0, UTF8_2B, UTF8_CC, UTF8_1B, 0, 0 };
    writeMeta(bytes, (1 << 31) | 4);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 1)]);
    // mid-left, U+0800 - U+FFFF
    bytes = new byte[] { 0, 0, 0, 0, UTF8_3B, UTF8_CC, UTF8_CC, UTF8_1B, 0, 0 };
    writeMeta(bytes, (1 << 31) | 4);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 1)]);
    // mid-left, U+10000 - U+10FFFF
    bytes = new byte[] { 0, 0, 0, 0, UTF8_4B, UTF8_CC, UTF8_CC, UTF8_CC, UTF8_1B, 0, 0 };
    writeMeta(bytes, (1 << 31) | 4);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 1)]);
    // mid-right, U+0000 - U+007F
    bytes = new byte[] { 0, 0, 0, 0, 0, 0, UTF8_1B, 0 };
    writeMeta(bytes, (1 << 31) | 4);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 2)]);
    // mid-right, U+0080 - U+07FF
    bytes = new byte[] { 0, 0, 0, 0, 0, 0, UTF8_1B, UTF8_2B, UTF8_CC };
    writeMeta(bytes, (1 << 31) | 4);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 2)]);
    // mid-right, U+0800 - U+FFFF
    bytes = new byte[] { 0, 0, 0, 0, 0, 0, UTF8_1B, UTF8_3B, UTF8_CC, UTF8_CC };
    writeMeta(bytes, (1 << 31) | 4);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 2)]);
    // mid-right, U+10000 - U+10FFFF
    bytes = new byte[] { 0, 0, 0, 0, 0, 0, UTF8_1B, UTF8_4B, UTF8_CC, UTF8_CC, UTF8_CC };
    writeMeta(bytes, (1 << 31) | 4);
    assertEquals(UTF8_1B, bytes[offsetOf(bytes, 2)]);
  }

  @Test
  public void testVarIntReadWrite() {
    final byte[] bytes = new byte[5];
    testVarIntReadWrite(bytes, 0x0);
    testVarIntReadWrite(bytes, 0x7a);
    testVarIntReadWrite(bytes, 0x8a);
    testVarIntReadWrite(bytes, 0x3abc);
    testVarIntReadWrite(bytes, 0x4abc);
    testVarIntReadWrite(bytes, 0x1abcde);
    testVarIntReadWrite(bytes, 0x2abcde);
    testVarIntReadWrite(bytes, 0xfabcdef);
    testVarIntReadWrite(bytes, 0x1abcdef0);
    testVarIntReadWrite(bytes, 0x7fffffff);
  }

  private static void testVarIntReadWrite(byte[] bytes, int value) {
    varIntWrite(value, bytes, 0);
    assertEquals(value, varIntRead(bytes, 0));
  }

  @Test
  public void testReadWriteMeta() {
    final byte[] bytes = new byte[4];
    testReadWriteMeta(bytes, 0x0);
    testReadWriteMeta(bytes, 0x7abcdef0);
    testReadWriteMeta(bytes, 0xffffffff);
  }

  private static void testReadWriteMeta(byte[] bytes, int value) {
    writeMeta(bytes, value);
    assertEquals(value, readMeta(bytes));
  }

  @Test
  public void testPush() {
    assertEquals("a", NIL.push("a").lookup(0, null));
    final byte[] bytes = fourBytes(0xa, 0xb, 0xc, 0xd);
    assertEquals((byte) 0xa, NIL.push(bytes).lookup(0, null));
    assertEquals((byte) 0xb, NIL.push(bytes).lookup(1, null));
    assertEquals((byte) 0xc, NIL.push(bytes).lookup(2, null));
    assertEquals((byte) 0xd, NIL.push(bytes).lookup(3, null));
    final Char c0 = new Char(UTF8_1B);
    final Char c1 = new Char(UTF8_2B, UTF8_CC);
    final Char c2 = new Char(UTF8_3B, UTF8_CC, UTF8_CC);
    final Char c3 = new Char(UTF8_4B, UTF8_CC, UTF8_CC, UTF8_CC);
    final byte[] chars = fourChars(c0, c1, c2, c3);
    assertEquals(c0, NIL.push(chars).lookup(0, null));
    assertEquals(c1, NIL.push(chars).lookup(1, null));
    assertEquals(c2, NIL.push(chars).lookup(2, null));
    assertEquals(c3, NIL.push(chars).lookup(3, null));
    // TODO
  }

  @Test
  public void testInject() {
    assertEquals("a", NIL.inject("a").lookup(0, null));
    final byte[] bytes = fourBytes(0xa, 0xb, 0xc, 0xd);
    assertEquals((byte) 0xa, NIL.inject(bytes).lookup(0, null));
    assertEquals((byte) 0xb, NIL.inject(bytes).lookup(1, null));
    assertEquals((byte) 0xc, NIL.inject(bytes).lookup(2, null));
    assertEquals((byte) 0xd, NIL.inject(bytes).lookup(3, null));
    final Char c0 = new Char(UTF8_1B);
    final Char c1 = new Char(UTF8_2B, UTF8_CC);
    final Char c2 = new Char(UTF8_3B, UTF8_CC, UTF8_CC);
    final Char c3 = new Char(UTF8_4B, UTF8_CC, UTF8_CC, UTF8_CC);
    final byte[] chars = fourChars(c0, c1, c2, c3);
    assertEquals(c0, NIL.inject(chars).lookup(0, null));
    assertEquals(c1, NIL.inject(chars).lookup(1, null));
    assertEquals(c2, NIL.inject(chars).lookup(2, null));
    assertEquals(c3, NIL.inject(chars).lookup(3, null));
    // TODO
  }

  @Test
  public void testFirst() {
    OuterSequence seq;
    seq = NIL.inject("a");
    assertEquals(seq.lookup(0, null), seq.first());
    seq = NIL.inject(fourBytes(0xa, 0x0, 0x0, 0x0));
    assertEquals(seq.lookup(0, null), seq.first());
    seq = NIL.inject(fourChars(new Char(UTF8_2B, UTF8_CC), new Char((byte) 0), new Char((byte) 0), new Char((byte) 0)));
    assertEquals(seq.lookup(0, null), seq.first());
    // TODO
  }

  @Test
  public void testRemoveFirst() {
    assertEquals(NIL, NIL.push(new Object()).removeFirst());
    final byte[] bytes = fourBytes(0xa, 0xb, 0xc, 0xd);
    assertEquals((byte) 0xb, NIL.push(bytes).removeFirst().lookup(0, null));
    assertEquals((byte) 0xd, NIL.push(bytes).removeFirst().lookup(2, null));
    final Char c1 = new Char(UTF8_2B, UTF8_CC);
    final Char c2 = new Char(UTF8_3B, UTF8_CC, UTF8_CC);
    final Char c3 = new Char(UTF8_4B, UTF8_CC, UTF8_CC, UTF8_CC);
    final byte[] chars = fourChars(new Char(UTF8_1B), c1, c2, c3);
    assertEquals(c1, NIL.push(chars).removeFirst().lookup(0, null));
    assertEquals(c3, NIL.push(chars).removeFirst().lookup(2, null));
    // TODO
  }

  @Test
  public void testLast() {
    OuterSequence seq;
    seq = NIL.push("a");
    assertEquals(seq.lookup(0, null), seq.last());
    seq = NIL.push(fourBytes(0x0, 0x0, 0x0, 0xa));
    assertEquals(seq.lookup(3, null), seq.last());
    seq = NIL.push(fourChars(new Char((byte) 0), new Char((byte) 0), new Char((byte) 0), new Char(UTF8_2B, UTF8_CC)));
    assertEquals(seq.lookup(3, null), seq.last());
    // TODO
  }

  @Test
  public void testRemoveLast() {
    assertEquals(NIL, NIL.push(new Object()).removeLast());
    final byte[] bytes = fourBytes(0xa, 0xb, 0xc, 0xd);
    assertEquals((byte) 0xa, NIL.push(bytes).removeLast().lookup(0, null));
    assertEquals((byte) 0xc, NIL.push(bytes).removeLast().lookup(2, null));
    final Char c0 = new Char(UTF8_1B);
    final Char c1 = new Char(UTF8_2B, UTF8_CC);
    final Char c2 = new Char(UTF8_3B, UTF8_CC, UTF8_CC);
    final byte[] chars = fourChars(c0, c1, c2, new Char(UTF8_4B, UTF8_CC, UTF8_CC, UTF8_CC));
    assertEquals(c0, NIL.push(chars).removeLast().lookup(0, null));
    assertEquals(c2, NIL.push(chars).removeLast().lookup(2, null));
    // TODO
  }

  private static byte[] fourBytes(int b0, int b1, int b2, int b3) {
    final byte[] result = new byte[8];
    writeMeta(result, 4);
    result[4] = (byte) b0;
    result[5] = (byte) b1;
    result[6] = (byte) b2;
    result[7] = (byte) b3;
    return result;
  }

  private static byte[] fourChars(Char c0, Char c1, Char c2, Char c3) {
    final int c0Len = c0.byteLen();
    final int c1Len = c1.byteLen();
    final int c2Len = c2.byteLen();
    final int c3Len = c3.byteLen();
    final byte[] result = new byte[4 + c0Len + c1Len + c2Len + c3Len];
    writeMeta(result, (1 << 31) | 4);
    c0.toBytes(result, 4);
    c1.toBytes(result, 4 + c0Len);
    c2.toBytes(result, 4 + c0Len + c1Len);
    c3.toBytes(result, 4 + c0Len + c1Len + c2Len);
    return result;
  }
}