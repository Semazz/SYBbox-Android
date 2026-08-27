package tun

import (
	"github.com/sagernet/gvisor/pkg/tcpip/adapters/gonet"
	"github.com/sagernet/gvisor/pkg/tcpip/stack"
	"github.com/sagernet/gvisor/pkg/tcpip/transport/tcp"
	"github.com/sagernet/gvisor/pkg/waiter"
	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/session"
	xcore "github.com/xtls/xray-core/core"
)

func (s *Stack) handleTCP(request *tcp.ForwarderRequest) {
	id := request.ID()

	var queue waiter.Queue
	endpoint, err := request.CreateEndpoint(&queue)
	if err != nil {
		request.Complete(true)
		return
	}
	request.Complete(false)
	endpoint.SocketOptions().SetKeepAlive(true)

	go s.relayTCP(gonet.NewTCPConn(&queue, endpoint), id)
}

func (s *Stack) relayTCP(local *gonet.TCPConn, id stack.TransportEndpointID) {
	defer local.Close()

	destination := xnet.TCPDestination(addressOf(id.LocalAddress), xnet.Port(id.LocalPort))
	source := xnet.TCPDestination(addressOf(id.RemoteAddress), xnet.Port(id.RemotePort))

	ctx := session.ContextWithInbound(s.ctx, &session.Inbound{Tag: InboundTag, Source: source})
	ctx = session.ContextWithContent(ctx, &session.Content{
		SniffingRequest: session.SniffingRequest{
			Enabled:                        true,
			OverrideDestinationForProtocol: sniffedProtocols,
			MetadataOnly:                   false,
			RouteOnly:                      false,
		},
	})
	remote, dialErr := xcore.Dial(ctx, s.instance, destination)
	if dialErr != nil {
		s.log(0, "tcp to "+destination.String()+" failed: "+dialErr.Error())
		return
	}
	defer remote.Close()

	pipe(local, remote, 0)
}
