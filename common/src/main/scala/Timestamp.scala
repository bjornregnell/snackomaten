package snackomaten

import java.sql.Time

case class Timestamp(underlying: java.time.Instant) extends Ordered[Timestamp]:

  override def compare(that: Timestamp): Int = underlying.compareTo(that.underlying)

  def encode: String = underlying.toString

  def toDate: java.util.Date = java.util.Date.from(underlying)

object Timestamp:

  def now(): Timestamp = Timestamp(java.time.Instant.now())
  
  def decode(s: String): Option[Timestamp] = 
    try Some(Timestamp(java.time.Instant.parse(s))) catch case e: Throwable => None 

end Timestamp