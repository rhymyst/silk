//--------------------------------------
//
// Sort.scala
// Since: 2012/12/07 2:31 PM
//
//--------------------------------------

package xerial.silk.example

import util.Random
import xerial.silk._
import scala.collection.immutable.SortedMap


/**
 * Sorting example
 * @author Taro L. Saito
 */
object Sort {

  def blockIndex(v:Int, sIndex:SortedMap[Int, Int]) : Int = {
    // Find i where sample[i-1] <= v < sample[i]
    sIndex.from(v).headOption.map(_._2).getOrElse(sIndex.size)
  }

  def run = {
    // Create an random Int sequence
    val N = 100000000
    val input = (for(i <- 0 until N) yield {
      Random.nextInt
    }).toArray.toSilk



    // Sampling strategy described in
    // TeraByteSort on Apache Hadoop. Owen O'Malley (Yahoo!) May 2008
    val sample = input.takeSample(100000 / N)

    val splitIndex : SortedMap[Int, Int] = {
      val b = SortedMap.newBuilder[Int, Int]
      for((key, i) <- sample.sorted.zipWithIndex)
        b += key -> i
      b.result
    }

    // Split the data by block indexes, then sort each block
    val blocks = for((bin, lst) <- sample.groupBy(blockIndex(_, splitIndex))) yield
      (bin, lst.sorted)

    // Merge blocks
    val sorted = blocks.sortBy(_._1).map(_._2).concat
    sorted
  }

}