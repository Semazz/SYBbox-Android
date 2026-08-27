package core

import (
	"sync"
	"syscall"

	"github.com/xtls/xray-core/transport/internet"
)

var (
	controllerOnce sync.Once
	platformGuard  sync.RWMutex
	activePlatform Platform
)

func installDialerController(platform Platform) {
	platformGuard.Lock()
	activePlatform = platform
	platformGuard.Unlock()

	controllerOnce.Do(func() {
		_ = internet.RegisterDialerController(protectSocket)
		_ = internet.RegisterListenerController(protectSocket)
	})
}

func clearPlatform(platform Platform) {
	platformGuard.Lock()
	if activePlatform == platform {
		activePlatform = nil
	}
	platformGuard.Unlock()
}

func protectSocket(network string, address string, conn syscall.RawConn) error {
	platformGuard.RLock()
	platform := activePlatform
	platformGuard.RUnlock()

	if platform == nil || conn == nil {
		return nil
	}
	return conn.Control(func(fd uintptr) {
		platform.Protect(int32(fd))
	})
}
