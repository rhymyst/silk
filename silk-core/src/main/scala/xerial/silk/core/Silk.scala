package xerial.silk.core
import reflect.ClassTag
import java.io.File
import scala.collection.GenTraversableOnce
import xerial.silk.SilkException

/**
 * A trait for all Silk data types
 * @tparam A
 */
trait Silk[+A] extends SilkOps[A] with Serializable {
 // def eval : Silk[A]
  def isSingle : Boolean
  def isRaw : Boolean = false
}


/**
 * Silk data class for single elements
 * @tparam A
 */
trait SilkSingle[+A] extends Silk[A] {
  ///  def map[B](f: A => B) : SilkSingle[B]
  import scala.language.experimental.macros

  def mapSingle[B](f: A => B) : SilkSingle[B] = macro SilkFlow.mMapSingle[A, B]
  def get(implicit ex:SilkExecutor): A

  override def isSingle = true
}


object Silk {
  def empty = Empty

  object Empty extends Silk[Nothing] {
    override def toString = "Empty"
    override def isEmpty = true
  }

}




/**
 * A trait that defines silk specific operations
 * @tparam A
 */
trait SilkOps[+A] { self: Silk[A] =>

  import scala.language.experimental.macros

  import SilkFlow._

  private def err = sys.error("N/A")

  def file : SilkSingle[File] = SaveToFile(self)


  // Mapper
  def foreach[U](f: A => U) : Silk[U] = macro mForeach[A, U]
  def map[B](f: A => B): Silk[B] = macro mMap[A, B]
  def flatMap[B](f: A => Silk[B]): Silk[B] = macro mFlatMap[A, B]

  // Filtering
  def filter(p: A => Boolean): Silk[A] = macro mFilter[A]
  def filterNot(p: A => Boolean): Silk[A] = macro mFilterNot[A]
  def withFilter(p: A => Boolean): Silk[A] = macro mWithFilter[A]
  def distinct : Silk[A] = Distinct(self)

  // Extraction
  def head : SilkSingle[A] = Head(self)
  def collect[B](pf: PartialFunction[A, B]): Silk[B] = err
  def collectFirst[B](pf: PartialFunction[A, B]): SilkSingle[Option[B]] = err



  // Aggregators
  def aggregate[B](z: B)(seqop: (B, A) => B, combop: (B, B) => B): SilkSingle[B] = err
  def reduce[A1 >: A](op: (A1, A1) => A1): SilkSingle[A1] = macro mReduce[A, A1]
  def reduceLeft[B >: A](op: (B, A) => B): SilkSingle[B] = macro mReduceLeft[A, B]
  def fold[A1 >: A](z: A1)(op: (A1, A1) => A1): SilkSingle[A1] = macro mFold[A, A1]
  def foldLeft[B](z: B)(op: (B, A) => B): SilkSingle[B] = macro mFoldLeft[A, B]


  /**
   * Scan the elements with an additional variable z (e.g., a counter) , then produce another Silk data set
   * @param z initial value
   * @param op function updating z and producing another element
   * @tparam B additional variable
   * @tparam C produced element
   */
  def scanLeftWith[B, C](z: B)(op : (B, A) => (B, C)): Silk[C] = ScanLeftWith(self, z, op)

  import SilkException._

  def size: Long = throw pending // Count(self).get
  def isSingle: Boolean = false
  def isEmpty: Boolean = size != 0

  // Numeric aggeragions
  def sum[A1>:A](implicit num: Numeric[A1]) : SilkSingle[A1] = NumericFold(self, num.zero, num.plus)
  def product[A1 >: A](implicit num: Numeric[A1]) : SilkSingle[A1]= NumericFold(self, num.one, num.times)
  def min[A1 >: A](implicit cmp: Ordering[A1]) : SilkSingle[A1]= NumericReduce(self, (x: A1, y: A1) => if (cmp.lteq(x, y)) x else y)
  def max[A1 >: A](implicit cmp: Ordering[A1]) : SilkSingle[A1]= NumericReduce(self, (x: A1, y: A1) => if (cmp.gteq(x, y)) x else y)
  def maxBy[A1 >: A, B](f: (A1) => B)(implicit cmp: Ordering[B]) : SilkSingle[A1] = NumericReduce(self, (x: A1, y: A1) => if (cmp.gteq(f(x), f(y))) x else y)
  def minBy[A1 >: A, B](f: (A1) => B)(implicit cmp: Ordering[B]) : SilkSingle[A1] = NumericReduce(self, (x: A1, y: A1) => if (cmp.lteq(f(x), f(y))) x else y)

  def mkString(start: String, sep: String, end: String): SilkSingle[String] = MkString(self, start, sep, end)
  def mkString(sep: String): SilkSingle[String] = mkString("", sep, "")
  def mkString: SilkSingle[String] = mkString("")

  // Grouping
  def groupBy[K](f: A => K): Silk[(K, Silk[A])] = macro mGroupBy[A, K]

  // Joins
  def join[K, B](other: Silk[B], k1: A => K, k2: B => K): Silk[(K, Silk[(A, B)])] = Join(self, other, k1, k2)
  def joinBy[B](other: Silk[B], cond: (A, B) => Boolean): Silk[(A, B)] = JoinBy(self, other, cond)

  // Sorting
  def sortBy[K](keyExtractor: A => K)(implicit ord: Ordering[K]): Silk[A] = SortBy(self, keyExtractor, ord)
  def sorted[A1 >: A](implicit ord: Ordering[A1]): Silk[A1] = Sort[A, A1](self, ord)

  // Sampling
  def takeSample(proportion: Double): Silk[A] = Sampling(self, proportion)

  // Zipper
  def zip[B](other: Silk[B]) : Silk[(A, B)] = Zip(self, other)
  def zipWithIndex : Silk[(A, Int)] = ZipWithIndex(self)

  // Data split and concatenation
  def split : Silk[Silk[A]] = Split(self)
  def concat[B](implicit asTraversable: A => Silk[B]) : Silk[B] = Concat(self, asTraversable)

  // Type conversion method
  def toArray[B >: A : ClassTag] : Array[B] = throw pending // ConvertToArray[A, B](self).get
  def toSeq[B >: A : ClassTag] : Seq[B] = throw pending // ConvertToSeq[A, B](self).get
  def save : SilkSingle[File] = SaveToFile(self)
}
