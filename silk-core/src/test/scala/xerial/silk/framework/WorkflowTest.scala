//--------------------------------------
//
// WorkflowTest.scala
// Since: 2013/05/17 12:36 PM
//
//--------------------------------------

package xerial.silk.framework

import xerial.silk.util.SilkSpec
import xerial.core.log.Logger
import java.io.{ObjectOutputStream, ByteArrayOutputStream}
import xerial.silk.framework.ops.CallGraph

trait NestedLoop { this: Workflow =>

  def A = session.newSilk(Seq(1, 2, 3))
  def B = session.newSilk(Seq("x", "y"))

  def main = for(a <- A; b <- B) yield (a, b)

}

case class Person(id:Int, name:String, age:Int)

trait SamplePerson { this: Workflow =>
  def P = session.newSilk(Seq(Person(1, "Peter", 22), Person(2, "Yui", 10), Person(3, "Aina", 0)))
}

trait SeqOp extends SamplePerson { this:Workflow =>

  def main = {
    val B = P.filter(_.age <= 20)
    val C = B.map(_.name)
    C
  }
}

case class Address(id:Int, addr:String)


trait Twig extends SamplePerson  { this:Workflow =>

  def B = session.newSilk(Seq(Address(1, "xxx"), Address(1, "yyy"), Address(3, "zzz")))
  def join = P.naturalJoin(B)

}


trait SampleInput { this:Workflow =>

  def main = session.newSilk(Seq(1, 2, 3, 4))

}



trait NestedMixinExample { this:Workflow =>

  val indexer = mixin[SampleInput]

  def main = indexer.main.map(_*2)

}


/**
 * @author Taro L. Saito
 */
class WorkflowTest extends SilkSpec {


  "Workflow" should {

    "evaluate nested loops" taggedAs("nested") in {
      val w = new MyWorkflow with NestedLoop
      import w._
      val g = CallGraph.createCallGraph(w.main)
      debug(g)
      debug(s"eval: ${w.main.run}")
    }

    "sequential operation" taggedAs("seq") in {
      val w = new MyWorkflow with SeqOp
      import w._
      val g = CallGraph.createCallGraph(w.main)
      debug(g)
      debug(s"eval: ${w.main.run}")
    }

    "take joins" taggedAs("join") in {
      val w = new MyWorkflow with Twig
      import w._

      val g = CallGraph.createCallGraph(w.join)
      debug(g)
      debug(s"eval : ${w.join.run}")

    }

    "serialize SilkSession" taggedAs("ser") in {
      val s = new SilkSession


      val bo = new ByteArrayOutputStream
      val oos = new ObjectOutputStream(bo)
      oos.writeObject(s)
      oos.close

      val b = bo.toByteArray

      debug(s"serialized: ${b.length}")
    }

    "allow nested mixin workflows" taggedAs("mixin") in {
      val w = new MyWorkflow with NestedMixinExample
      import w._
      debug(s"eval: ${w.main.run}")
    }


  }
}