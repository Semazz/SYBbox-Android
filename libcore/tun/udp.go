package tun

import (
	"github.com/sagernet/gvisor/pkg/tcpip/adapters/gonet"
	"github.com/sagernet/gvisor/pkg/tcpip/stack"
	"github.com/sagernet/gvisor/pkg/tcpip/transport/udp"
	"github.com/sagernet/gvisor/pkg/waiter"
	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/session"
	xcore "github.com/xtls/xray-core/core"
)

func (s *Stack) handleUDP(request *udp.ForwarderRequest) bool {
	id := request.ID()

	var queue waiter.Queue
	endpoint, err := request.CreateEndpoint(&queue)
	if err != nil {
		return false
	}

	go s.relayUDP(gonet.NewUDPConn(&queue, endpoint), id)
	return true
}

func (s *Stack) relayUDP(local *gonet.UDPConn, id stack.TransportEndpointID) {
	defer local.Close()

	destination := xnet.UDPDestination(addressOf(id.LocalAddress), xnet.Port(id.LocalPort))
	source := xnet.UDPDestination(addressOf(id.RemoteAddress), xnet.Port(id.RemotePort))

	ctx := session.ContextWithInbound(s.ctx, &session.Inbound{Tag: InboundTag, Source: source})
	ctx = session.ContextWithContent(ctx, &session.Content{SniffingRequest: sniffingRequest()})
	remote, dialErr := xcore.Dial(ctx, s.instance, destination)
	if dialErr != nil {
		s.log(0, "udp to "+destination.String()+" failed: "+dialErr.Error())
		return
	}
	defer remote.Close()

	pipe(local, remote, udpIdleTimeout)
}
