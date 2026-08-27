package tun

import (
	"context"
	"errors"
	"os"
	"sync"

	"github.com/sagernet/gvisor/pkg/buffer"
	"github.com/sagernet/gvisor/pkg/tcpip"
	"github.com/sagernet/gvisor/pkg/tcpip/header"
	"github.com/sagernet/gvisor/pkg/tcpip/link/channel"
	"github.com/sagernet/gvisor/pkg/tcpip/network/ipv4"
	"github.com/sagernet/gvisor/pkg/tcpip/network/ipv6"
	"github.com/sagernet/gvisor/pkg/tcpip/stack"
	"github.com/sagernet/gvisor/pkg/tcpip/transport/icmp"
	"github.com/sagernet/gvisor/pkg/tcpip/transport/tcp"
	"github.com/sagernet/gvisor/pkg/tcpip/transport/udp"
	xcore "github.com/xtls/xray-core/core"
)

const (
	nicID      = 1
	InboundTag = "tun"
	queueDepth  = 4096
	maxInFlight = 8192
)

type Logger func(level int32, message string)

type Stack struct {
	instance *xcore.Instance
	stack    *stack.Stack
	endpoint *channel.Endpoint
	file     *os.File
	log      Logger
	ctx      context.Context
	cancel   context.CancelFunc
	stopOnce sync.Once
	mtu      int
}

func New(fd int32, mtu int32, instance *xcore.Instance, log Logger) (*Stack, error) {
	if fd <= 0 {
		return nil, errors.New("invalid tun descriptor")
	}
	file := os.NewFile(uintptr(fd), "tun")
	if file == nil {
		return nil, errors.New("invalid tun descriptor")
	}
	if instance == nil {
		file.Close()
		return nil, errors.New("xray instance is not created")
	}
	if mtu < 1280 {
		mtu = 8500
	}
	if log == nil {
		log = func(int32, string) {}
	}

	ctx, cancel := context.WithCancel(context.Background())
	s := &Stack{
		instance: instance,
		file:     file,
		log:      log,
		ctx:      ctx,
		cancel:   cancel,
		mtu:      int(mtu),
	}

	if err := s.build(); err != nil {
		cancel()
		file.Close()
		return nil, err
	}
	return s, nil
}

func (s *Stack) build() error {
	s.stack = stack.New(stack.Options{
		NetworkProtocols: []stack.NetworkProtocolFactory{
			ipv4.NewProtocol,
			ipv6.NewProtocol,
		},
		TransportProtocols: []stack.TransportProtocolFactory{
			tcp.NewProtocol,
			udp.NewProtocol,
			icmp.NewProtocol4,
			icmp.NewProtocol6,
		},
		HandleLocal: false,
	})

	s.tuneTCP()

	s.endpoint = channel.New(queueDepth, uint32(s.mtu), "")
	if err := s.stack.CreateNIC(nicID, s.endpoint); err != nil {
		return errors.New(err.String())
	}
	s.stack.SetPromiscuousMode(nicID, true)
	s.stack.SetSpoofing(nicID, true)
	s.stack.SetRouteTable([]tcpip.Route{
		{Destination: header.IPv4EmptySubnet, NIC: nicID},
		{Destination: header.IPv6EmptySubnet, NIC: nicID},
	})

	s.stack.SetTransportProtocolHandler(
		tcp.ProtocolNumber,
		tcp.NewForwarder(s.stack, 0, maxInFlight, s.handleTCP).HandlePacket,
	)
	s.stack.SetTransportProtocolHandler(
		udp.ProtocolNumber,
		udp.NewForwarder(s.stack, s.handleUDP).HandlePacket,
	)
	return nil
}

func (s *Stack) tuneTCP() {
	sack := tcpip.TCPSACKEnabled(true)
	s.stack.SetTransportProtocolOption(tcp.ProtocolNumber, &sack)

	noDelay := tcpip.TCPDelayEnabled(false)
	s.stack.SetTransportProtocolOption(tcp.ProtocolNumber, &noDelay)

	moderate := tcpip.TCPModerateReceiveBufferOption(true)
	s.stack.SetTransportProtocolOption(tcp.ProtocolNumber, &moderate)

	sendBuffer := tcpip.TCPSendBufferSizeRangeOption{Min: 16 << 10, Default: 1 << 20, Max: 16 << 20}
	s.stack.SetTransportProtocolOption(tcp.ProtocolNumber, &sendBuffer)

	receiveBuffer := tcpip.TCPReceiveBufferSizeRangeOption{Min: 16 << 10, Default: 1 << 20, Max: 16 << 20}
	s.stack.SetTransportProtocolOption(tcp.ProtocolNumber, &receiveBuffer)

	congestion := tcpip.CongestionControlOption("cubic")
	s.stack.SetTransportProtocolOption(tcp.ProtocolNumber, &congestion)
}

func (s *Stack) Start() {
	go s.readLoop()
	go s.writeLoop()
}

func (s *Stack) readLoop() {
	frame := make([]byte, s.mtu+header.IPv6MinimumSize)
	for {
		n, err := s.file.Read(frame)
		if err != nil {
			if s.ctx.Err() == nil {
				s.log(0, "tun read failed: "+err.Error())
			}
			s.Close()
			return
		}
		if n == 0 {
			continue
		}

		var protocol tcpip.NetworkProtocolNumber
		switch header.IPVersion(frame[:n]) {
		case header.IPv4Version:
			protocol = ipv4.ProtocolNumber
		case header.IPv6Version:
			protocol = ipv6.ProtocolNumber
		default:
			continue
		}

		packet := stack.NewPacketBuffer(stack.PacketBufferOptions{
			Payload: buffer.MakeWithData(frame[:n]),
		})
		s.endpoint.InjectInbound(protocol, packet)
		packet.DecRef()
	}
}

func (s *Stack) writeLoop() {
	for {
		packet := s.endpoint.ReadContext(s.ctx)
		if packet == nil {
			return
		}
		view := packet.ToView()
		_, err := s.file.Write(view.AsSlice())
		view.Release()
		packet.DecRef()
		if err != nil {
			if s.ctx.Err() == nil {
				s.log(0, "tun write failed: "+err.Error())
			}
			s.Close()
			return
		}
	}
}

func (s *Stack) Close() error {
	s.stopOnce.Do(func() {
		s.cancel()
		if s.endpoint != nil {
			s.endpoint.Close()
		}
		if s.stack != nil {
			s.stack.Close()
		}
		if s.file != nil {
			s.file.Close()
		}
	})
	return nil
}
