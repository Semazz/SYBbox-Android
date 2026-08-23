package core

import (
	"context"
	"os"
	"sync"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	_ "github.com/sagernet/sing-box/protocol/hysteria2"
	_ "github.com/sagernet/sing-box/protocol/tuic"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/filemanager"
)

var (
	setupOnce   sync.Once
	basePath    string
	workingPath string
	tempPath    string
)

func Setup(base string, working string, temp string) error {
	var err error
	setupOnce.Do(func() {
		basePath, workingPath, tempPath = base, working, temp
		if err = os.MkdirAll(workingPath, 0o777); err != nil {
			return
		}
		err = os.MkdirAll(tempPath, 0o777)
	})
	return err
}

func Version() string {
	return C.Version
}

func baseContext() context.Context {
	ctx := context.Background()
	ctx = filemanager.WithDefault(ctx, workingPath, tempPath, os.Getuid(), os.Getgid())
	return box.Context(
		ctx,
		include.InboundRegistry(),
		include.OutboundRegistry(),
		include.EndpointRegistry(),
		include.DNSTransportRegistry(),
		include.ServiceRegistry(),
	)
}

func parseConfig(ctx context.Context, configContent string) (option.Options, error) {
	options, err := json.UnmarshalExtendedContext[option.Options](ctx, []byte(configContent))
	if err != nil {
		return option.Options{}, E.Cause(err, "decode config")
	}
	return options, nil
}

func CheckConfig(configContent string) error {
	ctx := baseContext()
	options, err := parseConfig(ctx, configContent)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	ctx = service.ContextWith[adapter.PlatformInterface](ctx, (*platformStub)(nil))
	instance, err := box.New(box.Options{Context: ctx, Options: options})
	if err != nil {
		return err
	}

	_ = instance.Close()
	return nil
}

type BoxService struct {
	ctx      context.Context
	cancel   context.CancelFunc
	instance *box.Box
	wrapper  *platformWrapper
	platform Platform

	access  sync.Mutex
	started bool
}

func NewService(configContent string, platform Platform) (*BoxService, error) {
	if platform == nil {
		return nil, E.New("platform interface is required")
	}
	ctx := baseContext()
	options, err := parseConfig(ctx, configContent)
	if err != nil {
		return nil, err
	}
	wrapper := newPlatformWrapper(platform)
	ctx, cancel := context.WithCancel(ctx)
	service.MustRegister[adapter.PlatformInterface](ctx, wrapper)
	instance, err := box.New(box.Options{
		Context:           ctx,
		Options:           options,
		PlatformLogWriter: &platformLogWriter{platform: platform},
	})
	if err != nil {
		cancel()
		return nil, E.Cause(err, "create service")
	}
	return &BoxService{
		ctx:      ctx,
		cancel:   cancel,
		instance: instance,
		wrapper:  wrapper,
		platform: platform,
	}, nil
}

func (s *BoxService) Start() error {
	s.access.Lock()
	defer s.access.Unlock()
	if s.started {
		return E.New("service already started")
	}
	if err := s.instance.Start(); err != nil {
		return err
	}
	s.started = true
	return nil
}

func (s *BoxService) Close() error {
	s.access.Lock()
	defer s.access.Unlock()
	if s.instance == nil {
		return nil
	}
	instance := s.instance
	s.instance = nil
	s.started = false
	s.cancel()
	done := make(chan error, 1)
	go func() { done <- instance.Close() }()
	select {
	case err := <-done:
		return err
	case <-time.After(5 * time.Second):
		return E.New("service close timed out")
	}
}

func (s *BoxService) UpdateDefaultInterface(interfaceName string, interfaceIndex int32, isExpensive bool, isConstrained bool) {
	if s.wrapper == nil || s.wrapper.monitor == nil {
		return
	}
	s.wrapper.monitor.updateDefaultInterface(interfaceName, interfaceIndex, isExpensive, isConstrained)
}

func (s *BoxService) URLTest(outboundTag string, link string, timeoutMillis int32) (int32, error) {
	s.access.Lock()
	instance := s.instance
	s.access.Unlock()
	if instance == nil {
		return 0, E.New("service is not running")
	}
	outbound, found := instance.Outbound().Outbound(outboundTag)
	if !found {
		return 0, E.New("outbound not found: ", outboundTag)
	}
	if timeoutMillis <= 0 {
		timeoutMillis = 5000
	}
	ctx, cancel := context.WithTimeout(s.ctx, time.Duration(timeoutMillis)*time.Millisecond)
	defer cancel()
	latency, err := urltest.URLTest(ctx, link, outbound)
	if err != nil {
		return 0, err
	}
	return int32(latency), nil
}

type platformLogWriter struct {
	platform Platform
}

func (w *platformLogWriter) WriteMessage(level log.Level, message string) {
	w.platform.WriteLog(int32(level), message)
}
