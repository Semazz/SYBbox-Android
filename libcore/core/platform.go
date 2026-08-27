package core

type Platform interface {
	Protect(fd int32) bool
	Log(level int32, message string)
	SystemDns() string
}

const (
	LogLevelError int32 = 0
	LogLevelWarn  int32 = 1
	LogLevelInfo  int32 = 2
	LogLevelDebug int32 = 3
)
