package tv.nabo.subtitlefinder

import java.io.File
import java.net.{HttpURLConnection, URL}
import java.nio.charset.Charset
import java.util.zip.ZipFile

import org.powerscala.io._

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

object SubtitleFinder {
  def main(args: Array[String]): Unit = {
    if (args.length == 1) {
      val video = new File(args.head)
      val hash = OpenSubtitlesHasher.computeHash(video)
      val rpc = new OpenSubtitlesRPC()
      val future = rpc.login().flatMap { token =>
        rpc.search(token, hash, video.length())
      }
      val matches = Await.result(future, 30.seconds)
      if (matches.isEmpty) {
        sys.error("No subtitle matches found!")
      } else {
        val subZip = new File("subtitles.zip")
        IO.stream(new URL(matches.head), subZip)

        val zip = new ZipFile(subZip)
        try {
          val srtEntry = zip.entries().asScala.find(_.getName.toLowerCase.endsWith(".srt")).getOrElse(throw new RuntimeException("No SRT file found in ZIP!"))
          val videoName = video.getName.substring(0, video.getName.lastIndexOf('.'))
          val srtFile = new File(video.getParentFile, s"$videoName.srt")
          val input = zip.getInputStream(srtEntry)
          IO.stream(input, srtFile)
        } finally {
          zip.close()
          if (!subZip.delete()) {
            subZip.deleteOnExit()
          }
        }
      }
    } else {
      println(s"Usage: subtitle_finder.jar PATH_TO_VIDEO")
    }
  }
}
