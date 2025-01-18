package tv.nabo.subfinder

import fabric._
import fabric.rw.Asable
import profig.Profig
import rapid._
import spice.http.Headers
import spice.http.client.HttpClient
import spice.net._
import scribe.{rapid => logger}
import spice.streamer._

import java.io.File
import scala.sys.process._
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

object Subfinder extends RapidApp {
  private val videoExtensions = Set("avi", "mkv", "mp4")

  private lazy val apiKey: String = Profig("apiKey").as[String]
  private val subURL: URL = url"https://api.opensubtitles.com/api/v1/subtitles"

  private lazy val forceGenerate: Boolean = Profig("forceGenerate").get().exists(_.asBoolean)

  private def fileIds(hash: String): Task[List[Long]] = fileIds(hash, includeAI = true, includeMachine = true)

  private def fileIds(hash: String,
                      includeAI: Boolean,
                      includeMachine: Boolean): Task[List[Long]] = HttpClient
    .url(subURL
      .withParam("moviehash", hash)
      .withParam("languages", "en")
      .withParam("ai_translated", if (includeAI) "include" else "exclude")
      .withParam("machine_translated", if (includeMachine) "include" else "exclude")
    )
    .header("Api-Key", apiKey)
    .removeHeader("User-Agent")
    .header(Headers.Request.`User-Agent`("Subfinder v2.0.0"))
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
    .handleError { throwable =>
      throw new RuntimeException(s"Failed to query file ids for hash: $hash", throwable)
    }

  private def download(videoFile: Path, fileId: Long, index: Int): Task[Unit] = for {
    json <- HttpClient
      .url(url"https://api.opensubtitles.com/api/v1/download")
      .header("Api-Key", apiKey)
      .failOnHttpStatus(false)
      .removeHeader("User-Agent")
      .header(Headers.Request.`User-Agent`("Subfinder v2.0.0"))
      .header(Headers.`Content-Type`(ContentType.`application/json`))
      .restful[Json, Json](obj("file_id" -> fileId))
      .handleError { throwable =>
        throw new RuntimeException(s"Failed to get download info for $fileId", throwable)
      }
    link = json("link").as[URL]
    videoFileName = videoFile.getFileName.toString
    extra = if (index == 0) "" else s".alt$index"
    fileName = videoFileName.substring(0, videoFileName.lastIndexOf('.')) + s".en$extra.srt"
    file = new File(videoFile.toFile.getParent, fileName).toPath
    content <- HttpClient.url(link).send().flatMap(_.content.get.asString).handleError { throwable =>
      throw new RuntimeException(s"Failed to download subtitle at $link")
    }
    _ <- Streamer(content, file)
  } yield {
    ()
  }

  private def loadFor(path: Path): Task[Unit] = if (Files.isDirectory(path)) {
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
      .map { path =>
        val fileName = path.getFileName.toString
        val srt = path.getParent.resolve(s"${fileName.substring(0, fileName.lastIndexOf('.'))}.srt")
        if (Files.exists(srt)) {
          logger.debug("Skipping, SRT already exists!")
        } else {
          loadFor(path)
        }
      }
      .tasks
      .map(_ => ())
  } else {
    for {
      _ <- logger.info(s"Finding subtitles file for ${path.getFileName.toString}")
      hash <- FileHasher(path)
      ids <- if (forceGenerate) {
        Task.pure(Nil)
      } else {
        fileIds(hash)
      }
      _ <- ids.zipWithIndex.map {
        case (fileId, index) => download(path, fileId, index)
      }.tasks.when(!forceGenerate)
      _ <- logger
        .warn(s"Nothing found for $path ($hash). Generating Subtitles...")
        .map { _ =>
          s"auto_subtitle --srt_only true \"${path.toFile.getCanonicalPath}\" -o \"${path.toFile.getParentFile.getCanonicalPath}\"".!
        }
        .when(ids.isEmpty)
    } yield {
      ()
    }
  }

  override def run(args: List[String]): Task[Unit] = for {
    _ <- Task(Profig.initConfiguration())
    path <- Task(Paths.get(args.headOption.getOrElse(".")))
    _ <- loadFor(path)
    _ <- HttpClient.dispose()
  } yield ()
}