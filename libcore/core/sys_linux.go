//go:build linux

package core

import (
	"fmt"
	"net"
	"os"
	"path/filepath"

	"golang.org/x/sys/unix"
)

const (
	iffUp           = 0x1
	iffBroadcast    = 0x2
	iffLoopback     = 0x8
	iffPointToPoint = 0x10
	iffRunning      = 0x40
	iffMulticast    = 0x1000
)

func getTunnelName(fd int32) (string, error) {
	link, err := os.Readlink(fmt.Sprintf("/proc/self/fd/%d", fd))
	if err != nil {
		return "", err
	}
	return filepath.Base(link), nil
}

func dupFD(fd int) (int, error) {
	return unix.Dup(fd)
}

func linkFlags(rawFlags uint32) net.Flags {
	var flags net.Flags
	if rawFlags&iffUp != 0 {
		flags |= net.FlagUp
	}
	if rawFlags&iffBroadcast != 0 {
		flags |= net.FlagBroadcast
	}
	if rawFlags&iffLoopback != 0 {
		flags |= net.FlagLoopback
	}
	if rawFlags&iffPointToPoint != 0 {
		flags |= net.FlagPointToPoint
	}
	if rawFlags&iffRunning != 0 {
		flags |= net.FlagRunning
	}
	if rawFlags&iffMulticast != 0 {
		flags |= net.FlagMulticast
	}
	return flags
}
