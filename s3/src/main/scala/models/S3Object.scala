package aws.s3.models

import java.util.Date

import play.api.libs.ws._

import scala.concurrent.Future
import scala.xml.Elem

import aws.core._
import aws.core.Types._
import aws.core.parsers.Parser

import aws.s3.S3._
import aws.s3.S3.HTTPMethods._
import aws.s3.S3Parsers._

import scala.concurrent.ExecutionContext.Implicits.global

import aws.s3.S3.Parameters.Permisions.Grantees._

case class Owner(id: String, name: Option[String])

import S3Object.StorageClasses.StorageClass

trait Container {
  val id: Option[String]
  val key: String
  val isLatest: Boolean
  val lastModified: Date
  val etag: String
  val size: Long
  val storageClass: StorageClass
  val owner: Owner
}

case class DeleteMarker(
  id: Option[String],
  key: String,
  isLatest: Boolean,
  lastModified: Date,
  etag: String,
  size: Long,
  storageClass: StorageClass = S3Object.StorageClasses.STANDARD,
  owner: Owner) extends Container

case class Version(
  id: Option[String],
  key: String,
  isLatest: Boolean,
  lastModified: Date,
  etag: String,
  size: Long,
  storageClass: StorageClass = S3Object.StorageClasses.STANDARD,
  owner: Owner) extends Container

// TODO: Add content
case class Versions(
  name: String,
  prefix: Option[String],
  key: Option[String],
  maxKeys: Long,
  isTruncated: Boolean,
  versions: Seq[Version],
  deleteMarkers: Seq[DeleteMarker],
  versionId: Option[String])

case class S3Object(
  name: String,
  prefix: Option[String],
  marker: Option[String],
  maxKeys: Long,
  isTruncated: Boolean,
  contents: Seq[Content])

case class Content(
  id: Option[String],
  key: String,
  isLatest: Boolean,
  lastModified: Date,
  etag: String,
  size: Long,
  storageClass: StorageClass = S3Object.StorageClasses.STANDARD,
  owner: Owner) extends Container

case class BatchDeletion(successes: Seq[BatchDeletion.DeletionSuccess], failures: Seq[BatchDeletion.DeletionFailure])
object BatchDeletion {
  case class DeletionSuccess(
    key: String,
    versionId: Option[String],
    deleteMarker: Option[String],
    deleteMarkerVersionId: Option[String])

  case class DeletionFailure(key: String, code: String, message: String)
}

object S3Object {

  import java.io.File
  import play.api.libs.iteratee._
  import Http._

  object StorageClasses extends Enumeration {
    type StorageClass = Value
    val STANDARD = Value
  }

  def content(bucketname: String) =
    request[S3Object](GET, Some(bucketname))

  def getVersions(bucketname: String) =
    request[Versions](GET, Some(bucketname), subresource = Some("versions"))

  // http://aws.amazon.com/articles/1109?_encoding=UTF8&jiveRedirect=1
  // Transfer-Encoding: chunked is not supported. The PUT operation must include a Content-Length header.
  def put(bucketname: String, body: File) =
    upload[Unit](PUT, bucketname, body.getName, body)

  // TODO: versionId support
  def delete(bucketname: String, objectName: String, versionId: Option[String] = None) = {
    if(versionId.isDefined)
      ???
    else
      request[Unit](DELETE, Some(bucketname), Some(objectName))
  }


  //TODO: WSError: RequestTimeout - Your socket connection to the server was not read from or written to within the timeout period. Idle connections will be closed.
  def delete(bucketname: String, objects: (String, Option[String])*) = {
    val body =
      <Delete>
        <Quiet>false</Quiet>
        {
          for(o <- objects) yield
          <Object>
            <Key>{ o._1 }</Key>
            { for(v <- o._2.toSeq) yield <VersionId>{ v }</VersionId> }
          </Object>
        }
      </Delete>

    val ps = Seq(Parameters.MD5(body.mkString), Parameters.ContentLength(body.mkString.length))

    request[BatchDeletion](POST,
      Some(bucketname),
      body = Some(enumString(body.mkString)),
      subresource = Some("delete"),
      contentType = Some("text/plain; charset=utf-8"),
      parameters = ps)
  }

}