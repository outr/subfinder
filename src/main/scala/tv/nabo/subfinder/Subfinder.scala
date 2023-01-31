package tv.nabo.subfinder

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fabric._
import fabric.rw.Asable
import profig.Profig
import spice.http.Headers
import spice.http.client.HttpClient
import spice.net._
import scribe.cats.{io => logger}
import spice.streamer._

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

object Subfinder extends IOApp {
  private val videoExtensions = Set("avi", "mkv")

  private lazy val apiKey: String = Profig("apiKey").as[String]
  private val subURL: URL = url"https://api.opensubtitles.com/api/v1/subtitles"

  private def fileIds(hash: String): IO[List[Long]] = HttpClient
    .url(subURL.withParam("moviehash", hash))
    .header("Api-Key", apiKey)
    .header(Headers.`Content-Type`(ContentType.`application/json`))
    .call[Json]
    .map { json =>
      json("data")
        .asVector
        .toList
        .flatMap { json =>
          json("attributes" \ "files").asVector.map(_("file_id").asLong)
        }
    }

  private def download(fileId: Long, directory: Path): IO[Unit] = for {
    json <- HttpClient
      .url(url"https://api.opensubtitles.com/api/v1/download")
      .header("Api-Key", apiKey)
      .header(Headers.`Content-Type`(ContentType.`application/json`))
      .restful[Json, Json](obj("file_id" -> fileId))
    link = json("link").as[URL]
    fileName = json("file_name").asString
    file = directory.resolve(fileName)
    content <- HttpClient.url(link).send().map(_.content.get.asString)
    _ <- Streamer(content, file)
  } yield {
    ()
  }

  private def loadFor(path: Path): IO[Unit] = if (Files.isDirectory(path)) {
    Files
      .list(path)
      .iterator()
      .asScala
      .toList
      .filter { path =>
        val fileName = path.getFileName.toString
        val index = fileName.lastIndexOf('.')
        val extension = if (index == -1) {
          fileName
        } else {
          fileName.substring(index + 1)
        }
        Files.isDirectory(path) || videoExtensions.contains(extension.toLowerCase)
      }
      .map(path => loadFor(path))
      .sequence
      .map(_ => ())
  } else {
    for {
      _ <- logger.info(s"Finding subtitles file for ${path.getFileName.toString}")
      hash <- FileHasher(path) //"24.S02E01.Day.2_.8_00.A.M..-.9_00.A.M..720p.WEB-DL.DD5.1.H.264.mkv"))
      fileIds <- fileIds(hash)
      _ <- fileIds.headOption match {
        case Some(fileId) => download(fileId, Paths.get("."))
        case None => logger.warn(s"Nothing found for $path")
      }
    } yield {
      ()
    }
  }

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(assert(args.length == 1, "Expected exactly one argument for the path to run in"))
    _ <- IO(Profig.initConfiguration())
    path <- IO(Paths.get(args.head))
    _ <- loadFor(path)
    _ <- HttpClient.dispose()
  } yield {
    ExitCode.Success
  }
}