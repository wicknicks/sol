// +build darwin

package main

import (
	"fmt"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"syscall"
	"unsafe"
)

// borrowed from https://github.com/pbnjay/memory

func sysTotalMemory() uint64 {
	s, err := sysctlUint64("hw.memsize")
	if err != nil {
		return 0
	}
	return s
}

func sysctlUint64(name string) (uint64, error) {
	s, err := syscall.Sysctl(name)
	if err != nil {
		return 0, err
	}
	// hack because the string conversion above drops a \0
	b := []byte(s)
	if len(b) < 8 {
		b = append(b, 0)
	}
	return *(*uint64)(unsafe.Pointer(&b[0])), nil
}

func sysFreeMemory() uint64 {
	out, err := exec.Command("vm_stat").Output()
	if err != nil {
		fmt.Printf("%v", err)
		return 0
	}

	lines := strings.Split(string(out), "\n")
	if len(lines) < 2 {
		return 0
	}

	regex := regexp.MustCompile("[0-9]+")

	var pageSize uint64
	var pagesFree uint64
	if strings.Index(lines[0], "page size of ") > 0 {
		pageSizeStr := regex.FindStringSubmatch(lines[0])
		pageSize = 0
		if len(pageSizeStr) == 1 {
			ps, err := strconv.ParseUint(pageSizeStr[0], 10, 64)
			if err == nil {
				pageSize = ps
			}
		}
	}

	if strings.Index(lines[1], "Pages free") >= 0 {
		pagesFreeStr := regex.FindStringSubmatch(lines[1])
		pagesFree = 0
		if len(pagesFreeStr) == 1 {
			pf, err := strconv.ParseUint(pagesFreeStr[0], 10, 64)
			if err == nil {
				pagesFree = pf
			}
		}
	}

	return pageSize * pagesFree
}
