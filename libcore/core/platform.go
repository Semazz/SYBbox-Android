package core

import (
	"encoding/json"
	"net/netip"
	"strings"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
)

type Platform interface {

	OpenTun(optionsJSON string) (int32, error)

	Protect(fd int32) bool

	Interfaces() (string, error)

	StartInterfaceMonitor() error
	CloseInterfaceMonitor() error

	WIFIState() string

	WriteLog(level int32, message string)
}

type tunOptionsJSON struct {
	MTU                      int32          `json:"mtu"`
	AutoRoute                bool           `json:"auto_route"`
	StrictRoute              bool           `json:"strict_route"`
	Inet4Address             []string       `json:"inet4_address"`
	Inet6Address             []string       `json:"inet6_address"`
	Inet4RouteAddress        []string       `json:"inet4_route_address"`
	Inet6RouteAddress        []string       `json:"inet6_route_address"`
	Inet4RouteExcludeAddress []string       `json:"inet4_route_exclude_address"`
	Inet6RouteExcludeAddress []string       `json:"inet6_route_exclude_address"`
	Inet4RouteRange          []string       `json:"inet4_route_range"`
	Inet6RouteRange          []string       `json:"inet6_route_range"`
	IncludePackage           []string       `json:"include_package"`
	ExcludePackage           []string       `json:"exclude_package"`
	DNSServerAddress         string         `json:"dns_server_address"`
	HTTPProxy                *httpProxyJSON `json:"http_proxy,omitempty"`
}

type httpProxyJSON struct {
	Server       string   `json:"server"`
	ServerPort   int32    `json:"server_port"`
	BypassDomain []string `json:"bypass_domain"`
	MatchDomain  []string `json:"match_domain"`
}

type interfaceJSON struct {
	Index      int32    `json:"index"`
	MTU        int32    `json:"mtu"`
	Name       string   `json:"name"`
	Addresses  []string `json:"addresses"`
	Flags      uint32   `json:"flags"`
	Type       string   `json:"type"`
	DNSServers []string `json:"dns_servers"`
	Metered    bool     `json:"metered"`
}

var _ adapter.PlatformInterface = (*platformWrapper)(nil)

type platformWrapper struct {
	platform       Platform
	networkManager adapter.NetworkManager
	monitor        *defaultInterfaceMonitor
	myTunName      string
}

func newPlatformWrapper(platform Platform) *platformWrapper {
	return &platformWrapper{platform: platform}
}

func (w *platformWrapper) Initialize(networkManager adapter.NetworkManager) error {
	w.networkManager = networkManager
	return nil
}

func (w *platformWrapper) UsePlatformAutoDetectInterfaceControl() bool { return true }

func (w *platformWrapper) AutoDetectInterfaceControl(fd int) error {
	if !w.platform.Protect(int32(fd)) {
		return E.New("protect socket failed: fd ", fd)
	}
	return nil
}

func (w *platformWrapper) UsePlatformInterface() bool { return true }

func (w *platformWrapper) OpenInterface(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	if len(options.IncludeUID) > 0 || len(options.ExcludeUID) > 0 {
		return nil, E.New("platform: uid options are not supported on android")
	}
	if len(options.IncludeAndroidUser) > 0 {
		return nil, E.New("platform: android_user option is not supported")
	}
	routeRanges, err := options.BuildAutoRouteRanges(true)
	if err != nil {
		return nil, E.Cause(err, "build auto route ranges")
	}
	payload := tunOptionsJSON{
		MTU:                      int32(options.MTU),
		AutoRoute:                options.AutoRoute,
		StrictRoute:              options.StrictRoute,
		Inet4Address:             prefixesToStrings(options.Inet4Address),
		Inet6Address:             prefixesToStrings(options.Inet6Address),
		Inet4RouteAddress:        prefixesToStrings(options.Inet4RouteAddress),
		Inet6RouteAddress:        prefixesToStrings(options.Inet6RouteAddress),
		Inet4RouteExcludeAddress: prefixesToStrings(options.Inet4RouteExcludeAddress),
		Inet6RouteExcludeAddress: prefixesToStrings(options.Inet6RouteExcludeAddress),
		Inet4RouteRange:          prefixesToStrings(filterPrefixes(routeRanges, true)),
		Inet6RouteRange:          prefixesToStrings(filterPrefixes(routeRanges, false)),
		IncludePackage:           options.IncludePackage,
		ExcludePackage:           options.ExcludePackage,
	}

	if len(options.Inet4Address) > 0 && options.Inet4Address[0].Bits() < 32 {
		payload.DNSServerAddress = options.Inet4Address[0].Addr().Next().String()
	}
	if platformOptions.HTTPProxy != nil && platformOptions.HTTPProxy.Enabled {
		payload.HTTPProxy = &httpProxyJSON{
			Server:       platformOptions.HTTPProxy.Server,
			ServerPort:   int32(platformOptions.HTTPProxy.ServerPort),
			BypassDomain: platformOptions.HTTPProxy.BypassDomain,
			MatchDomain:  platformOptions.HTTPProxy.MatchDomain,
		}
	}
	encoded, err := json.Marshal(payload)
	if err != nil {
		return nil, E.Cause(err, "encode tun options")
	}
	tunFd, err := w.platform.OpenTun(string(encoded))
	if err != nil {
		return nil, E.Cause(err, "open tun")
	}
	options.Name, err = getTunnelName(tunFd)
	if err != nil {
		return nil, E.Cause(err, "query tun name")
	}
	if options.InterfaceMonitor != nil {
		options.InterfaceMonitor.RegisterMyInterface(options.Name)
	}

	dupFd, err := dupFD(int(tunFd))
	if err != nil {
		return nil, E.Cause(err, "dup tun file descriptor")
	}
	options.FileDescriptor = dupFd
	w.myTunName = options.Name
	return tun.New(*options)
}

func (w *platformWrapper) UsePlatformDefaultInterfaceMonitor() bool { return true }

func (w *platformWrapper) CreateDefaultInterfaceMonitor(logger logger.Logger) tun.DefaultInterfaceMonitor {
	monitor := &defaultInterfaceMonitor{platformWrapper: w, logger: logger}
	w.monitor = monitor
	return monitor
}

func (w *platformWrapper) UsePlatformNetworkInterfaces() bool { return true }

func (w *platformWrapper) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	encoded, err := w.platform.Interfaces()
	if err != nil {
		return nil, err
	}
	var raw []interfaceJSON
	if err = json.Unmarshal([]byte(encoded), &raw); err != nil {
		return nil, E.Cause(err, "decode interfaces")
	}
	defaultIndex, expensive, constrained := -1, false, false
	if w.monitor != nil {
		defaultIndex, expensive, constrained = w.monitor.defaultState()
	}
	var interfaces []adapter.NetworkInterface
	for _, item := range raw {
		if item.Name == w.myTunName {
			continue
		}
		addresses, err := parsePrefixes(item.Addresses)
		if err != nil {
			return nil, E.Cause(err, "parse addresses of ", item.Name)
		}
		isDefault := int(item.Index) == defaultIndex
		interfaces = append(interfaces, adapter.NetworkInterface{
			Interface: control.Interface{
				Index:     int(item.Index),
				MTU:       int(item.MTU),
				Name:      item.Name,
				Addresses: addresses,
				Flags:     linkFlags(item.Flags),
			},
			Type:        C.StringToInterfaceType[item.Type],
			DNSServers:  item.DNSServers,
			Expensive:   item.Metered || (isDefault && expensive),
			Constrained: isDefault && constrained,
		})
	}
	return common.UniqBy(interfaces, func(it adapter.NetworkInterface) string {
		return it.Name
	}), nil
}

func (w *platformWrapper) UnderNetworkExtension() bool              { return false }
func (w *platformWrapper) NetworkExtensionIncludeAllNetworks() bool { return false }
func (w *platformWrapper) ClearDNSCache()                           {}
func (w *platformWrapper) RequestPermissionForWIFIState() error     { return nil }
func (w *platformWrapper) UsePlatformWIFIMonitor() bool             { return true }

func (w *platformWrapper) ReadWIFIState() adapter.WIFIState {
	state := w.platform.WIFIState()
	if state == "" {
		return adapter.WIFIState{}
	}
	ssid, bssid, _ := strings.Cut(state, "\n")
	return adapter.WIFIState{SSID: ssid, BSSID: bssid}
}

func (w *platformWrapper) SystemCertificates() []string { return nil }

func (w *platformWrapper) UsePlatformConnectionOwnerFinder() bool { return false }

func (w *platformWrapper) FindConnectionOwner(request *adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	return nil, E.New("connection owner lookup is not supported")
}

func (w *platformWrapper) UsePlatformNotification() bool { return false }

func (w *platformWrapper) SendNotification(notification *adapter.Notification) error { return nil }

func prefixesToStrings(prefixes []netip.Prefix) []string {
	result := make([]string, 0, len(prefixes))
	for _, prefix := range prefixes {
		result = append(result, prefix.String())
	}
	return result
}

func parsePrefixes(values []string) ([]netip.Prefix, error) {
	result := make([]netip.Prefix, 0, len(values))
	for _, value := range values {
		prefix, err := netip.ParsePrefix(value)
		if err != nil {
			return nil, err
		}
		result = append(result, prefix)
	}
	return result, nil
}

func filterPrefixes(prefixes []netip.Prefix, v4 bool) []netip.Prefix {
	var result []netip.Prefix
	for _, prefix := range prefixes {
		if prefix.Addr().Is4() == v4 {
			result = append(result, prefix)
		}
	}
	return result
}
