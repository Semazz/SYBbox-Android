package core

import (
	"sync"

	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

var _ tun.DefaultInterfaceMonitor = (*defaultInterfaceMonitor)(nil)

type defaultInterfaceMonitor struct {
	*platformWrapper
	logger logger.Logger

	access           sync.Mutex
	callbacks        list.List[tun.DefaultInterfaceUpdateCallback]
	defaultInterface *control.Interface
	defaultIndex     int
	isExpensive      bool
	isConstrained    bool
	myInterface      string
}

func (m *defaultInterfaceMonitor) Start() error {
	m.access.Lock()
	m.defaultIndex = -1
	m.access.Unlock()
	return m.platform.StartInterfaceMonitor()
}

func (m *defaultInterfaceMonitor) Close() error {
	return m.platform.CloseInterfaceMonitor()
}

func (m *defaultInterfaceMonitor) DefaultInterface() *control.Interface {
	m.access.Lock()
	defer m.access.Unlock()
	return m.defaultInterface
}

func (m *defaultInterfaceMonitor) OverrideAndroidVPN() bool { return false }

func (m *defaultInterfaceMonitor) AndroidVPNEnabled() bool { return false }

func (m *defaultInterfaceMonitor) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	m.access.Lock()
	defer m.access.Unlock()
	return m.callbacks.PushBack(callback)
}

func (m *defaultInterfaceMonitor) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
	m.access.Lock()
	defer m.access.Unlock()
	m.callbacks.Remove(element)
}

func (m *defaultInterfaceMonitor) RegisterMyInterface(interfaceName string) {
	m.access.Lock()
	defer m.access.Unlock()
	m.myInterface = interfaceName
}

func (m *defaultInterfaceMonitor) MyInterface() string {
	m.access.Lock()
	defer m.access.Unlock()
	return m.myInterface
}

func (m *defaultInterfaceMonitor) defaultState() (index int, expensive bool, constrained bool) {
	m.access.Lock()
	defer m.access.Unlock()
	return m.defaultIndex, m.isExpensive, m.isConstrained
}

func (m *defaultInterfaceMonitor) updateDefaultInterface(interfaceName string, interfaceIndex int32, isExpensive bool, isConstrained bool) {
	m.access.Lock()
	m.isExpensive = isExpensive
	m.isConstrained = isConstrained
	m.defaultIndex = int(interfaceIndex)
	m.access.Unlock()

	if m.networkManager != nil {
		if err := m.networkManager.UpdateInterfaces(); err != nil {
			m.logger.Error(E.Cause(err, "update interfaces"))
		}
	}

	if interfaceIndex == -1 {
		m.access.Lock()
		m.defaultInterface = nil
		callbacks := m.callbacks.Array()
		m.access.Unlock()
		for _, callback := range callbacks {
			callback(nil, 0)
		}
		return
	}

	if m.networkManager == nil {
		return
	}
	newInterface, err := m.networkManager.InterfaceFinder().ByIndex(int(interfaceIndex))
	if err != nil {
		m.logger.Error(E.Cause(err, "find updated interface: ", interfaceName))
		return
	}

	m.access.Lock()
	oldInterface := m.defaultInterface
	m.defaultInterface = newInterface
	if oldInterface != nil && oldInterface.Name == newInterface.Name && oldInterface.Index == newInterface.Index {
		m.access.Unlock()
		return
	}
	callbacks := m.callbacks.Array()
	m.access.Unlock()

	for _, callback := range callbacks {
		callback(newInterface, 0)
	}
}
