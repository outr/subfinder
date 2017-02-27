package tv.nabo.subtitlefinder

import java.nio.charset.Charset

import gigahorse.support.asynchttpclient.Gigahorse

import scala.collection.immutable.Seq
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.xml.XML
import scala.concurrent.ExecutionContext.Implicits.global

class OpenSubtitlesRPC(language: String = "eng", userAgent: String = "SubtitleFinder") {
  def login(username: String = "",
            password: String = ""): Future[String] = {
    val http = Gigahorse.http(Gigahorse.config)
    val content = <methodCall>
      <methodName>LogIn</methodName>
      <params>
        <param>
          <value><string>{username}</string></value>
        </param>
        <param>
          <value><string>{password}</string></value>
        </param>
        <param>
          <value><string>{language}</string></value>
        </param>
        <param>
          <value><string>{userAgent}</string></value>
        </param>
      </params>
    </methodCall>.toString()
    val request = Gigahorse.url("http://api.opensubtitles.org/xml-rpc")
      .post(content, Charset.forName("UTF-8"))
      .withContentType("text/xml")
      .withRequestTimeout(15.seconds)
      .withFollowRedirects(true)
    val future = http.processFull(request, Gigahorse.asString)
    future.onComplete(_ => http.close())
    future.map { xmlString =>
      try {
        XML.loadString(xmlString)
      } catch {
        case t: Throwable => throw new RuntimeException(s"Failed to parse XML: [$xmlString]")
      }
    }.map { xml =>
      ((xml \\ "member").head \ "value" \ "string").text
    }
  }

  def search(token: String, hash: String, sizeInBytes: Long): Future[Seq[String]] = {
    val http = Gigahorse.http(Gigahorse.config)
    val content = <methodCall>
      <methodName>SearchSubtitles</methodName>
      <params>
        <param>
          <value><string>{token}</string></value>
        </param>
        <param>
          <value>
            <array>
              <data>
                <value>
                  <struct>
                    <member>
                      <name>sublanguageid</name>
                      <value><string>{language}</string>
                      </value>
                    </member>
                    <member>
                      <name>moviehash</name>
                      <value><string>{hash}</string></value>
                    </member>
                    <member>
                      <name>moviebytesize</name>
                      <value><double>{sizeInBytes}</double></value>
                    </member>
                  </struct>
                </value>
              </data>
            </array>
          </value>
        </param>
      </params>
    </methodCall>.toString()
    val request = Gigahorse.url("http://api.opensubtitles.org/xml-rpc")
      .post(content, Charset.forName("UTF-8"))
      .withContentType("text/xml")
      .withRequestTimeout(15.seconds)
      .withFollowRedirects(true)
    val future = http.processFull(request, Gigahorse.asString)
    future.onComplete(_ => http.close())
    future.map(XML.loadString).map { xml =>
      val values = xml \ "params" \ "param" \ "value" \ "struct" \ "member" \ "value" \ "array" \ "data" \ "value"
      val zipURLs = values.flatMap { value =>
        val members = value \ "struct" \ "member"
        val languageId = members.collectFirst {
          case member if (member \ "name").text == "SubLanguageID" => (member \ "value" \ "string").text
        }
        if (languageId.contains(language)) {
          members.collectFirst {
            case member if (member \ "name").text == "ZipDownloadLink" => (member \ "value" \ "string").text
          }
        } else {
          println(s"Excluding language: $languageId is not $language")
          None
        }
      }
      zipURLs
    }
  }

  def quickSuggest(token: String, query: String): Future[Seq[String]] = {
    val http = Gigahorse.http(Gigahorse.config)
    val content = <methodCall>
      <methodName>QuickSuggest</methodName>
      <params>
        <param>
          <value><string>{token}</string></value>
        </param>
        <param>
          <value>
            <array>
              <data>
                <value>
                  <struct>
                    <member>
                      <name>sublanguageid</name>
                      <value><string>{language}</string>
                      </value>
                    </member>
                    <member>
                      <name>string</name>
                      <value><string>{query}</string></value>
                    </member>
                  </struct>
                </value>
              </data>
            </array>
          </value>
        </param>
      </params>
    </methodCall>.toString()
    val request = Gigahorse.url("http://api.opensubtitles.org/xml-rpc")
      .post(content, Charset.forName("UTF-8"))
      .withContentType("text/xml")
      .withRequestTimeout(15.seconds)
      .withFollowRedirects(true)
    val future = http.processFull(request, Gigahorse.asString)
    future.onComplete(_ => http.close())
    future.map(XML.loadString).map { xml =>
      println(s"RESPONS: $xml")
      val values = xml \ "params" \ "param" \ "value" \ "struct" \ "member" \ "value" \ "array" \ "data" \ "value"
      val zipURLs = values.flatMap { value =>
        val members = value \ "struct" \ "member"
        val languageId = members.collectFirst {
          case member if (member \ "name").text == "SubLanguageID" => (member \ "value" \ "string").text
        }
        if (languageId.contains(language)) {
          members.collectFirst {
            case member if (member \ "name").text == "ZipDownloadLink" => (member \ "value" \ "string").text
          }
        } else {
          None
        }
      }
      zipURLs
    }
  }
}