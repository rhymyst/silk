package xerial.silk.framework

import xerial.silk.mini._
import xerial.silk.SilkException
import xerial.silk.framework.ops._
import xerial.silk.framework.ops.ReduceOp
import xerial.silk.framework.ops.FilterOp
import xerial.silk.framework.ops.FlatMapOp
import xerial.silk.framework.ops.MapOp

/**
 * Executor of Silk programs
 */
trait ExecutorComponent {
  self : SessionComponent
    with SliceComponent
    with StageManagerComponent
    with SliceStorageComponent =>

  type Executor <: ExecutorAPI
  def executor : Executor

  def newSlice[A](op:Silk[_], index:Int, data:Seq[A]) : Slice[A]

  def run[A](session:Session, silk: Silk[A]): Result[A] = {
    val result = executor.getSlices(silk).flatMap(_.data)
    result
  }


  trait ExecutorAPI {
    def defaultParallelism : Int = 2


    def evalRecursively[A](op:SilkOps[A], v:Any) : Seq[Slice[A]] = {
      v match {
        case silk:Silk[_] => getSlices(silk).asInstanceOf[Seq[Slice[A]]]
        case seq:Seq[_] => Seq(newSlice(op, 0, seq.asInstanceOf[Seq[A]]))
        case e => Seq(newSlice(op, 0, Seq(e).asInstanceOf[Seq[A]]))
      }
    }

    private def flattenSlices[A](op:SilkOps[_], in: Seq[Seq[Slice[A]]]): Seq[Slice[A]] = {
      var counter = 0
      val result = for (ss <- in; s <- ss) yield {
        val r = newSlice(op, counter, s.data)
        counter += 1
        r
      }
      result
    }

    def processSlice[A,U](slice:Slice[A])(f:Slice[A]=>Slice[U]) : Slice[U] = {
      f(slice)
    }

    def getSlices[A](op: Silk[A]) : Seq[Slice[A]] = {
      import helper._
      try {
        stageManager.startStage(op)
        val result : Seq[Slice[A]] = op match {
          case m @ MapOp(fref, in, f, fe) =>
            val slices = for(slc <- getSlices(in)) yield {
              newSlice(op, slc.index, slc.data.map(m.fwrap).asInstanceOf[Seq[A]])
            }
            slices
          case m @ FlatMapOp(fref, in, f, fe) =>
            val nestedSlices = for(slc <- getSlices(in)) yield {
              slc.data.flatMap(e => evalRecursively(op, m.fwrap(e)))
            }
            flattenSlices(op, nestedSlices)
          case FilterOp(fref, in, f, fe) =>
            val slices = for(slc <- getSlices(in)) yield
              newSlice(op, slc.index, slc.data.filter(filterWrap(f)))
            slices
          case ReduceOp(fref, in, f, fe) =>
            val rf = rwrap(f)
            val reduced : Seq[Any] = for(slc <- getSlices(in)) yield
              slc.data.reduce(rf)
            val resultSlice = newSlice(op, 0, Seq(reduced.reduce(rf))).asInstanceOf[Slice[A]]
            Seq(resultSlice)
          case RawSeq(fc, in) =>
            val w = (in.length + (defaultParallelism - 1)) / defaultParallelism
            val split = for((split, i) <- in.sliding(w, w).zipWithIndex) yield
              newSlice(op, i, split)
            split.toIndexedSeq
          case other =>
            warn(s"unknown op: $other")
            Seq.empty
        }
        stageManager.finishStage(op)
        result
      }
      catch {
        case e:Exception =>
          stageManager.abortStage(op)
          throw SilkException.pending
      }
    }
  }
}







//
//class Worker(val host: Host) extends Logger {
//
//  import Worker._
//
//  def resource = WorkerResource(host, 2, 1 * 1024 * 1024) // 2CPUs, 1G memory
//
//  private def flattenSlices(in: Seq[Seq[Slice[_]]]): Seq[Slice[_]] = {
//    var counter = 0
//    val result = for (ss <- in; s <- ss) yield {
//      val r = RawSlice(s.host, counter, s.data)
//      counter += 1
//      r
//    }
//    result
//  }
//
//  private def evalAtRemote[U](ss: SilkSession, op: SilkOps[_], slice: Slice[_])(f: (SilkSession, Slice[_]) => U): U = {
//    val b = SilkOps.serializeFunc(f)
//
//    debug(s"Eval slice (opID:${op.idPrefix}, host:${slice.host}) at ${host} function ${f.getClass.getSimpleName} size:${b.length}")
//    val fd = SilkOps.deserializeFunc(b).asInstanceOf[(SilkSession, Slice[_]) => U]
//    fd(ss, slice)
//  }
//
//
//  private def evalSlice(ss: SilkSession, in: SilkOps[_]): Seq[Slice[_]] = {
//    // Evaluate slice
//    // TODO: parallel evaluation
//    in.slice(ss)
//  }
//
//
//  def execute(ss: SilkSession, task: EvalTask) = {
//    task match {
//      case e @ EvalOpTask(b) => executeSilkOp(ss, e)
//      case e @ EvalSliceTask(slice, b) => executeSliceOp(ss, e)
//    }
//  }
//
//  def executeSliceOp(ss:SilkSession, task:EvalSliceTask) = {
//    val fd = SilkOps.deserializeFunc(task.opBinary).asInstanceOf[(SilkSession, Slice[_]) => Any]
//    fd(ss, task.slice)
//  }
//
//  def executeSilkOp(ss:SilkSession, task:EvalOpTask) = {
//
//    // Deserialize the operation
//    val op = SilkOps.deserializeOp(task.opBinary)
//    trace(s"execute: ${op}, byte size: ${DataUnit.toHumanReadableFormat(task.opBinary.length)}")
//
//
//    // TODO send the job to a remote machine
//    val result: Seq[Slice[_]] = op match {
//      case m@MapOp(fref, in, f, expr) =>
//        // in: S1, S2, ...., Sk
//        // output: S1.map(f), S2.map(f), ..., Sk.map(f)
//        // TODO: Open a stage
//        val r = for (slice <- evalSlice(ss, in)) yield {
//          // TODO: Choose an appropriate host
//          evalAtRemote(ss, op, slice) { execFlatMap(_, _, f) }
//        }
//        // TODO: Await until all of the sub stages terminates
//        flattenSlices(r)
//      case fm@FlatMapOp(fref, in, f, expr) =>
//        val r = for (slice <- evalSlice(ss, in)) yield
//          evalAtRemote(ss, op, slice) { execFlatMap(_, _, f) }
//        flattenSlices(r)
//      case fl@FilterOp(fref, in, f, expr) =>
//        val r = for (slice <- evalSlice(ss, in)) yield
//          evalAtRemote(ss, op, slice) { execFilter(_, _, f) }
//        flattenSlices(r)
//      case rs@RawSeq(fref, in) =>
//        // TODO distribute the data set
//        val r = rs.slice(ss)
//        debug(s"rawseq $r")
//        r
//      case jo@JoinOp(fref, left, right) =>
//        //val ls = evalSlice(left)
//        //val rs = evalSlice(right)
//        val keyParams = jo.keyParameterPairs
//        debug(s"key params: ${jo.keyParameterPairs.mkString(", ")}")
//        // TODO: Shuffle operation
//        if (keyParams.length == 1) {
//          val ka = keyParams.head._1
//          val kb = keyParams.head._2
//          val partitioner = {
//            k: Int => k % 2
//          } // Simple partitioner
//          val ls = ShuffleOp(left.fref, left, ka, partitioner)
//          val rs = ShuffleOp(right.fref, right, kb, partitioner)
//          val merger = MergeShuffleOp(fref, ls, rs)
//          ss.eval(merger)
//        }
//        else {
//          warn("multiple primary keys are not supported yet")
//          Seq.empty
//        }
//      case so@ShuffleOp(fref, in, keyParam, partitioner) =>
//        val shuffleSet = for (slice <- evalSlice(ss, in)) yield {
//          evalAtRemote(ss, so, slice) {
//            (sc, sl) =>
//              val shuffled = for (e <- sl.data) yield {
//                val key = keyParam.get(e)
//                val partition = fwrap(partitioner)(key).asInstanceOf[Int]
//                (key, partition) -> e
//              }
//              val slices = for (((key, partition), lst) <- shuffled.groupBy(_._1)) yield
//                PartitionedSlice(sl.host, partition, lst.map {
//                  x => (x._1._1, x._2)
//                })
//              slices.toSeq
//          }
//        }
//        // Merge partitions generated from a slice
//        val hostList = Worker.hosts
//        val partitions = for ((pid, slices) <- shuffleSet.flatten.groupBy(_.index)) yield {
//          val hi = slices.head.index % hostList.length
//          val h = hostList(hi)
//          PartitionedSlice(h, hi, slices.flatMap(_.data))
//        }
//        debug(s"partitions:${partitions}")
//        partitions.toSeq
//      case mo@MergeShuffleOp(fref, left, right) =>
//        val l = evalSlice(ss, left).sortBy(_.index)
//        val r = evalSlice(ss, right).sortBy(_.index)
//        val joined = for (le <- l; re <- r.filter(_.index == le.index)) yield {
//          // TODO hash-join
//          for ((lkey, ll) <- le.data; (rkey, rr) <- re.data if lkey == rkey) yield {
//            (ll, rr)
//          }
//        }
//        debug(s"joined $joined")
//        Seq(RawSlice(Host("localhost"), 0, joined.flatten))
//      case ReduceOp(fref, in, f, expr) =>
//        val r = for (slice <- evalSlice(ss, in)) yield {
//          // Reduce at each host
//          val red = evalAtRemote(ss, op, slice) {
//            (sc, slc) => slc.data.reduce(rwrap(f))
//          }
//          // TODO: Create new reducers
//          //evalRecursively(in).reduce{evalSingleRecursively(f.asInstanceOf[(Any,Any)=>Any](_, _))}
//        }
//        Seq(RawSlice(Host("localhost"), 0, Seq(r.reduce(rwrap(f)))))
//      case _ =>
//        warn(s"unknown op: ${op}")
//        Seq.empty
//    }
//
//    ss.putIfAbsent(op.uuid, result)
//  }
//
//  //  def scatter[A: ClassTag](rs: RawSeq[A]): DistributedSeq[A] = {
//  //    val numSlices = 2 // TODO retrieve default number of slices
//  //    val sliceSize = (rs.in.size + (numSlices - 1)) / numSlices
//  //    val slices = for ((slice, i) <- rs.in.sliding(sliceSize, sliceSize).zipWithIndex) yield {
//  //      val h = hostList(i % hostList.size) // round-robin split
//  //      // TODO: Send data to remote hosts
//  //      RawSlice(h, i, slice)
//  //    }
//  //    DistributedSeq[A](rs.fref, ss.newID, slices.toIndexedSeq)
//  //  }
//
//

