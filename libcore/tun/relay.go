package tun

import (
	"net"
	"sync"
	"time"

	"github.com/sagernet/gvisor/pkg/tcpip"
	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/session"
)

const (
	relayBufferSize = 64 * 1024
	udpIdleTimeout  = 2 * time.Minute
)

var relayBuffers = sync.Pool{
	New: func() any {
		buffer := make([]byte, relayBufferSize)
		return &buffer
	},
}

func addressOf(address tcpip.Address) xnet.Address {
	return xnet.IPAddress(address.AsSlice())
}

func pipe(local, remote net.Conn, idle time.Duration) {
	finished := make(chan struct{}, 2)
	go copyStream(remote, local, idle, finished)
	go copyStream(local, remote, idle, finished)
	<-finished
}

func copyStream(destination, source net.Conn, idle time.Duration, finished chan<- struct{}) {
	defer func() { finished <- struct{}{} }()

	pooled := relayBuffers.Get().(*[]byte)
	defer relayBuffers.Put(pooled)
	buffer := *pooled

	for {
		if idle > 0 {
			_ = source.SetReadDeadline(time.Now().Add(idle))
		}
		read, readErr := source.Read(buffer)
		if read > 0 {
			if _, writeErr := destination.Write(buffer[:read]); writeErr != nil {
				return
			}
		}
		if readErr != nil {
			return
		}
	}
}

var sniffedProtocols = []string{"http", "tls", "quic", "fakedns"}

var (
	sniffGuard     sync.RWMutex
	sniffEnabled   = true
	sniffRouteOnly = false
)

func SetSniffing(enabled bool, routeOnly bool) {
	sniffGuard.Lock()
	sniffEnabled = enabled
	sniffRouteOnly = routeOnly
	sniffGuard.Unlock()
}

func sniffingRequest() session.SniffingRequest {
	sniffGuard.RLock()
	defer sniffGuard.RUnlock()
	return session.SniffingRequest{
		Enabled:                        sniffEnabled,
		OverrideDestinationForProtocol: sniffedProtocols,
		MetadataOnly:                   false,
		RouteOnly:                      sniffRouteOnly,
	}
}
