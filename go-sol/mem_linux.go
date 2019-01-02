// +build linux

package main

import (
	"fmt"
	"syscall"
)

func sysTotalMemory() uint64 {
	in := &syscall.Sysinfo_t{}
	err := syscall.Sysinfo(in)
	if err != nil {
		fmt.Printf("%v\n", err)
		return 0
	}
	return in.Totalram
}

func sysFreeMemory() uint64 {
	in := &syscall.Sysinfo_t{}
	err := syscall.Sysinfo(in)
	if err != nil {
		fmt.Printf("%v\n", err)
		return 0
	}
	return in.Freeram
}
