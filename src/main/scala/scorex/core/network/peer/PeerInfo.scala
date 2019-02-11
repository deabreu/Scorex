package scorex.core.network.peer

import java.net.InetSocketAddress

import scorex.core.app.Version
import scorex.core.network.{ConnectionType, PeerFeature}

//todo does this make sense to have address == None?
case class PeerInfo(lastSeen: Long,
                    address: Option[InetSocketAddress],
                    version: Version,
                    nodeName: Option[String] = None,
                    connectionType: Option[ConnectionType] = None,
                    features: Seq[PeerFeature] = Seq()) {

  lazy val reachablePeer: Boolean = {
    address.isDefined || localAddressOpt.isDefined
  }

  lazy val localAddressOpt: Option[InetSocketAddress] = {
    features.collectFirst { case LocalAddressPeerFeature(addr) => addr }
  }
}

