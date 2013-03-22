/*
 * Copyright 2009-2011 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.json4s

import java.util.Locale.ENGLISH
import java.io.StringWriter

object JsonAST {

  /**
   * Concatenates a sequence of <code>JValue</code>s.
   * <p>
   * Example:<pre>
   * concat(JInt(1), JInt(2)) == JArray(List(JInt(1), JInt(2)))
   * </pre>
   */
  def concat(xs: JValue*) = xs.foldLeft(JNothing: JValue)(_ ++ _)

  object JValue extends Merge.Mergeable

  /**
   * Data type for JSON AST.
   */
  sealed abstract class JValue extends Diff.Diffable {
    type Values


    /**
     * Return unboxed values from JSON
     * <p>
     * Example:<pre>
     * JObject(JField("name", JString("joe")) :: Nil).values == Map("name" -> "joe")
     * </pre>
     */
    def values: Values

    /**
     * Return direct child elements.
     * <p>
     * Example:<pre>
     * JArray(JInt(1) :: JInt(2) :: Nil).children == List(JInt(1), JInt(2))
     * </pre>
     */
    def children: List[JValue] = this match {
      case JObject(l) ⇒ l map (_._2)
      case JArray(l) ⇒ l
      case _ ⇒ Nil
    }


    /**
     * Return nth element from JSON.
     * Meaningful only to JArray, JObject and JField. Returns JNothing for other types.
     * <p>
     * Example:<pre>
     * JArray(JInt(1) :: JInt(2) :: Nil)(1) == JInt(2)
     * </pre>
     */
    def apply(i: Int): JValue = JNothing


    /**
     * Concatenate with another JSON.
     * This is a concatenation monoid: (JValue, ++, JNothing)
     * <p>
     * Example:<pre>
     * JArray(JInt(1) :: JInt(2) :: Nil) ++ JArray(JInt(3) :: Nil) ==
     * JArray(List(JInt(1), JInt(2), JInt(3)))
     * </pre>
     */
    def ++(other: JValue) = {
      def append(value1: JValue, value2: JValue): JValue = (value1, value2) match {
        case (JNothing, x) ⇒ x
        case (x, JNothing) ⇒ x
        case (JArray(xs), JArray(ys)) ⇒ JArray(xs ::: ys)
        case (JArray(xs), v: JValue) ⇒ JArray(xs ::: List(v))
        case (v: JValue, JArray(xs)) ⇒ JArray(v :: xs)
        case (x, y) ⇒ JArray(x :: y :: Nil)
      }
      append(this, other)
    }

    /**
     * When this [[org.json4s.JValue]] is a [[org.json4s.JNothing]], this method returns [[scala.None]]
     * When it has a value it will return [[scala.Some]]
     */
    @deprecated("Use toOption instead", "3.1.0")
    def toOpt: Option[JValue] = toOption
    
    /**
     * When this [[org.json4s.JValue]] is a [[org.json4s.JNothing]], this method returns [[scala.None]]
     * When it has a value it will return [[scala.Some]]
     */
    def toOption: Option[JValue] = this match {
      case JNothing ⇒ None
      case json ⇒ Some(json)
    }
  }

  case object JNothing extends JValue {
    type Values = None.type
    def values = None
  }
  case object JNull extends JValue {
    type Values = Null
    def values = null
  }
  case class JString(s: String) extends JValue {
    type Values = String
    def values = s
  }
  trait JNumber extends JValue 
  case class JDouble(num: Double) extends JNumber {
    type Values = Double
    def values = num
  }
  case class JDecimal(num: BigDecimal) extends JNumber {
    type Values = BigDecimal
    def values = num
  }
  case class JInt(num: BigInt) extends JNumber {
    type Values = BigInt
    def values = num
  }
  case class JBool(value: Boolean) extends JValue {
    type Values = Boolean
    def values = value
  }

  case class JObject(obj: List[JField]) extends JValue {
    type Values = Map[String, Any]
    def values = obj.map { case (n, v) ⇒ (n, v.values) } toMap

    override def equals(that: Any): Boolean = that match {
      case o: JObject ⇒ Set(obj.toArray: _*) == Set(o.obj.toArray: _*)
      case _ ⇒ false
    }
  }
  case object JObject {
    def apply(fs: JField*): JObject = JObject(fs.toList)
  }

  case class JArray(arr: List[JValue]) extends JValue {
    type Values = List[Any]
    def values = arr.map(_.values)
    override def apply(i: Int): JValue = arr(i)
  }

  type JField = (String, JValue)
  object JField {
    def apply(name: String, value: JValue) = (name, value)
    def unapply(f: JField): Option[(String, JValue)] = Some(f)
  }

  private[this] trait StringAppender[T] {
    def append(s: String): T
    def subj: T
  }
  private[this] class StringWriterAppender(val subj: java.io.Writer) extends StringAppender[java.io.Writer] {
    def append(s: String): java.io.Writer = subj.append(s)
  }
  private[this] class StringBuilderAppender(val subj: StringBuilder) extends StringAppender[StringBuilder] {
    def append(s: String): StringBuilder = subj.append(s)
  }

  private[json4s] def quote(s: String): String = quote(s, new StringBuilderAppender(new StringBuilder)).toString
  private[json4s] def quote(s: String, writer: java.io.Writer): java.io.Writer = quote(s, new StringWriterAppender(writer))
  private[this] def quote[T](s: String, appender: StringAppender[T]): T = {
    s foreach { c =>
      if (c == '"') appender.append("\\\"")
      else if (c == '\\') appender.append("\\\\")
      else if (c == '\b') appender.append("\\b")
      else if (c == '\f') appender.append("\\f")
      else if (c == '\n') appender.append("\\n")
      else if (c == '\r') appender.append("\\r")
      else if (c == '\t') appender.append("\\t")
      else if ((c >= '\u0000' && c < '\u001f') || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100'))
        appender.append("\\u%04x".format(c: Int))
      else appender.append(c.toString)
    }
    appender.subj
  }
}
