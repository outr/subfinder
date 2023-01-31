/*
package tv.nabo.subtitlefinder

import java.io.{File, FileNotFoundException}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.Charset
import java.util.zip.{ZipEntry, ZipFile}
import org.powerscala.io._
import tv.nabo.subfinder.FileHasher

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object SubtitleFinder {
  private val videoExtensions = Set("avi", "mkv")

  def main(args: Array[String]): Unit = {
    if (args.length == 1) {
      val file = new File(args.head)
      process(file)
    } else {
      println(s"Usage: subtitle_finder.jar PATH_TO_VIDEO")
    }
  }

  implicit class FileExtras(f: File) {
    lazy val extension: String = f.getName.substring(f.getName.lastIndexOf('.') + 1)
  }

  implicit class ZipFileExtras(f: ZipEntry) {
    lazy val extension: String = f.getName.substring(f.getName.lastIndexOf('.') + 1)
  }

  def process(video: File): Unit = {
    if (video.isDirectory) {
      video.listFiles().foreach { f =>
        if (videoExtensions.contains(f.extension)) {
          process(f)
        } else if (f.isDirectory) {
          process(f)
        }
      }
    } else {
      val videoName = video.getName.substring(0, video.getName.lastIndexOf('.'))
      val srtFile = new File(video.getParentFile, s"$videoName.srt")
      if (srtFile.exists()) {
        println(s"Skipping ${video.getName}, it already has subtitles")
      } else {
        println(s"Processing ${video.getName}...")
        val hash = FileHasher.computeHash(video)
        val rpc = new OpenSubtitlesRPC()
        val future = rpc.login().flatMap { token =>
          rpc.search(token, hash, video.length())
          //        rpc.quickSuggest(token, "the magnificent seven 2016")
        }
        val matches = Await.result(future, 30.seconds)
        if (matches.isEmpty) {
          sys.error("No subtitle matches found!")
        } else {
          val subZip = new File("subtitles.zip")

          try {
            IO.stream(new URL(matches.head), subZip)

            val zip = new ZipFile(subZip)
            val extensions = List("srt", "sub", "txt")
            try {
              val entries = zip.entries().asScala.toList
              extensions.flatMap { ext =>
                entries.find(_.extension == ext)
              }.headOption match {
                case Some(srtEntry) => {
                  val input = zip.getInputStream(srtEntry)
                  IO.stream(input, srtFile)
                }
                case None => println(s"No SRT file found in ZIP (${entries.mkString(", ")})!")
              }
            } finally {
              zip.close()
              if (!subZip.delete()) {
                subZip.deleteOnExit()
              }
            }
          } catch {
            case exc: FileNotFoundException => println(s"Unable to find subtitles for ${video.getName} (${exc.getMessage})")
          }
        }
      }
    }
  }
}
*/
