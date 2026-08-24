package v2ray

import (
	"context"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/tls"
	"github.com/sagernet/sing-box/option"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/logger"
	xhttp "github.com/justinwoo280/sing-xhttp/xhttp"
)

func NewXHTTPClient(ctx context.Context, dialer N.Dialer, serverAddr M.Socksaddr, options option.V2RayXHTTPOptions, tlsConfig tls.Config) (adapter.V2RayClientTransport, error) {
	xOpts := xhttp.Options{
		Path:    options.Path,
		Host:    options.Host,
		Mode:    options.Mode,
		Headers: options.Headers,
	}
	return xhttp.NewClient(ctx, dialer, serverAddr, xOpts, tlsConfig)
}

func NewXHTTPServer(ctx context.Context, logger logger.ContextLogger, options option.V2RayXHTTPOptions, tlsConfig tls.ServerConfig, handler adapter.V2RayServerTransportHandler) (adapter.V2RayServerTransport, error) {
	xOpts := xhttp.Options{
		Path:    options.Path,
		Host:    options.Host,
		Mode:    options.Mode,
		Headers: options.Headers,
	}
	return xhttp.NewServer(ctx, logger, xOpts, tlsConfig, handler)
}
