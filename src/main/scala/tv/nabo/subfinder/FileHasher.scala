package tv.nabo.subfinder

import cats.effect.IO

import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.{Files, Path}
import java.nio.{ByteBuffer, ByteOrder, LongBuffer}
import scala.math._

object FileHasher {
  private val hashChunkSize = 64L * 1024L

  def apply(file: Path): IO[String] = IO.blocking {
    val fileSize = Files.size(file)
    val chunkSizeForFile = min(fileSize, hashChunkSize)

    val fileChannel = FileChannel.open(file)

    try {
      val head = computeHashForChunk(fileChannel.map(MapMode.READ_ONLY, 0, chunkSizeForFile))
      val tail = computeHashForChunk(fileChannel.map(MapMode.READ_ONLY, max(fileSize - hashChunkSize, 0), chunkSizeForFile))

      "%016x".format(fileSize + head + tail)
    } finally {
      fileChannel.close()
    }
  }

  private def computeHashForChunk(buffer: ByteBuffer): Long = {
    def doCompute(longBuffer: LongBuffer, hash: Long) : Long = {
      if (longBuffer.hasRemaining) {
        doCompute(longBuffer, hash + longBuffer.get)
      } else {
        hash
      }
    }
    val longBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
    doCompute(longBuffer, 0L)
  }
}