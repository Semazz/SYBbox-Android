package core

import (
	"bytes"
	"errors"
	"os"
	"sync"

	"github.com/sybbox/libcore/tun"
	xcore "github.com/xtls/xray-core/core"

	_ "github.com/xtls/xray-core/main/distro/all"
)

type Instance struct {
	xray     *xcore.Instance
	tunnel   *tun.Stack
	platform Platform
	guard    sync.Mutex
	started  bool
}

func SetAssetPath(path string) error {
	if err := os.Setenv("xray.location.asset", path); err != nil {
		return err
	}
	return os.Setenv("XRAY_LOCATION_ASSET", path)
}

func Version() string {
	return xcore.Version()
}

func NewInstance(configJSON string, platform Platform) (*Instance, error) {
	if platform == nil {
		return nil, errors.New("platform is not provided")
	}
	if configJSON == "" {
		return nil, errors.New("configuration is empty")
	}

	config, err := xcore.LoadConfig("json", bytes.NewReader([]byte(configJSON)))
	if err != nil {
		return nil, err
	}

	installDialerController(platform)

	xray, err := xcore.New(config)
	if err != nil {
		return nil, err
	}

	return &Instance{xray: xray, platform: platform}, nil
}

func (i *Instance) Start(tunFd int32, mtu int32) error {
	i.guard.Lock()
	defer i.guard.Unlock()

	if i.started {
		return errors.New("instance is already running")
	}

	tunnel, err := tun.New(tunFd, mtu, i.xray, i.platform.Log)
	if err != nil {
		return err
	}

	if err := i.xray.Start(); err != nil {
		_ = tunnel.Close()
		return err
	}
	tunnel.Start()

	i.tunnel = tunnel
	i.started = true
	i.platform.Log(LogLevelInfo, "core started, xray "+xcore.Version())
	return nil
}

func (i *Instance) Close() error {
	i.guard.Lock()
	defer i.guard.Unlock()

	if !i.started {
		return nil
	}
	i.started = false

	if i.tunnel != nil {
		_ = i.tunnel.Close()
		i.tunnel = nil
	}
	err := i.xray.Close()
	clearPlatform(i.platform)
	return err
}

func TestConfig(configJSON string) error {
	config, err := xcore.LoadConfig("json", bytes.NewReader([]byte(configJSON)))
	if err != nil {
		return err
	}
	instance, err := xcore.New(config)
	if err != nil {
		return err
	}
	return instance.Close()
}
