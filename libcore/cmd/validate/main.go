package main

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"sort"

	xcore "github.com/xtls/xray-core/core"

	_ "github.com/xtls/xray-core/main/distro/all"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: validate <config dir>")
		os.Exit(2)
	}

	paths, err := filepath.Glob(filepath.Join(os.Args[1], "*.json"))
	if err != nil || len(paths) == 0 {
		fmt.Fprintf(os.Stderr, "no configs found in %s\n", os.Args[1])
		os.Exit(2)
	}
	sort.Strings(paths)

	failed := 0
	for _, path := range paths {
		name := filepath.Base(path)
		if err := check(path); err != nil {
			failed++
			fmt.Printf("FAIL %s\n     %v\n", name, err)
			continue
		}
		fmt.Printf("ok   %s\n", name)
	}

	fmt.Printf("\n%d checked, %d failed\n", len(paths), failed)
	if failed > 0 {
		os.Exit(1)
	}
}

func check(path string) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}

	config, err := xcore.LoadConfig("json", bytes.NewReader(data))
	if err != nil {
		return fmt.Errorf("config was refused: %w", err)
	}

	instance, err := xcore.New(config)
	if err != nil {
		return fmt.Errorf("core could not be built: %w", err)
	}
	return instance.Close()
}
