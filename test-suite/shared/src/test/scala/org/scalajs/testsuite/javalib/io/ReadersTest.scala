/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.scalajs.testsuite.javalib.io

import scala.annotation.tailrec

import java.io._

import org.junit.Test
import org.junit.Assert._

import org.scalajs.testsuite.utils.AssertThrows._

/** Tests for our implementation of java.io._ reader classes */
class ReaderTest {
  object MyReader extends java.io.Reader {
    def read(dbuf: Array[Char], off: Int, len: Int): Int = {
      java.util.Arrays.fill(dbuf, off, off + len, 'A')
      len
    }
    def close(): Unit = ()
  }

  @Test def skipIntIfPossible(): Unit = {
    assertEquals(42, MyReader.skip(42))
    assertEquals(10000, MyReader.skip(10000)) // more than the 8192 batch size
  }
}

class StringReaderTest {
  val str = "asdf"
  def newReader: StringReader = new StringReader(str)

  @Test def read()(): Unit = {
    val r = newReader

    for (c <- str) {
      assertEquals(c, r.read().toChar)
    }

    assertEquals(-1, r.read())
  }

  @Test def readArrayCharIntInt(): Unit = {
    val r = newReader
    val buf = new Array[Char](10)

    assertEquals(4, r.read(buf, 2, 8))
    assertArrayEquals(buf.map(_.toInt), Array[Int](0,0,'a','s','d','f',0,0,0,0))
    assertEquals(-1, r.read(buf, 2, 8)) // #1560
  }

  @Test def readCharBuffer(): Unit = {
    val r = newReader
    val buf0 = java.nio.CharBuffer.allocate(25)
    buf0.position(3)
    val buf = buf0.slice()
    buf.position(4)
    buf.limit(14)

    assertEquals(4, r.read(buf))
    assertEquals(8, buf.position())
    buf.flip()
    assertArrayEquals(buf.toString().map(_.toInt).toArray,
        Array[Int](0, 0, 0, 0, 'a', 's', 'd', 'f'))
  }

  @Test def ready(): Unit = {
    val r = newReader

    for (c <- str) {
      assertTrue(r.ready())
      assertEquals(c, r.read().toChar)
    }

    assertTrue(r.ready())
    assertEquals(-1, r.read())

    r.close()
    expectThrows(classOf[IOException], r.ready())
  }

  @Test def markReset(): Unit = {
    val r = newReader
    r.mark(str.length)

    for (c <- str) {
      assertEquals(c, r.read().toChar)
    }
    assertEquals(-1, r.read())

    r.reset()

    for (c <- str) {
      assertEquals(c, r.read().toChar)
    }
    assertEquals(-1, r.read())
  }

  @Test def skip(): Unit = {
    val r = newReader

    assertEquals('a': Int, r.read())
    assertEquals(2, r.skip(2L).toInt)

    assertEquals('f': Int, r.read())
    assertEquals(-1, r.read())
  }

  @Test def close(): Unit = {
    val r = newReader

    r.close()
    expectThrows(classOf[IOException], r.read())
  }

  @Test def mark(): Unit = {
    assertTrue(newReader.markSupported)
  }

  @Test def markThrowsWithNegativeLookahead(): Unit = {
    expectThrows(classOf[IllegalArgumentException], newReader.mark(-10))
  }

  @Test def skipAcceptsNegativeLookaheadAsLookback(): Unit = {
    // StringReader.skip accepts negative lookahead
    val r = newReader
    assertEquals("already head", 0, r.skip(-1))
    assertEquals('a', r.read())

    assertEquals(1, r.skip(1))
    assertEquals('d', r.read())

    assertEquals(-2, r.skip(-2))
    assertEquals('s', r.read())
  }

  @Test def skipReturns0AfterReachingEnd(): Unit = {
    val r = newReader
    assertEquals(4, r.skip(100))
    assertEquals(-1, r.read())

    assertEquals(0, r.skip(-100))
    assertEquals(-1, r.read())
  }

}

class BufferedReaderTest {

  val str = "line1\nline2\r\n\nline4\rline5"
  def newReader: BufferedReader = new BufferedReader(new StringReader(str), 3)

  @Test def close(): Unit = {
    class UnderlyingReader extends StringReader(str) {
      var closeCount: Int = 0

      override def close(): Unit = {
        closeCount += 1
        /* Do not call super.close(), to ensure IOExceptions come from
         * BufferedReader, and not the underlying reader.
         */
      }
    }

    val underlying = new UnderlyingReader
    val r = new BufferedReader(underlying)
    r.read()
    assertEquals(0, underlying.closeCount)
    r.close()
    assertEquals(1, underlying.closeCount)

    // close() actually prevents further use of the reader
    assertThrows(classOf[IOException], r.mark(1))
    assertThrows(classOf[IOException], r.read())
    assertThrows(classOf[IOException], r.read(new Array[Char](1), 0, 1))
    assertThrows(classOf[IOException], r.read(new Array[Char](1)))
    assertThrows(classOf[IOException], r.readLine())
    assertThrows(classOf[IOException], r.ready())
    assertThrows(classOf[IOException], r.reset())
    assertThrows(classOf[IOException], r.skip(1L))
    assertThrows(classOf[IllegalArgumentException], r.skip(-1L))

    // close() is idempotent
    r.close()
    assertEquals(1, underlying.closeCount)
  }

  @Test def read(): Unit = {
    val r = newReader

    for (c <- str) {
      assertEquals(c, r.read().toChar)
    }
    assertEquals(-1, r.read())
  }

  @Test def readArrayChar(): Unit = {
    var read = 0
    val r = newReader
    val buf = new Array[Char](15)

    // twice to force filling internal buffer
    for (_ <- 0 to 1) {
      val len = r.read(buf)
      assertTrue(len > 0)

      for (i <- 0 until len)
        assertEquals(str.charAt(i+read), buf(i))

      read += len
    }
  }

  @Test def readArrayCharIntInt(): Unit = {
    var read = 0
    val r = newReader
    val buf = new Array[Char](15)

    // twice to force filling internal buffer
    for (_ <- 0 to 1) {
      val len = r.read(buf, 1, 10)
      assertTrue(len > 0)
      assertTrue(len < 11)

      for (i <- 0 until len)
        assertEquals(str.charAt(i+read), buf(i+1))

      read += len
    }
  }

  @Test def markAndReset(): Unit = {
    val r = newReader
    assertEquals('l': Int, r.read())

    // force moving and resizing buffer
    r.mark(10)

    for (i <- 0 until 10) {
      assertEquals(str.charAt(i+1): Int, r.read())
    }

    r.reset()

    for (i <- 1 until str.length) {
      assertEquals(str.charAt(i): Int, r.read())
    }
  }

  @Test def readLine(): Unit = {
    val r = newReader

    assertEquals("line1", r.readLine())
    assertEquals("line2", r.readLine())
    assertEquals("", r.readLine())
    assertEquals("line4", r.readLine())
    assertEquals("line5", r.readLine())
    assertEquals(null, r.readLine())
  }

  @Test def readLineEmptyStream(): Unit = {
    val r = new BufferedReader(new StringReader(""))

    assertEquals(null, r.readLine())
  }

  @Test def readLineEmptyLinesOnly(): Unit = {
    val r = new BufferedReader(new StringReader("\n\r\n\r\r\n"), 1)

    for (_ <- 1 to 4)
      assertEquals("", r.readLine())

    assertEquals(null, r.readLine())
  }

  @Test def skipReturns0AfterReachingEnd(): Unit = {
    val r = newReader
    assertEquals(25, r.skip(100))
    assertEquals(-1, r.read())

    assertEquals(0, r.skip(100))
    assertEquals(-1, r.read())
  }

  @Test def markSupported(): Unit = {
    assertTrue(newReader.markSupported)
  }

  @Test def markThrowsWithNegativeLookahead(): Unit = {
    expectThrows(classOf[IllegalArgumentException], newReader.mark(-10))
  }
}

class InputStreamReaderTest {

  @Test def readUTF8(): Unit = {

    val buf = Array[Byte](72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100,
        46, -29, -127, -109, -29, -126, -109, -29, -127, -85, -29, -127, -95,
        -29, -127, -81, -26, -105, -91, -26, -100, -84, -24, -86, -98, -29,
        -126, -110, -24, -86, -83, -29, -126, -127, -29, -127, -66, -29, -127,
        -103, -29, -127, -117, -29, -128, -126)

    val r = new InputStreamReader(new ByteArrayInputStream(buf))

    def expectRead(str: String): Unit = {
      val buf = new Array[Char](str.length)
      @tailrec
      def readAll(readSoFar: Int): Int = {
        if (readSoFar == buf.length) readSoFar
        else {
          val newlyRead = r.read(buf, readSoFar, buf.length - readSoFar)
          if (newlyRead == -1) readSoFar
          else readAll(readSoFar + newlyRead)
        }
      }
      assertEquals(str.length, readAll(0))
      assertEquals(str, new String(buf))
    }

    expectRead("Hello World.")
    expectRead("???????????????")
    expectRead("??????????????????????????????")
    assertEquals(-1, r.read())
  }

  @Test def readEOFThrows(): Unit = {
    val data = "Lorem ipsum".getBytes()
    val streamReader = new InputStreamReader(new ByteArrayInputStream(data))
    val bytes = new Array[Char](11)

    assertEquals(11, streamReader.read(bytes))
    // Do it twice to check for a regression where this used to throw
    assertEquals(-1, streamReader.read(bytes))
    assertEquals(-1, streamReader.read(bytes))
    expectThrows(classOf[IndexOutOfBoundsException], streamReader.read(bytes, 10, 3))
    assertEquals(0, streamReader.read(new Array[Char](0)))
  }

  @Test def skipReturns0AfterReachingEnd(): Unit = {
    val data = "Lorem ipsum".getBytes()
    val r = new InputStreamReader(new ByteArrayInputStream(data))
    assertTrue(r.skip(100) > 0)
    assertEquals(-1, r.read())

    assertEquals(0, r.skip(100))
    assertEquals(-1, r.read())
  }

  @Test def markThrowsNotSupported(): Unit = {
    val data = "Lorem ipsum".getBytes()
    val r = new InputStreamReader(new ByteArrayInputStream(data))
    expectThrows(classOf[IOException], r.mark(0))
  }
}
