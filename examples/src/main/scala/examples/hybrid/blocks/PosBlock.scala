package examples.hybrid.blocks

import com.google.common.primitives.{Bytes, Ints, Longs}
import examples.commons._
import io.circe.Encoder
import io.circe.syntax._
import scorex.core.block.Block
import scorex.core.block.Block._
import scorex.util.serialization._
import scorex.core.serialization.ScorexSerializer
import scorex.core.transaction.proof.{Signature25519, Signature25519Serializer}
import scorex.core.transaction.state.PrivateKey25519
import scorex.core.utils.ScorexEncoding
import scorex.core.{ModifierTypeId, TransactionsCarryingPersistentNodeViewModifier, idToBytes}
import scorex.crypto.hash.Blake2b256
import scorex.crypto.signatures.{Curve25519, Signature}
import scorex.util.{ModifierId, ScorexLogging, bytesToId}
import scorex.util.Extensions._

case class PosBlock(override val parentId: BlockId, //PoW block
                    override val timestamp: Block.Timestamp,
                    override val transactions: Seq[SimpleBoxTransaction],
                    generatorBox: PublicKey25519NoncedBox,
                    attachment: Array[Byte],
                    signature: Signature25519
                   ) extends HybridBlock
  with TransactionsCarryingPersistentNodeViewModifier[SimpleBoxTransaction] with ScorexLogging {

  override type M = PosBlock

  override lazy val serializer = PosBlockSerializer

  override lazy val version: Version = 0: Byte

  override lazy val modifierTypeId: ModifierTypeId = PosBlock.ModifierTypeId

  override lazy val id: ModifierId =
    bytesToId(Blake2b256(idToBytes(parentId) ++ Longs.toByteArray(timestamp) ++ generatorBox.id ++ attachment))

  override def toString: String = s"PoSBlock(${this.asJson.noSpaces})"
}

object PosBlockSerializer extends ScorexSerializer[PosBlock] with ScorexEncoding {

  override def serialize(b: PosBlock, w: Writer): Unit = {
    w.putBytes(idToBytes(b.parentId))
    w.putULong(b.timestamp)
    PublicKey25519NoncedBoxSerializer.serialize(b.generatorBox, w)
    Signature25519Serializer.serialize(b.signature, w)
    w.putUInt(b.transactions.length)
    b.transactions.sortBy(t => encoder.encodeId(t.id)).foreach { tx =>
      SimpleBoxTransactionSerializer.serialize(tx, w)
    }
    w.putUInt(b.attachment.length)
    w.putBytes(b.attachment)
  }

  override def parse(r: Reader): PosBlock = {
    require(r.remaining <= PosBlock.MaxBlockSize)
    val parentId = bytesToId(r.getBytes(BlockIdLength))
    val timestamp = r.getULong()
    val box = PublicKey25519NoncedBoxSerializer.parse(r)
    val signature = Signature25519Serializer.parse(r)
    val txsLength = r.getUInt().toIntExact
    val txs: Seq[SimpleBoxTransaction] = (0 until txsLength) map { _ =>
      SimpleBoxTransactionSerializer.parse(r)
    }
    val attachmentLength = r.getUInt().toIntExact
    val attachment = r.getBytes(attachmentLength)
    PosBlock(parentId, timestamp, txs, box, attachment, signature)
  }
}

object PosBlock extends ScorexEncoding {
  val MaxBlockSize = 512 * 1024 //512K
  val ModifierTypeId: ModifierTypeId = scorex.core.ModifierTypeId @@ 4.toByte

  implicit val posBlockEncoder: Encoder[PosBlock] = (psb: PosBlock) => {
    Map(
      "id" -> encoder.encodeId(psb.id).asJson,
      "parentId" -> encoder.encodeId(psb.parentId).asJson,
      "attachment" -> encoder.encode(psb.attachment).asJson,
      "timestamp" -> psb.timestamp.asJson,
      "transactions" -> psb.transactions.map(_.asJson).asJson,
      "generatorBox" -> psb.generatorBox.asJson,
      "signature" -> encoder.encode(psb.signature.signature).asJson
    ).asJson
  }

  def create(parentId: BlockId,
             timestamp: Block.Timestamp,
             txs: Seq[SimpleBoxTransaction],
             box: PublicKey25519NoncedBox,
             attachment: Array[Byte],
             privateKey: PrivateKey25519): PosBlock = {
    require(java.util.Arrays.equals(box.proposition.pubKeyBytes, privateKey.publicKeyBytes))
    val unsigned = PosBlock(parentId, timestamp, txs, box, attachment, Signature25519(Signature @@ Array[Byte]()))
    val signature = Curve25519.sign(privateKey.privKeyBytes, PosBlockSerializer.toByteString(unsigned).toArray)
    unsigned.copy(signature = Signature25519(signature))
  }

  def signatureValid(posBlock: PosBlock): Boolean = {
    val unsigned = posBlock.copy(signature = Signature25519(Signature @@ Array[Byte]()))
    val unsignedBytes = PosBlockSerializer.toByteString(unsigned).toArray
    posBlock.generatorBox.proposition.verify(unsignedBytes, posBlock.signature.signature)
  }
}
