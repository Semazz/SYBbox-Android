package core

import (
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

var _ adapter.PlatformInterface = (*platformStub)(nil)

type platformStub struct{}

func (s *platformStub) Initialize(networkManager adapter.NetworkManager) error { return nil }
func (s *platformStub) UsePlatformAutoDetectInterfaceControl() bool            { return true }
func (s *platformStub) AutoDetectInterfaceControl(fd int) error                { return nil }
func (s *platformStub) UsePlatformInterface() bool                             { return false }

func (s *platformStub) OpenInterface(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	return nil, os.ErrInvalid
}

func (s *platformStub) UsePlatformDefaultInterfaceMonitor() bool { return true }

func (s *platformStub) CreateDefaultInterfaceMonitor(logger logger.Logger) tun.DefaultInterfaceMonitor {
	return (*interfaceMonitorStub)(nil)
}

func (s *platformStub) UsePlatformNetworkInterfaces() bool { return false }

func (s *platformStub) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	return nil, os.ErrInvalid
}

func (s *platformStub) UnderNetworkExtension() bool              { return false }
func (s *platformStub) NetworkExtensionIncludeAllNetworks() bool { return false }
func (s *platformStub) ClearDNSCache()                           {}
func (s *platformStub) RequestPermissionForWIFIState() error     { return nil }
func (s *platformStub) UsePlatformWIFIMonitor() bool             { return false }
func (s *platformStub) ReadWIFIState() adapter.WIFIState         { return adapter.WIFIState{} }
func (s *platformStub) SystemCertificates() []string             { return nil }
func (s *platformStub) UsePlatformConnectionOwnerFinder() bool   { return false }

func (s *platformStub) FindConnectionOwner(request *adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	return nil, os.ErrInvalid
}

func (s *platformStub) UsePlatformNotification() bool                             { return false }
func (s *platformStub) SendNotification(notification *adapter.Notification) error { return nil }

type interfaceMonitorStub struct{}

func (s *interfaceMonitorStub) Start() error                         { return os.ErrInvalid }
func (s *interfaceMonitorStub) Close() error                         { return os.ErrInvalid }
func (s *interfaceMonitorStub) DefaultInterface() *control.Interface { return nil }
func (s *interfaceMonitorStub) OverrideAndroidVPN() bool             { return false }
func (s *interfaceMonitorStub) AndroidVPNEnabled() bool              { return false }

func (s *interfaceMonitorStub) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	return nil
}

func (s *interfaceMonitorStub) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
}

func (s *interfaceMonitorStub) RegisterMyInterface(interfaceName string) {}
func (s *interfaceMonitorStub) MyInterface() string                      { return "" }
