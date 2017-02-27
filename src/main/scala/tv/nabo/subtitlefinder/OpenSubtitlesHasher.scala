package tv.nabo.subtitlefinder

import java.io.{FileInputStream, File}
import java.nio.{LongBuffer, ByteOrder, ByteBuffer}
import java.nio.channels.FileChannel.MapMode
import scala.math._

object OpenSubtitlesHasher {
  private val hashChunkSize = 64L * 1024L

  def computeHash(file: File) : String = {
    val fileSize = file.length
    val chunkSizeForFile = min(fileSize, hashChunkSize)

    val fileChannel = new FileInputStream(file).getChannel

    try {
      val head = computeHashForChunk(fileChannel.map(MapMode.READ_ONLY, 0, chunkSizeForFile))
      val tail = computeHashForChunk(fileChannel.map(MapMode.READ_ONLY, max(fileSize - hashChunkSize, 0), chunkSizeForFile))

      "%016x".format(fileSize + head + tail)
    } finally {
      fileChannel.close()
    }
  }

  private def computeHashForChunk(buffer: ByteBuffer) : Long = {
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