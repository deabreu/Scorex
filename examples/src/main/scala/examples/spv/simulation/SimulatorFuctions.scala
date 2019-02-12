package examples.spv.simulation

import com.google.common.primitives.{Ints, Longs}
import examples.commons.{Nonce, PublicKey25519NoncedBox, Value}
import examples.spv.{Header, SpvAlgos}
import examples.trimchain.core.Constants._
import examples.trimchain.simulation.InMemoryAuthenticatedUtxo
import scorex.core.block.Block._
import scorex.core.transaction.box.proposition.PublicKey25519Proposition
import scorex.core.transaction.state.{BoxStateChanges, Insertion}
import scorex.util.ModifierId
import scorex.util.{bytesToId, idToBytes}
import scorex.core.bytesToVersion

import scala.annotation.tailrec
import scala.util.Random

trait SimulatorFuctions {
  val defaultBytes = Array.fill(32)(0: Byte)
  val defaultId = bytesToId(defaultBytes)
  val defaultVersion = bytesToVersion(defaultBytes)

  def genGenesisState(minerPubKey: PublicKey25519Proposition): InMemoryAuthenticatedUtxo = {
    val genesisBoxes = (1 to 5000) map { i =>
      PublicKey25519NoncedBox(
        minerPubKey,
        Nonce @@ Longs.fromByteArray(hashfn(minerPubKey.pubKeyBytes ++ Ints.toByteArray(i)).take(8)),
        Value @@ 10000000000L
      )
    }
    val genesisChanges: BoxStateChanges[PublicKey25519Proposition, PublicKey25519NoncedBox] =
      BoxStateChanges(genesisBoxes.map(box => Insertion[PublicKey25519Proposition, PublicKey25519NoncedBox](box)))
    InMemoryAuthenticatedUtxo(genesisBoxes.size, None, defaultVersion).applyChanges(genesisChanges, defaultVersion).get
  }

  @tailrec
  final def genChain(height: Int, difficulty: BigInt, stateRoot: Array[Byte], acc: IndexedSeq[Header]): Seq[Header] = if (height == 0) {
    acc.reverse
  } else {
    val block = genBlock(difficulty, acc, stateRoot, defaultBytes, System.currentTimeMillis())
    genChain(height - 1, difficulty, stateRoot, block +: acc)
  }


  def genBlock(difficulty: BigInt,
               parents: IndexedSeq[Header],
               stateRoot: Array[Byte],
               transactionsRoot: Array[Byte],
               timestamp: Timestamp): Header = {
    //TODO: fixme, What should happen if `parents` is empty?
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    val parent = parents.head
    val interlinks: Seq[ModifierId] = if (parents.length > 1) SpvAlgos.constructInterlinkVector(parent)
    else Seq(parent.id)

    @tailrec
    def generateHeader(): Header = {
      val nonce = Random.nextInt
      val header = Header(parent.id, interlinks, stateRoot, transactionsRoot, timestamp, nonce)
      if (correctWorkDone(header.id, difficulty)) header
      else generateHeader()
    }
    generateHeader()
  }

  def correctWorkDone(id: ModifierId, difficulty: BigInt): Boolean = {
    val target = examples.spv.Constants.MaxTarget / difficulty
    BigInt(1, idToBytes(id)) < target
  }

  def genGenesisHeader(stateRoot: Array[Byte], minerPubKey: PublicKey25519Proposition): Header = {
    val genesisBoxes = (1 to 5000) map { i =>
      PublicKey25519NoncedBox(
        minerPubKey,
        Nonce @@ Longs.fromByteArray(hashfn(minerPubKey.pubKeyBytes ++ Ints.toByteArray(i)).take(8)),
        Value @@ 10000000000L
      )
    }

    Header(bytesToId(Array.fill(32)(0: Byte)), Seq(), stateRoot, defaultBytes, 0L, 0)
  }

}
