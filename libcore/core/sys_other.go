//go:build !linux

package core

import "net"

func getTunnelName(fd int32) (string, error) {
	return "tun", nil
}

func dupFD(fd int) (int, error) {
	return fd, nil
}

func linkFlags(rawFlags uint32) net.Flags {
	return 0
}
