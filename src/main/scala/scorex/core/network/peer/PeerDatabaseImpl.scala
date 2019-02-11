package scorex.core.network.peer

import java.net.InetSocketAddress

import scorex.core.utils.TimeProvider
import scorex.util.ScorexLogging

import scala.collection.mutable

//todo: persistence
class PeerDatabaseImpl(filename: Option[String]) extends PeerDatabase with ScorexLogging {

  private val whitelistPersistence = mutable.Map[InetSocketAddress, PeerInfo]()

  private val blacklist = mutable.Map[String, TimeProvider.Time]()

  override def addOrUpdateKnownPeer(peerInfo: PeerInfo): Unit = {
    log.trace(s"Add or Update known peer: $peerInfo")

    peerInfo.address.foreach { address =>
      val updatedPeerInfo = whitelistPersistence.get(address).fold(peerInfo) { dbPeerInfo =>
        val nodeNameOpt = peerInfo.nodeName orElse dbPeerInfo.nodeName
        val connTypeOpt = peerInfo.connectionType orElse dbPeerInfo.connectionType
        val feats = if (dbPeerInfo.features.nonEmpty && peerInfo.features.isEmpty) {
          dbPeerInfo.features
        } else {
          peerInfo.features
        }
        PeerInfo(peerInfo.lastSeen, peerInfo.address, peerInfo.version, nodeNameOpt, connTypeOpt, feats)
      }
      whitelistPersistence.put(address, updatedPeerInfo)
    }
  }

  override def blacklistPeer(address: InetSocketAddress, time: TimeProvider.Time): Unit = {
    log.warn(s"Black list peer: ${address.toString}")
    whitelistPersistence.remove(address)
    if (!isBlacklisted(address)) blacklist += address.getHostName -> time
  }

  override def isBlacklisted(address: InetSocketAddress): Boolean = {
    blacklist.synchronized(blacklist.contains(address.getHostName))
  }

  override def knownPeers(): Map[InetSocketAddress, PeerInfo] = {
    log.trace(s"Known peers:  ${whitelistPersistence.toMap}")
    whitelistPersistence.keys.flatMap(k => whitelistPersistence.get(k).map(v => k -> v)).toMap
  }

  override def blacklistedPeers(): Seq[String] = blacklist.keys.toSeq

  override def isEmpty(): Boolean = whitelistPersistence.isEmpty

  override def remove(address: InetSocketAddress): Boolean = {
    log.trace(s"Remove peer: ${address.toString}")
    whitelistPersistence.remove(address).nonEmpty
  }
}
