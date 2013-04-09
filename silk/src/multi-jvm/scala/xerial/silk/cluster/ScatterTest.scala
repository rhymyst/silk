

package xerial.silk.cluster

import xerial.silk.util.SilkSpec
import xerial.larray.LArray
import java.io.File
import xerial.core.io.IOUtil
import xerial.silk.cluster.ZooKeeper._
import scala.Some
import xerial.silk.cluster.SilkClient.ClientInfo
import xerial.silk.core.Silk


class ScatterTestMultiJvm1 extends SilkSpec {

  "scatter" should {

    "distribute data" in {
      info("hello")

      val l = LArray.of[Int](10)
      import xerial.larray._

      StandaloneCluster.withCluster {

        for(i <- 0 Until l.size) l(i) = i.toInt

        Thread.sleep(10000)

        val nodeList = Silk.hosts

        warn(s"node list: ${nodeList.mkString(", ")}")

        for(n <- nodeList; client <- SilkClient.remoteClient(n.host, n.port)) {
          warn(s"send a ping to $n")
          client ! SilkClient.Status
        }

        Thread.sleep(5000)

        // TODO
        //val s = l.toSilk.map(_*2)
        //s.execute()

        for(n <- nodeList; client <- SilkClient.remoteClient(n.host, n.port)) {
          client ! SilkClient.Terminate
        }
      }
    }
  }
}

class ScatterTestMultiJvm2 extends SilkSpec {

  "scatter" should {
    "distribute data" in {

      Thread.sleep(2000)

      val zk = new ZkEnsembleHost(StandaloneCluster.lh)
      val tmpDir : File = IOUtil.createTempDir(new File("target"), "silk-tmp2").getAbsoluteFile
      withConfig(Config(silkHome=tmpDir, silkClientPort = IOUtil.randomPort)) {

        SilkClient.startClient(Host("localhost2", "127.0.0.1"), zk.connectAddress)

        for(client <- SilkClient.localClient) {
          warn("in loop")
          Thread.sleep(3000)
        }
      }
    }
  }

}